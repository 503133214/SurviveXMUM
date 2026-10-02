import test from 'node:test'
import assert from 'node:assert/strict'

import { splitFeatured } from '../src/utils/navGroups.js'

const page = (title, viewCount) => ({ title, path: `篇/${title}`, viewCount })
const titles = (list) => list.map((p) => p.title)

test('small categories list every page in sidebar order, with nothing to expand', () => {
  for (const n of [0, 1, 3, 4]) {
    const list = Array.from({ length: n }, (_, i) => page(`p${i}`, i * 10))
    const { featured, rest } = splitFeatured(list)
    assert.deepEqual(titles(featured), titles(list))
    assert.deepEqual(rest, [])
  }
})

test('five pages: three shown and two behind the toggle, never a toggle for one page', () => {
  const list = [page('a', 1), page('b', 50), page('c', 2), page('d', 40), page('e', 30)]
  const { featured, rest } = splitFeatured(list)
  assert.deepEqual(titles(featured), ['b', 'd', 'e'])
  assert.deepEqual(titles(rest), ['a', 'c'])
})

test('picks the most-read pages but keeps sidebar order for both groups', () => {
  // 专业篇 的真实片段：CST 排在侧栏第 13 位，但阅读量第二
  const list = [page('ECM', 125), page('CHS', 74), page('ACC', 78), page('IBU', 38), page('CST', 110), page('AIT', 66)]
  const { featured, rest } = splitFeatured(list)
  assert.deepEqual(titles(featured), ['ECM', 'ACC', 'CST'])
  assert.deepEqual(titles(rest), ['CHS', 'IBU', 'AIT'])
})

test('ties go to the page that comes first in the sidebar', () => {
  const list = [page('a', 5), page('b', 9), page('c', 5), page('d', 5), page('e', 5)]
  assert.deepEqual(titles(splitFeatured(list).featured), ['a', 'b', 'c'])
})

test('missing or invalid view counts count as 0 and fall back to sidebar order', () => {
  const list = [page('a'), page('b', null), page('c', 'x'), page('d', undefined), page('e')]
  const { featured, rest } = splitFeatured(list)
  assert.deepEqual(titles(featured), ['a', 'b', 'c'])
  assert.deepEqual(titles(rest), ['d', 'e'])
})

test('every page lands in exactly one group, and the input is not mutated', () => {
  const list = Array.from({ length: 24 }, (_, i) => page(`m${i}`, (i * 37) % 23))
  const before = titles(list)
  const { featured, rest } = splitFeatured(list)
  assert.equal(featured.length, 3)
  assert.equal(rest.length, 21)
  assert.deepEqual([...titles(featured), ...titles(rest)].sort(), [...before].sort())
  assert.deepEqual(titles(list), before)
})

test('limits are configurable and bad input is tolerated', () => {
  const list = [page('a', 1), page('b', 3), page('c', 2), page('d', 0)]
  assert.deepEqual(titles(splitFeatured(list, { max: 3, featured: 2 }).featured), ['b', 'c'])
  assert.deepEqual(splitFeatured(null), { featured: [], rest: [] })
  assert.deepEqual(splitFeatured(undefined), { featured: [], rest: [] })
})
