import test from 'node:test'
import assert from 'node:assert/strict'

import {
  createSseParser,
  isClosingEvent,
  isSettledStatus,
  nextCursor,
  parseFrameData,
  reconnectDelay,
} from '../src/utils/agentStream.js'

test('parses frames the way the Agent writes them', () => {
  const parser = createSseParser()
  const frames = parser.push(
    'id: 1\nevent: status\ndata: {"status": "QUEUED"}\n\n'
      + 'id: 2\nevent: status\ndata: {"stage": "retrieving", "message": "正在检索 Wiki"}\n\n',
  )
  assert.deepEqual(frames, [
    { id: '1', event: 'status', data: '{"status": "QUEUED"}' },
    { id: '2', event: 'status', data: '{"stage": "retrieving", "message": "正在检索 Wiki"}' },
  ])
  assert.equal(parseFrameData(frames[1]).message, '正在检索 Wiki')
})

test('frames split across reads are reassembled, including a split CRLF', () => {
  const parser = createSseParser()
  assert.deepEqual(parser.push('id: 7\r\nevent: so'), [])
  assert.deepEqual(parser.push('urces\r\ndata: {"sources": []}\r'), [])
  assert.deepEqual(parser.push('\n\r\n'), [{ id: '7', event: 'sources', data: '{"sources": []}' }])
})

test('heartbeat comments produce no frames and multi-line data is joined', () => {
  const parser = createSseParser()
  assert.deepEqual(parser.push(': ping\n\n'), [])
  assert.deepEqual(parser.push('event: x\ndata: a\ndata: b\n\n'), [{ id: null, event: 'x', data: 'a\nb' }])
  // 没有 data 的帧不派发
  assert.deepEqual(parser.push('event: empty\n\n'), [])
})

test('snapshot frames have no id and never move the cursor', () => {
  const parser = createSseParser()
  const [snapshot, status] = parser.push('event: snapshot\ndata: {"status": "RUNNING"}\n\nid: 9\nevent: status\ndata: {}\n\n')
  assert.equal(snapshot.id, null)
  assert.equal(nextCursor(4, snapshot), 4)
  assert.equal(nextCursor(4, status), 9)
  // 重放的旧事件不会让游标倒退
  assert.equal(nextCursor(12, status), 12)
  assert.equal(nextCursor(0, { id: 'abc' }), 0)
  assert.equal(nextCursor(0, null), 0)
})

test('bad JSON in a frame is ignored instead of throwing', () => {
  assert.equal(parseFrameData({ data: '{oops' }), null)
  assert.equal(parseFrameData({ data: '"text"' }), null)
  assert.equal(parseFrameData(null), null)
})

test('closing events and settled statuses', () => {
  for (const type of ['done', 'error', 'clarification_required', 'confirmation_required']) {
    assert.equal(isClosingEvent(type), true, type)
  }
  for (const type of ['status', 'sources', 'delta', 'snapshot', 'proposal']) {
    assert.equal(isClosingEvent(type), false, type)
  }
  for (const status of ['SUCCEEDED', 'FAILED', 'CANCELLED', 'EXPIRED', 'WAITING_USER']) {
    assert.equal(isSettledStatus(status), true, status)
  }
  assert.equal(isSettledStatus('QUEUED'), false)
  assert.equal(isSettledStatus('RUNNING'), false)
})

test('reconnect backs off to ten seconds', () => {
  assert.deepEqual([1, 2, 3, 4, 5, 9].map(reconnectDelay), [1000, 2000, 4000, 8000, 10000, 10000])
  assert.equal(reconnectDelay(0), 1000)
})
