import test from 'node:test'
import assert from 'node:assert/strict'
import { BookOpen, FileText, Flag, PlaneLanding, Activity, Languages, Sprout, Award, Plane, Mic } from 'lucide-vue-next'

import { resolveIcon, emojiIcon, badgeIcon, iconLabel, ICON_CHOICES } from '../src/utils/icons.js'

// 线上内容里实际出现过的全部 emoji（2026-09 的内容清单）
const LIVE_EMOJI = '✍️ 🌏 📡 🏫 👀 ✈️ 🖥️ 🔑 🛡️ 💯 🏠 🍜 🏛️ 🏨 🏘️ 💳 🐱 🎌 🛒 🏥 🗺️ 🏃‍♀ 🇲🇾 🎓 ⌨️ 🌐 🛠️ 📊 🔄 🔬 📚 👥 🎯 📖 💰 📈 🔤 📢 📰 🔐 📐 ⚛️ 💻 🤖 🌊 🐋 🌿 ⚗️ 📟 🎬 📝 🌱 🏅 💼 🎤 🏢 💡 🎙️ 🔌 🧭'.split(' ')

test('every emoji used by live content and the editor picker maps to an icon', () => {
  for (const e of [...LIVE_EMOJI, ...ICON_CHOICES]) {
    assert.ok(emojiIcon(e), `no icon for ${e} (${[...e].map((c) => c.codePointAt(0).toString(16)).join(' ')})`)
  }
})

test('variation selectors and ZWJ sequences normalise to the base emoji', () => {
  assert.equal(emojiIcon('✈️'), Plane)
  assert.equal(emojiIcon('✈'), Plane)
  assert.equal(emojiIcon('🏃‍♀'), Activity)
  assert.equal(emojiIcon('🇲🇾'), Flag)
  assert.equal(emojiIcon(''), null)
})

test('resolution order: title keyword, emoji, category, generic', () => {
  assert.equal(resolveIcon({ title: '吉隆坡机场指南', icon: '', category: '入学篇' }), PlaneLanding)
  assert.equal(resolveIcon({ title: 'XMUM 英语入学考试', icon: '💯', category: '入学篇' }), Languages)
  assert.equal(resolveIcon({ title: '二次元指南', icon: '🧿', category: '访谈篇' }), Mic)
  assert.equal(resolveIcon({ title: '随便', icon: '🧿', category: '不存在' }), FileText)
  assert.equal(resolveIcon({ kind: 'category', category: '学习篇', icon: '🎓' }), BookOpen)
  assert.equal(resolveIcon({ kind: 'category', category: '新篇章', icon: '🧿' }), BookOpen)
})

test('badges resolve by id, then emoji, then a default', () => {
  assert.equal(badgeIcon({ id: 'first-contribution', icon: '🌱' }), Sprout)
  assert.equal(badgeIcon({ id: 'unknown', icon: '🌱' }), Sprout)
  assert.equal(badgeIcon({ id: 'unknown', icon: '🧿' }), Award)
})

test('editor icon choices are distinct once rendered', () => {
  const icons = ICON_CHOICES.map((e) => emojiIcon(e))
  assert.equal(new Set(icons).size, icons.length)
})

test('editor icon choices have distinct readable names for search and screen readers', () => {
  const labels = ICON_CHOICES.map(iconLabel)
  assert.equal(new Set(labels).size, ICON_CHOICES.length)
  for (const label of labels) {
    assert.match(label, /^[\u4e00-\u9fff]+$/)
    assert.notEqual(label, '其他图标')
  }
  assert.equal(iconLabel('✈️'), iconLabel('✈'))
  assert.equal(iconLabel(''), '未选择图标')
  assert.equal(iconLabel('🧿'), '其他图标')
})
