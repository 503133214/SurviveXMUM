import test from 'node:test'
import assert from 'node:assert/strict'

import { shortcutLabel } from '../src/utils/shortcut.js'

test('shows the command key on Apple platforms', () => {
  assert.equal(shortcutLabel({ platform: 'MacIntel' }), '⌘K')
  assert.equal(shortcutLabel({ platform: 'iPhone' }), '⌘K')
  assert.equal(shortcutLabel({ platform: 'iPad' }), '⌘K')
  // Chromium 的 userAgentData 报的是 macOS
  assert.equal(shortcutLabel({ userAgentData: { platform: 'macOS' }, platform: '' }), '⌘K')
})

test('falls back to the user agent when platform is empty', () => {
  const ipadUa = 'Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15'
  assert.equal(shortcutLabel({ platform: '', userAgent: ipadUa }), '⌘K')
  const androidUa = 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36'
  assert.equal(shortcutLabel({ platform: '', userAgent: androidUa }), 'Ctrl K')
})

test('shows Ctrl K everywhere else', () => {
  assert.equal(shortcutLabel({ platform: 'Win32' }), 'Ctrl K')
  assert.equal(shortcutLabel({ platform: 'Linux x86_64' }), 'Ctrl K')
  assert.equal(shortcutLabel({ userAgentData: { platform: 'Windows' }, platform: 'Win32' }), 'Ctrl K')
})

test('does not throw without a navigator', () => {
  assert.equal(shortcutLabel(null), 'Ctrl K')
  assert.equal(shortcutLabel({}), 'Ctrl K')
  // 不传参时读 globalThis.navigator：node 20 没有它，新版 node 有，两种都只能得到这两个值之一
  assert.ok(['⌘K', 'Ctrl K'].includes(shortcutLabel()))
})

test('defaults safely when the global navigator is missing', () => {
  const original = Object.getOwnPropertyDescriptor(globalThis, 'navigator')
  Object.defineProperty(globalThis, 'navigator', { value: undefined, configurable: true, writable: true })
  try {
    assert.equal(shortcutLabel(), 'Ctrl K')
  } finally {
    if (original) Object.defineProperty(globalThis, 'navigator', original)
    else delete globalThis.navigator
  }
})
