// 站内问答 Agent 的展示规则：输入校验、引用链接、错误文案。都是纯函数，单测在 test/agentAnswer.test.js。
// 约定见 AGENTS.md「联调站内问答 Agent」的「响应与错误」「回答与引用」两节。

export const MAX_QUESTION_CODE_POINTS = 4000

/** 返回输入框的错误文案，没问题时返回空串。长度按码点数算（emoji 算 1 个），与 Agent 的上限一致。 */
export function questionError(text, emptyMessage = '请输入问题') {
  const value = String(text ?? '').trim()
  if (!value) return emptyMessage
  if ([...value].length > MAX_QUESTION_CODE_POINTS) return `最多 ${MAX_QUESTION_CODE_POINTS} 字，请精简后再提交`
  return ''
}

/**
 * 引用链接由前端拼：/docs/ 加上逐段编码的 path，anchor 非空时再加 #anchor。
 * 直接用服务端给的 anchor（null 或空串按没有处理），不自己对标题 slugify，也不用模型写的链接。
 * anchor 也编码：DocPage 跳转时会先 decodeURIComponent 再找标题。
 */
export function citationHref(source) {
  const path = String((source && source.path) || '')
    .split('/')
    .filter(Boolean)
    .map(encodeURIComponent)
    .join('/')
  const anchor = source && source.anchor ? `#${encodeURIComponent(source.anchor)}` : ''
  return `/docs/${path}${anchor}`
}

function sourceNumber(sourceId) {
  const match = /^S(\d+)$/.exec(String(sourceId || ''))
  return match ? Number(match[1]) : null
}

/**
 * 显示用的编号：按来源原编号排序后依次记为 1、2、3…。
 * Agent 只返回回答里实际引用的来源，原编号可能不连续（例如只有 S2、S5），直接显示会让人以为漏了 [1]。
 */
function displayNumbers(sources) {
  const ordered = (sources || [])
    .map((s) => ({ id: s.sourceId, n: sourceNumber(s.sourceId) }))
    .filter((s) => s.n !== null)
    .sort((a, b) => a.n - b.n)
  return new Map(ordered.map((s, i) => [s.id, i + 1]))
}

/**
 * 交给 MarkdownRenderer 之前处理回答正文：
 * - 删掉所有 `[...]: ...` 形式的引用定义行。证据来自用户投稿，模型可能被诱导写出 `[S1]: https://…`，
 *   留着的话正文里的 [S1] 会全部变成外站链接，DOMPurify 不会拦。
 * - 把 [S#] 换成自己拼的站内引用链接，显示为与下方「参考资料」一致的连续编号。
 *   找不到对应来源的标记只留文字、不成为链接（正常情况下 Agent 的引用校验会先挡住）。
 */
export function prepareAnswerMarkdown(text, sources = []) {
  const bySourceId = new Map((sources || []).map((s) => [s.sourceId, s]))
  const numbers = displayNumbers(sources)
  const kept = String(text || '')
    .split(/\r\n|\r|\n/)
    .filter((line) => !/^\s{0,3}\[[^\]\n]+\]:/.test(line))
    .join('\n')
  return kept.replace(/\[(S\d+)\]/g, (_whole, sourceId) => {
    const source = bySourceId.get(sourceId)
    return source && numbers.has(sourceId)
      ? `[\\[${numbers.get(sourceId)}\\]](${citationHref(source)})`
      : `\\[${sourceId}\\]`
  })
}

/**
 * 「站内资料没有覆盖」的条目按纯文本显示。模型有时也在这里写 [S4] 之类的标记，
 * 这部分不经过引用校验（可能指向不存在的来源），显示前去掉标记，只留文字。
 */
export function unresolvedItems(list) {
  if (!Array.isArray(list)) return []
  return list
    .filter((item) => typeof item === 'string')
    .map((item) => item.replace(/\s*\[S\d+\]/g, '').trim())
    .filter(Boolean)
}

/** 「参考资料」列表：按编号排序；标题路径的最后一级与页面标题不同时才单独显示小节名。 */
export function sourceItems(sources = []) {
  const numbers = displayNumbers(sources)
  return (sources || [])
    .filter((s) => numbers.has(s.sourceId))
    .map((s) => {
      const headings = Array.isArray(s.headingPath) ? s.headingPath : []
      const last = headings.length ? headings[headings.length - 1] : ''
      return {
        key: s.sourceId,
        n: numbers.get(s.sourceId),
        title: s.title || s.path || '',
        section: last && last !== s.title ? last : '',
        href: citationHref(s),
      }
    })
    .sort((a, b) => a.n - b.n)
}

/** Agent 自己报错的 message 是中文，可以直接展示；FastAPI 自动生成的（422 等）是 JSON 文本，换成通用文案。 */
export function readableMessage(message, fallback) {
  const text = typeof message === 'string' ? message.trim() : ''
  return !text || /^[[{"]/.test(text) ? fallback : text
}

/**
 * 把请求失败归成界面能处理的几类。httpStatus 是真实的 HTTP 状态（0 表示没连上），code 是响应体里的 code。
 * - 网关自己拦下的是 HTTP 200：code 401 表示要重新登录，403 表示没有权限。只有这种 401 才表示要登录。
 * - HTTP 401 是网关与 Agent 的密钥或 audience 配置问题：不清登录态，也不展示它的 message。
 * - Agent 的字符串错误码被网关丢掉了，只能按 HTTP 状态分支；429 / 502 / 503 都退回「搜索文档」入口。
 * 返回 { text, login?, search?, retry? }：retry 表示可以原样重试同一次提交（复用 requestId）。
 */
export function describeAgentFailure({ httpStatus, code, message } = {}) {
  if (httpStatus === 200 && code === 401) return { text: '登录已过期，请重新登录后再提问。', login: true }
  if (httpStatus === 200 && code === 403) return { text: '站内问答目前只对超级管理员开放内测。' }
  if (httpStatus === 200) return { text: '请求没有被接受，请刷新页面后重试。' }
  switch (httpStatus) {
    case 400:
      return { text: readableMessage(message, '请求内容不合法，请修改后重试。') }
    case 401:
      return { text: '问答服务配置异常，请联系管理员。' }
    case 404:
      return { text: '这条问答记录不存在或已失效。' }
    case 409:
      return { text: readableMessage(message, '上一个问题还在处理中，请稍后再试。') }
    case 410:
      return { text: '补充说明已超时，请重新提问。' }
    case 422:
      return { text: '请求内容不合法，请修改后重试。' }
    case 429:
      return { text: '今天的问答额度已经用完，可以先搜索文档。', search: true }
    default:
      // 0（没连上）、502（Agent 返回坏数据）、503（Agent 不可用）及其它 5xx
      return { text: '问答服务暂时不可用，可以先搜索文档。', search: true, retry: true }
  }
}

const NOTICE_TITLES = {
  insufficient_evidence: '没有找到足够的资料',
  validation_failed: '没能生成引用可靠的回答',
  clarification_declined: '已结束本次提问',
}

/** notice 结果的小标题；正文直接用 Agent 给的 text（已是面向用户的中文）。 */
export function noticeTitle(reason) {
  return NOTICE_TITLES[reason] || '这个问题暂时无法回答'
}

const RUN_ERROR_TEXT = {
  DEADLINE_EXCEEDED: '处理超时了，请重试。',
  MODEL_UNAVAILABLE: '模型服务暂时不可用，请稍后重试。',
  BUDGET_EXHAUSTED: '今天的问答额度已经用完，可以先搜索文档。',
}

/** run 以 FAILED / CANCELLED / EXPIRED 结束时的说明；result 仍可能保留部分答案，原因在 error。 */
export function runErrorText(status, error) {
  if (status === 'CANCELLED') return '已停止。'
  if (status === 'EXPIRED') return '等待补充说明已超时，请重新提问。'
  return RUN_ERROR_TEXT[error && error.code] || readableMessage(error && error.message, '出了点问题，请重试。')
}

export const FEEDBACK_REASONS = [
  { value: 'off_topic', label: '答非所问' },
  { value: 'wrong_source', label: '引用不对' },
  { value: 'outdated', label: '内容过时' },
  { value: 'missing', label: '没答全' },
  { value: 'other', label: '其他' },
]
