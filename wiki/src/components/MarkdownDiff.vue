<template>
  <div class="diff-view">
    <div class="diff-header">
      <div class="side-title">
        <strong>{{ beforeTitle }}</strong>
        <span class="removed-count">− {{ removedCount }} 行</span>
      </div>
      <div class="side-title">
        <strong>{{ afterTitle }}</strong>
        <span class="added-count">+ {{ addedCount }} 行</span>
      </div>
    </div>

    <div class="diff-scroll">
      <div class="diff-table" role="table" :aria-label="ariaLabel">
        <template v-for="row in rows" :key="row.key">
          <button
            v-if="row.type === 'collapsed'"
            class="collapsed-line"
            type="button"
            @click="expandBlock(row.blockId)"
          >
            展开 {{ row.count }} 行未修改内容
          </button>
          <div v-else class="diff-row" role="row">
            <div class="diff-side left" :class="sideClass(row.left)">
              <span class="line-no">{{ row.left?.no || '' }}</span>
              <span class="marker" aria-hidden="true">{{ row.left?.kind === 'removed' ? '−' : '' }}</span>
              <code>{{ row.left?.text ?? ' ' }}</code>
            </div>
            <div class="diff-side right" :class="sideClass(row.right)">
              <span class="line-no">{{ row.right?.no || '' }}</span>
              <span class="marker" aria-hidden="true">{{ row.right?.kind === 'added' ? '+' : '' }}</span>
              <code>{{ row.right?.text ?? ' ' }}</code>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script>
import { diffLines } from 'diff'

const CONTEXT_LINES = 3
const COLLAPSE_THRESHOLD = 10

function partLines(value) {
  const lines = value.replace(/\r\n/g, '\n').split('\n')
  if (lines[lines.length - 1] === '') lines.pop()
  return lines
}

export default {
  name: 'MarkdownDiff',
  props: {
    before: { type: String, default: '' },
    after: { type: String, default: '' },
    beforeTitle: { type: String, default: '当前线上内容' },
    afterTitle: { type: String, default: '投稿修改后' },
    ariaLabel: { type: String, default: '投稿前后 Markdown 差异' },
  },
  data() {
    return { expandedBlocks: [] }
  },
  computed: {
    rawRows() {
      const parts = diffLines(this.before, this.after)
      const rows = []
      let oldNo = 1
      let newNo = 1
      let key = 0
      let index = 0

      while (index < parts.length) {
        const part = parts[index]
        if (!part.added && !part.removed) {
          partLines(part.value).forEach((text) => {
            rows.push({
              key: `line-${key++}`,
              type: 'unchanged',
              left: { text, no: oldNo++, kind: 'unchanged' },
              right: { text, no: newNo++, kind: 'unchanged' },
            })
          })
          index++
          continue
        }

        const removed = []
        const added = []
        while (index < parts.length && (parts[index].added || parts[index].removed)) {
          const changedPart = parts[index]
          const target = changedPart.added ? added : removed
          target.push(...partLines(changedPart.value))
          index++
        }

        const changedRows = Math.max(removed.length, added.length)
        for (let i = 0; i < changedRows; i++) {
          rows.push({
            key: `line-${key++}`,
            type: 'changed',
            left: i < removed.length
              ? { text: removed[i], no: oldNo++, kind: 'removed' }
              : null,
            right: i < added.length
              ? { text: added[i], no: newNo++, kind: 'added' }
              : null,
          })
        }
      }

      return rows
    },
    rows() {
      const output = []
      let index = 0
      let blockId = 0

      while (index < this.rawRows.length) {
        if (this.rawRows[index].type !== 'unchanged') {
          output.push(this.rawRows[index++])
          continue
        }

        let end = index
        while (end < this.rawRows.length && this.rawRows[end].type === 'unchanged') end++
        const run = this.rawRows.slice(index, end)
        const id = blockId++

        if (run.length < COLLAPSE_THRESHOLD || this.expandedBlocks.includes(id)) {
          output.push(...run)
        } else {
          output.push(...run.slice(0, CONTEXT_LINES))
          output.push({
            key: `collapsed-${id}`,
            type: 'collapsed',
            blockId: id,
            count: run.length - CONTEXT_LINES * 2,
          })
          output.push(...run.slice(-CONTEXT_LINES))
        }
        index = end
      }

      return output
    },
    addedCount() {
      return this.rawRows.filter((row) => row.right?.kind === 'added').length
    },
    removedCount() {
      return this.rawRows.filter((row) => row.left?.kind === 'removed').length
    },
  },
  watch: {
    before() { this.expandedBlocks = [] },
    after() { this.expandedBlocks = [] },
  },
  methods: {
    sideClass(side) {
      return side ? side.kind : 'empty'
    },
    expandBlock(id) {
      this.expandedBlocks = [...this.expandedBlocks, id]
    },
  },
}
</script>

<style scoped>
.diff-view { background: var(--bg-surface); }
.diff-header {
  display: grid;
  grid-template-columns: 1fr 1fr;
  border-bottom: 1px solid var(--border);
  background: var(--bg-subtle);
}
.side-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
  padding: 11px 16px;
  color: var(--text-secondary);
  font-size: var(--fs-xs);
}
.side-title + .side-title { border-left: 1px solid var(--border-strong); }
.side-title strong { color: var(--text-primary); font-size: var(--fs-sm); }
/* 增删颜色全部走状态令牌，暗色模式由 global.css 里同名变量自动切换，这里不再单独覆盖 */
.added-count { color: var(--success); font-variant-numeric: tabular-nums; }
.removed-count { color: var(--danger); font-variant-numeric: tabular-nums; }
.diff-scroll { overflow-x: auto; }
.diff-table {
  min-width: 820px;
  font-family: var(--font-mono);
  font-size: 13px;
  line-height: 1.65;
}
.diff-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  border-bottom: 1px solid var(--border);
}
/* 行间分隔线再淡一些；不支持 color-mix() 的浏览器保留上面的普通边框（原因见 MarkdownRenderer 的提示块） */
@supports (color: color-mix(in srgb, red 50%, transparent)) {
  .diff-row { border-bottom-color: color-mix(in srgb, var(--border) 62%, transparent); }
}
.diff-side {
  display: grid;
  grid-template-columns: 50px 26px minmax(0, 1fr);
  min-width: 0;
  min-height: 25px;
}
.diff-side.right { border-left: 1px solid var(--border-strong); }
.diff-side.removed { background: var(--diff-del-bg); }
.diff-side.added { background: var(--diff-add-bg); }
/* 一侧没有对应行时用平铺的浅灰底：和增删的红绿底、普通行的白底都区分得开，
   不再用斜纹渐变（界面整体不用渐变） */
.diff-side.empty { background: var(--bg-subtle); }
.line-no {
  padding: 2px 8px;
  border-right: 1px solid var(--border);
  color: var(--text-muted);
  text-align: right;
  user-select: none;
}
.marker {
  padding: 2px 7px;
  text-align: center;
  user-select: none;
}
.removed .marker { color: var(--danger); }
.added .marker { color: var(--success); }
.diff-side code {
  padding: 2px 12px 2px 3px;
  color: var(--text-body);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  tab-size: 2;
}
.collapsed-line {
  width: 100%;
  padding: 8px 16px;
  border: 0;
  border-bottom: 1px solid var(--border);
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font: inherit;
  text-align: center;
  cursor: pointer;
  transition: background-color var(--dur) ease, color var(--dur) ease;
}
.collapsed-line:hover { color: var(--text-primary); background: var(--bg-hover); }

@media (max-width: 768px) {
  .diff-header { min-width: 820px; }
}
</style>
