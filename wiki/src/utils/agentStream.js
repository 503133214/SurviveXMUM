// 站内问答 Agent 事件流（SSE）的解析与续传规则。都是纯函数，单测在 test/agentStream.test.js。
// 约定见 AGENTS.md「联调站内问答 Agent → 事件流」。

// 收到这几种事件后服务端会关流，不要再重连。
const CLOSING_EVENTS = new Set(['done', 'error', 'clarification_required', 'confirmation_required'])

// 快照处于这些状态时这一轮已有结果（或在等用户补充），不必再听事件流。
const SETTLED_STATUSES = new Set(['SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED', 'WAITING_USER'])

export function isClosingEvent(type) {
  return CLOSING_EVENTS.has(type)
}

export function isSettledStatus(status) {
  return SETTLED_STATUSES.has(status)
}

/**
 * 增量解析 SSE 文本。fetch 读到的一段文本可能在任意位置断开（半行、半帧，甚至 \r\n 中间），
 * 所以按行缓冲，遇到空行才算一帧结束。注释行（`: ping` 心跳）不产生帧；
 * 调用方的空闲计时按“收到任何字节”归零，所以心跳照样能证明连接还活着。
 */
export function createSseParser() {
  let buffer = ''
  let id = null
  let event = null
  let data = []

  function dispatch() {
    const frame = data.length ? { id, event: event || 'message', data: data.join('\n') } : null
    id = null
    event = null
    data = []
    return frame
  }

  function line(text) {
    if (text === '') return dispatch()
    if (text.startsWith(':')) return null
    const colon = text.indexOf(':')
    const field = colon < 0 ? text : text.slice(0, colon)
    let value = colon < 0 ? '' : text.slice(colon + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'id') id = value
    else if (field === 'event') event = value
    else if (field === 'data') data.push(value)
    return null
  }

  return {
    push(chunk) {
      buffer += chunk
      const frames = []
      for (;;) {
        const match = /\r\n|\r|\n/.exec(buffer)
        if (!match) break
        // 末尾单独一个 \r 可能是被切开的 \r\n，等下一段到了再判断
        if (match[0] === '\r' && match.index === buffer.length - 1) break
        const text = buffer.slice(0, match.index)
        buffer = buffer.slice(match.index + match[0].length)
        const frame = line(text)
        if (frame) frames.push(frame)
      }
      return frames
    },
  }
}

/** 帧的 data 是单行 JSON 对象；解析不了就返回 null，一帧坏数据不该让整个流程出错。 */
export function parseFrameData(frame) {
  try {
    const value = JSON.parse(frame && frame.data)
    return value && typeof value === 'object' ? value : null
  } catch {
    return null
  }
}

/**
 * 游标只在帧带合法 id 时前进。snapshot 帧没有 id，拿它更新游标会让重连带上 cursor=undefined，得到 422。
 */
export function nextCursor(current, frame) {
  const id = frame && frame.id
  if (typeof id === 'string' && /^\d+$/.test(id)) return Math.max(current, Number(id))
  return current
}

/** 断线重连前的等待：1、2、4、8 秒，之后一律 10 秒。 */
export function reconnectDelay(attempt) {
  return Math.min(10000, 1000 * 2 ** Math.max(0, attempt - 1))
}
