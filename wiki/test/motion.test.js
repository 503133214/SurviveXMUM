import test from 'node:test'
import assert from 'node:assert/strict'

import { prefersReducedMotion, scrollBehavior } from '../src/utils/motion.js'

// 只认 reduce 这一条媒体查询，其余查询一律不匹配
function fakeWindow(reduce) {
  return {
    matchMedia: (q) => ({ matches: reduce && q === '(prefers-reduced-motion: reduce)' }),
  }
}

test('follows the reduced-motion media query', () => {
  assert.equal(prefersReducedMotion(fakeWindow(true)), true)
  assert.equal(prefersReducedMotion(fakeWindow(false)), false)
})

test('scrolls instantly when reduced motion is requested', () => {
  assert.equal(scrollBehavior(fakeWindow(true)), 'auto')
  assert.equal(scrollBehavior(fakeWindow(false)), 'smooth')
})

test('does not throw without window or matchMedia', () => {
  assert.equal(prefersReducedMotion(null), false)
  assert.equal(prefersReducedMotion({}), false)
  assert.equal(prefersReducedMotion({ matchMedia: () => null }), false)
  assert.equal(prefersReducedMotion({ matchMedia: () => { throw new Error('boom') } }), false)
  assert.equal(scrollBehavior({}), 'smooth')
  // node 里没有 window：不传参时读 globalThis.window，只能退回平滑滚动
  assert.equal(prefersReducedMotion(), false)
  assert.equal(scrollBehavior(), 'smooth')
})
