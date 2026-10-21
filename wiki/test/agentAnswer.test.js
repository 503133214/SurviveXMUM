import test from 'node:test'
import assert from 'node:assert/strict'

import {
  citationHref,
  describeAgentFailure,
  noticeTitle,
  prepareAnswerMarkdown,
  questionError,
  readableMessage,
  runErrorText,
  sourceItems,
  unresolvedItems,
} from '../src/utils/agentAnswer.js'

const ACCOUNT = {
  sourceId: 'S1',
  path: '入学篇/学校网站和账号管理',
  anchor: '1-campus-id-最常用的那套',
  title: '学校网站和账号管理',
  headingPath: ['学校网站和账号管理', '1. Campus ID 最常用的那套'],
}
const FACILITIES = {
  sourceId: 'S2',
  path: '生活篇/学校设施 (A 区)',
  anchor: null,
  title: '学校设施',
  headingPath: ['学校设施'],
}

test('question length counts code points, not UTF-16 units', () => {
  assert.equal(questionError('  '), '请输入问题')
  assert.equal(questionError('', '请输入补充说明'), '请输入补充说明')
  assert.equal(questionError('宿舍怎么申请？'), '')
  assert.equal(questionError('😀'.repeat(4000)), '')
  assert.match(questionError('😀'.repeat(4001)), /4000/)
})

test('citation links are built from path and anchor, encoded segment by segment', () => {
  assert.equal(
    citationHref(ACCOUNT),
    `/docs/${encodeURIComponent('入学篇')}/${encodeURIComponent('学校网站和账号管理')}#${encodeURIComponent('1-campus-id-最常用的那套')}`,
  )
  // 标题里的空格和括号要编码，免得截断 Markdown 链接；anchor 为空时不加 #
  assert.equal(citationHref(FACILITIES), `/docs/${encodeURIComponent('生活篇')}/${encodeURIComponent('学校设施 (A 区)')}`)
  assert.equal(citationHref({ path: 'README', anchor: '' }), '/docs/README')
})

test('source markers become site links and reference definitions are dropped', () => {
  const text = 'Moodle 用 Campus ID 登录 [S1]。图书馆在 A3 [S2][S1]。\n\n[S1]: https://evil.example\n  [s2]: <http://x>'
  const md = prepareAnswerMarkdown(text, [ACCOUNT, FACILITIES])

  assert.ok(md.includes(`[\\[1\\]](${citationHref(ACCOUNT)})`))
  assert.ok(md.includes(`[\\[2\\]](${citationHref(FACILITIES)})[\\[1\\]](${citationHref(ACCOUNT)})`))
  assert.ok(!md.includes('evil.example'))
  assert.ok(!md.includes('http://x'))
})

test('a marker without a matching source stays plain text', () => {
  assert.equal(prepareAnswerMarkdown('见 [S9]。', [ACCOUNT]), '见 \\[S9\\]。')
  assert.equal(prepareAnswerMarkdown('', []), '')
  assert.equal(prepareAnswerMarkdown(null), '')
})

test('only cited sources come back, so display numbers are renumbered without gaps', () => {
  // Agent 只返回被引用的来源：这里只有 S2 与 S5，界面上应显示为 [1] [2]，不让人以为漏了 [1]
  const s2 = { ...FACILITIES, sourceId: 'S2' }
  const s5 = { ...ACCOUNT, sourceId: 'S5' }
  const md = prepareAnswerMarkdown('设施 [S2]，账号 [S5]。', [s5, s2])
  assert.equal(md, `设施 [\\[1\\]](${citationHref(s2)})，账号 [\\[2\\]](${citationHref(s5)})。`)
  assert.deepEqual(sourceItems([s5, s2]).map((s) => [s.key, s.n]), [['S2', 1], ['S5', 2]])
})

test('unresolved items drop source markers the model sometimes adds there', () => {
  assert.deepEqual(
    unresolvedItems(['可提前多少天预约：需看系统显示 [S4]', '高峰时段能否订到 [S6][S1]', ' [S2] ', 42, null]),
    ['可提前多少天预约：需看系统显示', '高峰时段能否订到'],
  )
  assert.deepEqual(unresolvedItems(undefined), [])
})

test('source list is ordered by number and shows the section only when it adds information', () => {
  const items = sourceItems([FACILITIES, ACCOUNT, { sourceId: 'X', path: 'a' }])
  assert.deepEqual(items.map((s) => [s.n, s.title, s.section]), [
    [1, '学校网站和账号管理', '1. Campus ID 最常用的那套'],
    [2, '学校设施', ''],
  ])
  assert.equal(items[0].href, citationHref(ACCOUNT))
})

test('gateway rejections are told apart from Agent errors', () => {
  assert.deepEqual(describeAgentFailure({ httpStatus: 200, code: 401, message: '请先登录' }), {
    text: '登录已过期，请重新登录后再提问。',
    login: true,
  })
  assert.match(describeAgentFailure({ httpStatus: 200, code: 403 }).text, /超级管理员/)
  // HTTP 401 是网关与 Agent 的配置问题：不要求重新登录，也不展示它的 message
  const misconfigured = describeAgentFailure({ httpStatus: 401, code: 401, message: '缺少有效的执行上下文' })
  assert.equal(misconfigured.login, undefined)
  assert.ok(!misconfigured.text.includes('执行上下文'))
})

test('Agent errors map by HTTP status and fall back to search when the service is down', () => {
  assert.equal(describeAgentFailure({ httpStatus: 409, message: '该会话已有执行中的任务' }).text, '该会话已有执行中的任务')
  assert.equal(describeAgentFailure({ httpStatus: 429 }).search, true)
  for (const status of [0, 502, 503, 500]) {
    const failure = describeAgentFailure({ httpStatus: status })
    assert.equal(failure.search, true, String(status))
    assert.equal(failure.retry, true, String(status))
  }
  assert.match(describeAgentFailure({ httpStatus: 410 }).text, /超时/)
})

test('FastAPI JSON messages are replaced with friendly text', () => {
  assert.equal(readableMessage('[{"loc": ["body"]}]', '通用'), '通用')
  assert.equal(readableMessage('{"detail": 1}', '通用'), '通用')
  assert.equal(readableMessage('"x"', '通用'), '通用')
  assert.equal(readableMessage('  ', '通用'), '通用')
  assert.equal(readableMessage('编辑任务需要提供选中的段落', '通用'), '编辑任务需要提供选中的段落')
  assert.equal(describeAgentFailure({ httpStatus: 400, message: '[{"type": "missing"}]' }).text, '请求内容不合法，请修改后重试。')
})

test('notice titles and run errors', () => {
  assert.equal(noticeTitle('insufficient_evidence'), '没有找到足够的资料')
  assert.equal(noticeTitle('out_of_scope'), '这个问题暂时无法回答')
  assert.equal(runErrorText('CANCELLED', null), '已停止。')
  assert.match(runErrorText('EXPIRED', { code: 'WAITING_EXPIRED' }), /超时/)
  assert.match(runErrorText('FAILED', { code: 'MODEL_UNAVAILABLE', message: 'x' }), /模型服务/)
  assert.equal(runErrorText('FAILED', { code: 'TOOL_DENIED', message: '没有权限访问该内容' }), '没有权限访问该内容')
  assert.equal(runErrorText('FAILED', null), '出了点问题，请重试。')
})
