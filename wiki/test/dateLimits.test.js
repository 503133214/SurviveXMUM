import test from 'node:test'
import assert from 'node:assert/strict'
import { readdir, readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

import { disableFutureDate } from '../src/utils/dateLimits.js'

test('allows today and earlier while disabling later calendar days', () => {
  // 16:30 UTC is already July 17 in Malaysia.
  const reference = new Date('2026-07-16T16:30:00Z')

  assert.equal(disableFutureDate(new Date(2026, 6, 16, 23, 59), reference), false)
  assert.equal(disableFutureDate(new Date(2026, 6, 17, 0, 0), reference), false)
  assert.equal(disableFutureDate(new Date(2026, 6, 17, 23, 59), reference), false)
  assert.equal(disableFutureDate(new Date(2026, 6, 18, 0, 0), reference), true)
})

test('handles month and year boundaries without caching the cutoff', () => {
  const january1 = new Date(2027, 0, 1, 0, 0)

  assert.equal(disableFutureDate(january1, new Date('2026-12-31T15:59:00Z')), true)
  assert.equal(disableFutureDate(january1, new Date('2026-12-31T16:01:00Z')), false)
  assert.equal(disableFutureDate(new Date(2026, 2, 1), new Date('2026-02-28T12:00:00Z')), true)
})

async function vueFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const nested = await Promise.all(entries.map((entry) => {
    const absolute = path.join(directory, entry.name)
    if (entry.isDirectory()) return vueFiles(absolute)
    return entry.isFile() && entry.name.endsWith('.vue') ? [absolute] : []
  }))
  return nested.flat()
}

test('every Element Plus date picker uses the shared future-date guard', async () => {
  const testDir = path.dirname(fileURLToPath(import.meta.url))
  const files = await vueFiles(path.resolve(testDir, '../src'))
  const pickers = []
  const nativeDateInputs = []

  for (const file of files) {
    const source = await readFile(file, 'utf8')
    for (const match of source.matchAll(/<el-date-picker\b[^>]*>/g)) {
      pickers.push({ file, tag: match[0] })
    }
    for (const match of source.matchAll(/<input\b[^>]*\btype=["'](?:date|datetime-local|month|week)["'][^>]*>/gi)) {
      nativeDateInputs.push({ file, tag: match[0] })
    }
  }

  assert.ok(pickers.length > 0, 'expected at least one date picker')
  for (const picker of pickers) {
    assert.match(
      picker.tag,
      /:disabled-date=["']disableFutureDate["']/,
      `${path.relative(path.resolve(testDir, '..'), picker.file)} must disable future dates`,
    )
  }
  assert.deepEqual(
    nativeDateInputs,
    [],
    'native date inputs bypass the shared site-date guard; use the guarded date picker',
  )
})
