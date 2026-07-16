import test from 'node:test'
import assert from 'node:assert/strict'

import {
  isLegacyContributionGuidePath,
  legacyContributionGuideRedirect,
  resolveDocAssetSrc,
  resolveDocHref,
} from '../src/utils/docLinks.js'

test('keeps over-deep contribution links inside the docs root', () => {
  assert.equal(resolveDocHref('../../贡献指南.md', '专业篇'), '/docs/贡献指南')
  assert.equal(resolveDocHref('../贡献指南.md', '访谈篇'), '/docs/贡献指南')
})

test('resolves sibling categories and preserves suffixes', () => {
  assert.equal(
    resolveDocHref('../学习篇/科研与项目经验.md#寻找导师', '升学篇'),
    '/docs/学习篇/科研与项目经验#寻找导师',
  )
  assert.equal(resolveDocHref('./endpoints.md?format=json', 'api'), '/docs/api/endpoints?format=json')
})

test('keeps external and special links unchanged', () => {
  for (const href of [
    'https://example.com/docs',
    'mailto:hello@example.com',
    '#本页标题',
    '?preview=true',
    '//cdn.example.com/file',
    '/docs/贡献指南',
  ]) {
    assert.equal(resolveDocHref(href, '专业篇'), href)
  }
})

test('normalizes the legacy root contribution URL', () => {
  assert.equal(resolveDocHref('/贡献指南', '专业篇'), '/docs/贡献指南')
  assert.equal(
    resolveDocHref('/%E8%B4%A1%E7%8C%AE%E6%8C%87%E5%8D%97#投稿', '专业篇'),
    '/docs/贡献指南#投稿',
  )
  assert.equal(isLegacyContributionGuidePath('/贡献指南'), true)
  assert.equal(isLegacyContributionGuidePath('/%E8%B4%A1%E7%8C%AE%E6%8C%87%E5%8D%97'), true)
  assert.equal(isLegacyContributionGuidePath('/%E8%B4%A1%E7%8C%AE%E6%8C%87%E5%8D%97/'), true)
  assert.equal(isLegacyContributionGuidePath('/docs/贡献指南'), false)
  assert.deepEqual(
    legacyContributionGuideRedirect({
      path: '/%E8%B4%A1%E7%8C%AE%E6%8C%87%E5%8D%97/',
      query: { from: 'old' },
      hash: '#投稿',
    }),
    {
      path: '/docs/贡献指南',
      query: { from: 'old' },
      hash: '#投稿',
      replace: true,
    },
  )
  assert.equal(legacyContributionGuideRedirect({ path: '/docs/贡献指南' }), null)
})

test('resolves relative images without escaping the docs root', () => {
  assert.equal(resolveDocAssetSrc('img/医疗/拔智齿-1.jpg', '生活篇'), '/docs/生活篇/img/医疗/拔智齿-1.jpg')
  assert.equal(resolveDocAssetSrc('../../shared/map.png', '人生篇'), '/docs/shared/map.png')
  assert.equal(resolveDocAssetSrc('/wiki/images/upload.png', '生活篇'), '/wiki/images/upload.png')
  assert.equal(resolveDocAssetSrc('data:image/png;base64,AA==', '生活篇'), 'data:image/png;base64,AA==')
})

test('clamps percent-encoded dot segments to the docs root', () => {
  assert.equal(resolveDocHref('%2e%2e/%2E%2E/admin', '专业篇'), '/docs/admin')
  assert.equal(resolveDocHref('.%2e/.%2E/贡献指南.md', '生活篇'), '/docs/贡献指南')
  assert.equal(resolveDocAssetSrc('%2e%2e/%2e%2e/private.png', '人生篇'), '/docs/private.png')
})
