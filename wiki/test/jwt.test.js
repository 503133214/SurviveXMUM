import test from 'node:test'
import assert from 'node:assert/strict'

import { parseJwtPayload } from '../src/utils/jwt.js'

function token(payload) {
  const part = Buffer.from(JSON.stringify(payload)).toString('base64url')
  return `header.${part}.signature`
}

test('decodes base64url payloads containing - and _', () => {
  // ~ 的位模式会编码出 base64url 字母表里的 - 和 _，先自检确实覆盖
  const t = token({ role: 'ADMIN', sub: '~~~~' })
  assert.match(t.split('.')[1], /[-_]/)
  assert.equal(parseJwtPayload(t).role, 'ADMIN')
})

test('decodes non-ASCII (UTF-8) payloads', () => {
  const payload = { nickname: '小明', exp: 1900000000 }
  assert.deepEqual(parseJwtPayload(token(payload)), payload)
})

test('reads exp and role fields of real-shaped tokens', () => {
  const payload = parseJwtPayload(token({ exp: 1900000000, role: 'SUPER_ADMIN' }))
  assert.equal(payload.exp, 1900000000)
  assert.equal(payload.role, 'SUPER_ADMIN')
})

test('returns null for malformed input', () => {
  assert.equal(parseJwtPayload(null), null)
  assert.equal(parseJwtPayload('not-a-jwt'), null)
  assert.equal(parseJwtPayload('a.@@@.c'), null)
})
