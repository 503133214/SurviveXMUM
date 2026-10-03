function selectionFor(content, selectionStart, selectionEnd) {
  const clamp = (value) => Math.max(0, Math.min(content.length, Number.isFinite(value) ? Math.trunc(value) : 0))
  const first = clamp(selectionStart)
  const last = clamp(selectionEnd ?? selectionStart)
  return { start: Math.min(first, last), end: Math.max(first, last) }
}

function replaceSelection(content, start, end, replacement, selectedStart = 0, selectedLength = replacement.length) {
  return {
    content: content.slice(0, start) + replacement + content.slice(end),
    selectionStart: start + selectedStart,
    selectionEnd: start + selectedStart + selectedLength,
  }
}

function wrapSelection(content, start, end, marker, placeholder) {
  const selected = content.slice(start, end)
  const beforeStars = content.slice(0, start).match(/\*+$/)?.[0].length || 0
  const afterStars = content.slice(end).match(/^\*+/)?.[0].length || 0
  const canRemove = marker === '*' ? beforeStars % 2 === 1 && afterStars % 2 === 1 : beforeStars >= 2 && afterStars >= 2
  if (selected && canRemove) {
    return replaceSelection(content, start - marker.length, end + marker.length, selected)
  }
  const text = selected || placeholder
  return replaceSelection(content, start, end, marker + text + marker, marker.length, text.length)
}

function editLines(content, start, end, action) {
  const blockStart = start === 0 ? 0 : content.lastIndexOf('\n', start - 1) + 1
  // 浏览器选区的终点不包含字符，停在下一行开头时不应修改那一行。
  const lastSelected = end > start && content[end - 1] === '\n' ? end - 1 : end
  const nextNewline = content.indexOf('\n', lastSelected)
  const blockEnd = nextNewline < 0 ? content.length : nextNewline
  const lines = content.slice(blockStart, blockEnd).split('\n')
  const listPattern = /^([ \t]{0,3})(?:[-+*]|\d+[.)])[ \t]+/
  const quotePattern = /^([ \t]{0,3})>[ \t]?/
  const headingPattern = /^([ \t]{0,3})#{1,6}[ \t]+/
  const currentPattern = action === 'unordered-list' ? /^[ \t]{0,3}[-+*][ \t]+/
    : action === 'ordered-list' ? /^[ \t]{0,3}\d+[.)][ \t]+/
      : quotePattern
  const canToggle = ['unordered-list', 'ordered-list', 'quote'].includes(action)
  const nonemptyLines = lines.filter((line) => line.trim())
  const remove = canToggle && nonemptyLines.length > 0 && nonemptyLines.every((line) => currentPattern.test(line))
  let itemIndex = 0
  let oldOffset = 0
  let newOffset = 0
  const mapping = []
  const changedLines = lines.map((line) => {
    let oldPrefix = ''
    let newPrefix = ''
    let body = line
    if (line.trim() || lines.length === 1) {
      const pattern = action === 'quote' ? quotePattern
        : action === 'heading' || action === 'subheading' ? headingPattern : listPattern
      const match = line.match(pattern)
      oldPrefix = match?.[0] || ''
      const indent = match?.[1] || ''
      body = line.slice(oldPrefix.length)
      itemIndex += 1
      newPrefix = remove ? indent : indent + ({
        heading: '## ',
        subheading: '### ',
        'unordered-list': '- ',
        'ordered-list': `${itemIndex}. `,
        quote: '> ',
      })[action]
      if (!body.trim() && lines.length === 1) {
        body = action === 'heading' || action === 'subheading' ? '小节标题'
          : action === 'quote' ? '引用文字' : '列表项目'
      }
    }
    const changed = newPrefix + body
    mapping.push({ oldOffset, newOffset, oldLength: line.length, oldPrefix: oldPrefix.length, newPrefix: newPrefix.length, newLength: changed.length })
    oldOffset += line.length + 1
    newOffset += changed.length + 1
    return changed
  })
  const replacement = changedLines.join('\n')
  const mapPosition = (position) => {
    if (position > blockEnd) return position + replacement.length - (blockEnd - blockStart)
    const relative = position - blockStart
    const line = mapping.find((entry) => relative <= entry.oldOffset + entry.oldLength) || mapping.at(-1)
    return blockStart + line.newOffset + line.newPrefix + Math.max(0, relative - line.oldOffset - line.oldPrefix)
  }
  const result = replaceSelection(content, blockStart, blockEnd, replacement)
  if (start === end && lines.length === 1 && !lines[0].trim()) {
    result.selectionStart = blockStart + mapping[0].newPrefix
    result.selectionEnd = blockStart + replacement.length
  } else {
    result.selectionStart = mapPosition(start)
    result.selectionEnd = mapPosition(end)
  }
  return result
}

function insertBlock(content, start, end, block, selectedStart, selectedLength) {
  const before = content.slice(0, start)
  const after = content.slice(end)
  const prefix = !before || before.endsWith('\n\n') ? '' : before.endsWith('\n') ? '\n' : '\n\n'
  const suffix = !after || after.startsWith('\n\n') ? '' : after.startsWith('\n') ? '\n' : '\n\n'
  return replaceSelection(content, start, end, prefix + block + suffix, prefix.length + selectedStart, selectedLength)
}

function escapeLinkLabel(label) {
  return String(label).replace(/\r?\n/g, ' ').replace(/[\\`*_[\]<>!]/g, '\\$&')
}

/** 链接表单只接受常用协议和明确的站内目标，避免把可执行地址写进正文。 */
export function createMarkdownLink(label, url) {
  const destination = String(url ?? '').trim()
  if (!destination || /[\s\u0000-\u001f\u007f<>\\]/u.test(destination)) {
    throw new Error('请输入有效的链接地址')
  }
  const isInternal = /^\/(?!\/)/.test(destination) || destination.startsWith('#')
  let isExternal = false
  if (/^https?:\/\//i.test(destination) || /^mailto:/i.test(destination)) {
    try {
      const parsed = new URL(destination)
      isExternal = ['http:', 'https:'].includes(parsed.protocol) ? Boolean(parsed.hostname)
        : parsed.protocol === 'mailto:' && Boolean(parsed.pathname)
    } catch {
      isExternal = false
    }
  }
  if (!isInternal && !isExternal) {
    throw new Error('链接需使用 http、https、mailto、站内路径或本页锚点')
  }
  // Markdown 会先解码 HTML 实体；保留地址中的字面 &，防止站内路径被实体变成 // 外链。
  return `[${escapeLinkLabel(String(label || '链接文字'))}](<${destination.replace(/&/g, '&amp;')}>)`
}

/** 只替换当前选区或所在行，避免格式化操作重写文章的其余内容。 */
export function applyMarkdownAction(value, selectionStart, selectionEnd, action) {
  const content = String(value ?? '')
  const { start, end } = selectionFor(content, selectionStart, selectionEnd)
  const selected = content.slice(start, end)
  if (action === 'bold') return wrapSelection(content, start, end, '**', '加粗文字')
  if (action === 'italic') return wrapSelection(content, start, end, '*', '强调文字')
  if (['heading', 'subheading', 'unordered-list', 'ordered-list', 'quote'].includes(action)) {
    return editLines(content, start, end, action)
  }
  if (action === 'link') {
    const label = selected || '链接文字'
    const escapedLabel = escapeLinkLabel(label)
    const url = 'https://example.com'
    const link = createMarkdownLink(label, url)
    return replaceSelection(content, start, end, link, selected ? escapedLabel.length + 4 : 1, selected ? url.length : escapedLabel.length)
  }
  if (action === 'code') {
    const code = selected || '在这里输入代码'
    // 长于选区中现有反引号的围栏，能原样容纳示例里的 Markdown 代码块。
    let longestRun = 0
    for (const match of code.matchAll(/`+/g)) longestRun = Math.max(longestRun, match[0].length)
    const fence = '`'.repeat(Math.max(3, longestRun + 1))
    const closingNewline = code.endsWith('\n') ? '' : '\n'
    return insertBlock(content, start, end, `${fence}\n${code}${closingNewline}${fence}`, fence.length + 1, code.length)
  }
  if (action === 'table') {
    const cell = selected ? selected.replace(/\\/g, '\\\\').replace(/\|/g, '\\|').replace(/\r?\n/g, '<br>') : '内容'
    const header = '| 项目 | 说明 |\n| --- | --- |\n| '
    return insertBlock(content, start, end, `${header}${cell} | 在这里补充说明 |`, header.length, cell.length)
  }
  if (action === 'divider') {
    const block = selected ? `---\n\n${selected}` : '---\n\n'
    return insertBlock(content, start, end, block, selected ? 5 : block.length, selected.length)
  }
  return { content, selectionStart: start, selectionEnd: end }
}

/** 工具栏和输入共用历史，避免程序更新正文后浏览器的原生撤销记录与草稿脱节。 */
export function createEditorHistory(initialContent = '') {
  const snapshot = (value, start = 0, end = start) => {
    const content = String(value ?? '')
    const selection = selectionFor(content, start, end)
    return { content, selectionStart: selection.start, selectionEnd: selection.end }
  }
  let entries = [snapshot(initialContent)]
  let index = 0
  const current = () => ({ ...entries[index] })

  return {
    record(content, selectionStart, selectionEnd) {
      const next = snapshot(content, selectionStart, selectionEnd)
      if (next.content === entries[index].content) {
        entries[index] = next
        return current()
      }
      entries = entries.slice(0, index + 1)
      entries.push(next)
      if (entries.length > 100) entries.shift()
      index = entries.length - 1
      return current()
    },
    setSelection(selectionStart, selectionEnd) {
      entries[index] = snapshot(entries[index].content, selectionStart, selectionEnd)
      return current()
    },
    removeText(value) {
      const token = String(value ?? '')
      if (!token) return current()
      const compact = []
      let nextIndex = 0
      for (let entryIndex = 0; entryIndex < entries.length; entryIndex += 1) {
        const entry = entries[entryIndex]
        const occurrences = []
        let position = entry.content.indexOf(token)
        while (position >= 0) {
          occurrences.push(position)
          position = entry.content.indexOf(token, position + token.length)
        }
        const adjustPosition = (original) => {
          let removed = 0
          for (const occurrence of occurrences) {
            if (original <= occurrence) break
            if (original < occurrence + token.length) return occurrence - removed
            removed += token.length
          }
          return original - removed
        }
        const cleaned = snapshot(entry.content.split(token).join(''), adjustPosition(entry.selectionStart), adjustPosition(entry.selectionEnd))
        const previous = compact.at(-1)
        if (previous?.entry.content === cleaned.content) {
          // 相邻记录合并时优先保留当前选区，不能让未来的 redo 快照移动当前光标。
          if (!previous.isCurrent) previous.entry = cleaned
          if (entryIndex === index) previous.isCurrent = true
        } else {
          compact.push({ entry: cleaned, isCurrent: entryIndex === index })
        }
        if (entryIndex === index) nextIndex = compact.length - 1
      }
      entries = compact.map((item) => item.entry)
      index = nextIndex
      return current()
    },
    undo() {
      if (index === 0) return null
      index -= 1
      return current()
    },
    redo() {
      if (index === entries.length - 1) return null
      index += 1
      return current()
    },
    reset(content = '') {
      entries = [snapshot(content)]
      index = 0
      return current()
    },
    canUndo: () => index > 0,
    canRedo: () => index < entries.length - 1,
  }
}
