import test from 'node:test'
import assert from 'node:assert/strict'

import { slugify } from '../src/utils/slug.js'

test('slugify keeps CJK, drops punctuation and collapses dashes', () => {
  assert.equal(slugify('GPA 的计算'), 'gpa-的计算')
  assert.equal(slugify('  一门课（Course）的绩点  '), '一门课course的绩点')
  assert.equal(slugify('A -- B'), 'a-b')
  assert.equal(slugify('!!!'), '')
})

test('recent pages dedupe, cap at six and survive a broken store', async () => {
  const store = new Map()
  globalThis.localStorage = {
    getItem: (k) => (store.has(k) ? store.get(k) : null),
    setItem: (k, v) => store.set(k, String(v)),
  }
  const { readRecent, pushRecent } = await import('../src/utils/recentPages.js')

  for (let i = 0; i < 8; i++) pushRecent({ path: `p${i}`, title: `T${i}` })
  pushRecent({ path: 'p3', title: 'T3 again' })
  const list = readRecent()
  assert.equal(list.length, 6)
  assert.deepEqual(list.slice(0, 2).map((p) => p.path), ['p3', 'p7'])
  assert.equal(list[0].title, 'T3 again')

  store.set('wiki-recent-pages', '{not json')
  assert.deepEqual(readRecent(), [])

  globalThis.localStorage = { getItem() { throw new Error('denied') }, setItem() { throw new Error('denied') } }
  assert.deepEqual(readRecent(), [])
  assert.doesNotThrow(() => pushRecent({ path: 'x' }))
})

test('relative time reads naturally and falls back to a date after a month', async () => {
  const { relativeTime, formatDate } = await import('../src/utils/relativeTime.js')
  const now = new Date('2026-09-30T12:00:00+08:00').getTime()
  assert.equal(relativeTime('2026-09-30T11:59:30+08:00', now), '刚刚')
  assert.equal(relativeTime('2026-09-30T11:15:00+08:00', now), '45分钟前')
  assert.equal(relativeTime('2026-09-29T12:00:00+08:00', now), '昨天')
  assert.equal(relativeTime('2026-09-25T12:00:00+08:00', now), '5天前')
  assert.equal(relativeTime('2026-08-11T18:18:08+08:00', now), formatDate('2026-08-11T18:18:08+08:00'))
  assert.equal(relativeTime('not a date', now), '')
})
