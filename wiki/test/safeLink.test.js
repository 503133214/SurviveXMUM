import test from 'node:test'
import assert from 'node:assert/strict'

import { resolveLink } from '../src/utils/safeLink.js'

const ORIGIN = 'https://surivivexmum.wiki'

test('site paths open in the app', () => {
  assert.deepEqual(resolveLink('/docs/贡献指南', ORIGIN), { kind: 'internal', to: '/docs/贡献指南' })
  assert.deepEqual(resolveLink('  /changes?page=2#top  ', ORIGIN), { kind: 'internal', to: '/changes?page=2#top' })
  // 加校验之前存下的通知，路径里可能带标题原文的空格
  assert.deepEqual(resolveLink('/docs/生活篇/Grab 打车', ORIGIN), { kind: 'internal', to: '/docs/生活篇/Grab 打车' })
})

test('http(s) links to other sites open as external links', () => {
  assert.deepEqual(resolveLink('https://www.xmu.edu.my/notice', ORIGIN), {
    kind: 'external',
    href: 'https://www.xmu.edu.my/notice',
  })
  assert.deepEqual(resolveLink('HTTP://Example.com', ORIGIN), { kind: 'external', href: 'http://example.com/' })
})

test('full links to this site are routed in the app instead of a new tab', () => {
  assert.deepEqual(resolveLink('https://surivivexmum.wiki/docs/a?x=1#h-2', ORIGIN), {
    kind: 'internal',
    to: '/docs/a?x=1#h-2',
  })
  // 没给 origin 时一律当外链，不猜
  assert.deepEqual(resolveLink('https://surivivexmum.wiki/docs/a'), {
    kind: 'external',
    href: 'https://surivivexmum.wiki/docs/a',
  })
})

test('script, data and other schemes are dropped', () => {
  for (const link of [
    'javascript:alert(1)',
    ' JaVaScRiPt:alert(1)',
    'data:text/html,<script>alert(1)</script>',
    'mailto:a@b.c',
    'vbscript:x',
    'docs/relative',
    'https:/no-host',
    'https://',
  ]) {
    assert.equal(resolveLink(link, ORIGIN), null, link)
  }
})

test('protocol-relative, backslash and control-character tricks are dropped', () => {
  for (const link of [
    '//evil.example',
    '/\\evil.example',
    '\\\\evil.example',
    'java\tscript:alert(1)',
    '/docs/a\nb',
    'https://evil.example\u0000.surivivexmum.wiki',
    'https://a b.example',
  ]) {
    assert.equal(resolveLink(link, ORIGIN), null, JSON.stringify(link))
  }
})

test('missing or non-string values mean no link', () => {
  for (const link of [undefined, null, '', '   ', 42, {}]) {
    assert.equal(resolveLink(link, ORIGIN), null)
  }
})
