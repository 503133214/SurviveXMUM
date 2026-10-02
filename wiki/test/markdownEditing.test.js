import test from 'node:test'
import assert from 'node:assert/strict'
import MarkdownIt from 'markdown-it'

import { applyMarkdownAction, createEditorHistory, createMarkdownLink } from '../src/utils/markdownEditing.js'

const selectedText = (result) => result.content.slice(result.selectionStart, result.selectionEnd)

test('wraps Chinese selections without changing surrounding text and can undo bold', () => {
  const result = applyMarkdownAction('开头需要强调结尾', 2, 6, 'bold')
  assert.equal(result.content, '开头**需要强调**结尾')
  assert.equal(selectedText(result), '需要强调')
  assert.deepEqual(applyMarkdownAction(result.content, result.selectionStart, result.selectionEnd, 'bold'), {
    content: '开头需要强调结尾', selectionStart: 2, selectionEnd: 6,
  })
})

test('inserts and selects an editable placeholder at an empty selection', () => {
  const result = applyMarkdownAction('前后', 1, 1, 'italic')
  assert.equal(result.content, '前*强调文字*后')
  assert.equal(selectedText(result), '强调文字')
  const heading = applyMarkdownAction('前言\n\n正文', 3, 3, 'heading')
  assert.equal(heading.content, '前言\n## 小节标题\n正文')
  assert.equal(selectedText(heading), '小节标题')
})

test('italic can be added to bold text without accidentally removing a bold marker', () => {
  const result = applyMarkdownAction('**正文**', 2, 4, 'italic')
  assert.equal(result.content, '***正文***')
  assert.equal(applyMarkdownAction(result.content, result.selectionStart, result.selectionEnd, 'italic').content, '**正文**')
})

test('replaces heading levels and leaves unrelated lines byte-for-byte intact', () => {
  const content = '前言\n# 原标题\n正文\n'
  const result = applyMarkdownAction(content, 6, 8, 'subheading')
  assert.equal(result.content, '前言\n### 原标题\n正文\n')
  assert.equal(selectedText(result), content.slice(6, 8))
})

test('formats only selected lines when a selection ends at the next line start', () => {
  const content = '第一项\n第二项\n最后一行\n'
  const result = applyMarkdownAction(content, 0, 8, 'unordered-list')
  assert.equal(result.content, '- 第一项\n- 第二项\n最后一行\n')
  assert.equal(result.content.slice(result.selectionEnd), '最后一行\n')
  assert.equal(applyMarkdownAction(result.content, result.selectionStart, result.selectionEnd, 'unordered-list').content, content)
})

test('converts list styles, numbers nonempty lines, and does not prefix empty lines', () => {
  const content = '- 第一项\n\n- 第二项\n后文'
  const result = applyMarkdownAction(content, 0, content.indexOf('后文'), 'ordered-list')
  assert.equal(result.content, '1. 第一项\n\n2. 第二项\n后文')
})

test('does not turn the trailing newline into an extra quoted paragraph', () => {
  const content = '第一段\n第二段\n'
  const result = applyMarkdownAction(content, 0, content.length, 'quote')
  assert.equal(result.content, '> 第一段\n> 第二段\n')
  assert.equal(result.selectionEnd, result.content.length)
  assert.equal(applyMarkdownAction(result.content, result.selectionStart, result.selectionEnd, 'quote').content, content)
})

test('inserts a new list item at the end without touching previous text', () => {
  const result = applyMarkdownAction('正文\n', 3, 3, 'ordered-list')
  assert.equal(result.content, '正文\n1. 列表项目')
  assert.equal(selectedText(result), '列表项目')
})

test('code fences accommodate embedded fences and keep selected content unchanged', () => {
  const code = '```js\nconst value = 1\n```\n'
  const content = `前言\n\n${code}后文`
  const result = applyMarkdownAction(content, 4, 4 + code.length, 'code')
  assert.equal(result.content, `前言\n\n\`\`\`\`\n${code}\`\`\`\`\n\n后文`)
  assert.equal(selectedText(result), code)
  assert.match(new MarkdownIt().render(result.content), /<code>```js/)
})

test('links keep the selected label and select the destination for immediate replacement', () => {
  const result = applyMarkdownAction('前查看指南后', 1, 5, 'link')
  assert.equal(result.content, '前[查看指南](<https://example.com>)后')
  assert.equal(selectedText(result), 'https://example.com')
  assert.equal(selectedText(applyMarkdownAction('', 0, 0, 'link')), '链接文字')
})

test('link labels and parentheses in URLs render as literal link content', () => {
  const markdown = createMarkdownLink('指南 [旧版] *提示*', 'https://example.com/a_(b)')
  const html = new MarkdownIt().render(markdown)
  assert.match(html, /href="https:\/\/example.com\/a_\(b\)"/)
  assert.match(html, />指南 \[旧版\] \*提示\*<\/a>/)
})

test('accepts common web, email, internal and anchor URLs', () => {
  for (const url of ['https://example.com', 'http://example.com', 'mailto:help@example.com', '/docs/图书馆#借书', '#本页标题']) {
    assert.doesNotThrow(() => createMarkdownLink('链接', url))
  }
})

test('URL entities stay literal so an internal path cannot become a protocol-relative link', () => {
  const markdown = createMarkdownLink('链接', '/&#47;evil.example')
  assert.equal(new MarkdownIt().render(markdown), '<p><a href="/&amp;#47;evil.example">链接</a></p>\n')
  assert.match(new MarkdownIt().render(createMarkdownLink('链接', 'https://example.com?a=1&b=2')), /href="https:\/\/example.com\?a=1&amp;b=2"/)
})

test('rejects executable schemes and destinations that can escape Markdown delimiters', () => {
  for (const url of ['javascript:alert(1)', 'data:text/html,test', 'vbscript:test', '//evil.example', '/\\evil.example', 'https://', 'mailto:', 'https://example.com>\n<script>', 'https://example.com/a b', 'java\nscript:alert(1)', 'relative/path']) {
    assert.throws(() => createMarkdownLink('链接', url), Error, url)
  }
})

test('table and divider insertion preserve surrounding paragraphs and editable selections', () => {
  const table = applyMarkdownAction('前言\n\n后文', 4, 4, 'table')
  assert.ok(table.content.startsWith('前言\n\n| 项目 | 说明 |\n'))
  assert.ok(table.content.endsWith('\n\n后文'))
  assert.equal(selectedText(table), '内容')
  const divider = applyMarkdownAction('前言\n正文\n后文', 3, 5, 'divider')
  assert.equal(divider.content, '前言\n\n---\n\n正文\n\n后文')
  assert.equal(selectedText(divider), '正文')
})

test('invalid selection bounds are clamped and unsupported actions preserve the draft', () => {
  assert.deepEqual(applyMarkdownAction('正文', 99, -4, 'unknown'), {
    content: '正文', selectionStart: 0, selectionEnd: 2,
  })
})

test('history restores the text and selected range before a toolbar action', () => {
  const history = createEditorHistory('一段正文')
  history.setSelection(2, 4)
  const bold = applyMarkdownAction('一段正文', 2, 4, 'bold')
  history.record(bold.content, bold.selectionStart, bold.selectionEnd)
  assert.equal(history.canUndo(), true)
  assert.equal(history.canRedo(), false)
  assert.deepEqual(history.undo(), { content: '一段正文', selectionStart: 2, selectionEnd: 4 })
  assert.equal(history.canUndo(), false)
  assert.equal(history.canRedo(), true)
  assert.deepEqual(history.redo(), bold)
  assert.equal(history.redo(), null)
})

test('selection changes do not add history entries or erase redo', () => {
  const history = createEditorHistory('原稿')
  history.record('新稿', 2, 2)
  history.undo()
  history.record('原稿', 1, 1)
  assert.equal(history.canUndo(), false)
  assert.equal(history.canRedo(), true)
  assert.equal(history.redo().content, '新稿')
})

test('typing after undo clears the abandoned redo branch', () => {
  const history = createEditorHistory('原稿')
  history.record('修改一', 3, 3)
  history.record('修改二', 3, 3)
  history.undo()
  history.record('新的修改', 4, 4)
  assert.equal(history.redo(), null)
  assert.equal(history.undo().content, '修改一')
  assert.equal(history.undo().content, '原稿')
  assert.equal(history.undo(), null)
})

test('history retains at most 100 snapshots and reset isolates the next article', () => {
  const history = createEditorHistory('0')
  for (let index = 1; index <= 120; index += 1) history.record(String(index), 0, 0)
  let last
  let count = 0
  while (history.canUndo()) {
    last = history.undo()
    count += 1
  }
  assert.equal(count, 99)
  assert.equal(last.content, '21')
  assert.deepEqual(history.reset('另一篇文章'), { content: '另一篇文章', selectionStart: 0, selectionEnd: 0 })
  assert.equal(history.undo(), null)
  assert.equal(history.redo(), null)
})

test('returned snapshots cannot mutate stored history and selections stay in bounds', () => {
  const history = createEditorHistory('原稿')
  const returned = history.record('修改后的正文', 200, -2)
  assert.deepEqual(returned, { content: '修改后的正文', selectionStart: 0, selectionEnd: 6 })
  returned.content = '外部误改'
  history.undo()
  assert.equal(history.redo().content, '修改后的正文')
})

test('removing an upload token prevents undo from reviving the temporary comment', () => {
  const history = createEditorHistory('正文')
  const token = '<!-- image-upload:1 -->'
  history.record(`正文${token}`, 2 + token.length, 2 + token.length)
  assert.deepEqual(history.removeText(token), { content: '正文', selectionStart: 2, selectionEnd: 2 })
  assert.equal(history.canUndo(), false)
  history.record('正文![图片](/image.png)', 20, 20)
  assert.deepEqual(history.undo(), { content: '正文', selectionStart: 2, selectionEnd: 2 })
  assert.equal(history.undo(), null)
})

test('token cleanup retains undo and redo branches and maps the current selected range', () => {
  const history = createEditorHistory('原稿')
  const token = '<!-- image-upload:2 -->'
  history.record(`前${token}后`, 0, token.length + 2)
  history.record(`前${token}后续`, token.length + 2, token.length + 3)
  history.record(`前${token}后续更多`, token.length + 3, token.length + 5)
  history.undo()
  assert.deepEqual(history.removeText(token), { content: '前后续', selectionStart: 2, selectionEnd: 3 })
  assert.equal(history.canUndo(), true)
  assert.equal(history.canRedo(), true)
  assert.deepEqual(history.redo(), { content: '前后续更多', selectionStart: 3, selectionEnd: 5 })
  assert.equal(history.undo().content, '前后续')
  assert.deepEqual(history.undo(), { content: '前后', selectionStart: 0, selectionEnd: 2 })
  assert.equal(history.undo().content, '原稿')
})

test('token cleanup collapses duplicate states on both sides without changing the current selection', () => {
  const token = '<!-- image-upload:3 -->'
  const history = createEditorHistory('原稿')
  history.record(`前${token}后`, 1, token.length + 1)
  history.record('前后', 0, 2)
  history.record(`前后${token}`, 2, 2 + token.length)
  history.record('下一段', 3, 3)
  history.undo()
  history.undo()
  assert.deepEqual(history.removeText(token), { content: '前后', selectionStart: 0, selectionEnd: 2 })
  assert.equal(history.redo().content, '下一段')
  assert.equal(history.redo(), null)
  assert.deepEqual(history.undo(), { content: '前后', selectionStart: 0, selectionEnd: 2 })
  assert.equal(history.undo().content, '原稿')
})

test('token cleanup removes every occurrence and clamps selections inside a removed token', () => {
  const token = '<!-- upload -->'
  const history = createEditorHistory(`前${token}中${token}后`)
  history.setSelection(3, token.length + 4)
  assert.deepEqual(history.removeText(token), { content: '前中后', selectionStart: 1, selectionEnd: 2 })
  assert.deepEqual(history.removeText(''), { content: '前中后', selectionStart: 1, selectionEnd: 2 })
  assert.deepEqual(history.removeText('不存在'), { content: '前中后', selectionStart: 1, selectionEnd: 2 })
})
