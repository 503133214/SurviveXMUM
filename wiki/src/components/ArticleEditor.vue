<template>
  <section class="article-editor" aria-label="文章正文">
    <div class="editor-heading">
      <div class="view-modes" role="group" aria-label="正文显示方式">
        <button v-for="mode in modes" :key="mode.value" type="button" :aria-pressed="view === mode.value"
          :class="{ active: view === mode.value }" @click="view = mode.value">{{ mode.label }}</button>
      </div>
      <button type="button" class="help-toggle" :aria-expanded="helpOpen" aria-controls="writing-help"
        @click="helpOpen = !helpOpen"><CircleHelp :size="16" />写作帮助</button>
    </div>
    <div v-if="helpOpen" id="writing-help" class="writing-help">
      <p>直接写正文，选中文字后点工具栏即可添加格式；点「预览」查看文章效果。</p>
      <p>小标题用来划分章节，列表适合写步骤。图片可以粘贴或拖入，支持 JPG、PNG、GIF、WebP，最大 10MB。</p>
      <p>也可以直接写 Markdown：<code>## 小标题</code>、<code>**重点**</code>、<code>- 列表项</code>。保存草稿用 Ctrl / ⌘ + S。</p>
    </div>
    <div v-if="view !== 'preview'" class="format-toolbar" role="group" aria-label="正文格式工具">
      <button type="button" title="撤销" aria-label="撤销" :disabled="!canUndo" @mousedown.prevent @click="undo"><Undo2 :size="17" /></button>
      <button type="button" title="重做" aria-label="重做" :disabled="!canRedo" @mousedown.prevent @click="redo"><Redo2 :size="17" /></button>
      <span class="tool-divider" aria-hidden="true"></span>
      <button v-for="tool in formatTools" :key="tool.action" type="button" :title="tool.hint || tool.label"
        @mousedown.prevent @click="format(tool.action)"><component :is="tool.icon" :size="17" /><span>{{ tool.label }}</span></button>
      <span class="tool-divider" aria-hidden="true"></span>
      <button type="button" @mousedown.prevent @click="openLink"><Link :size="17" /><span>链接</span></button>
      <button type="button" :disabled="uploading" @mousedown.prevent @click="$refs.imageInput.click()"><ImagePlus :size="17" /><span>图片</span></button>
      <input ref="imageInput" type="file" class="file-input" tabindex="-1" aria-label="上传图片"
        accept="image/jpeg,image/png,image/gif,image/webp" @change="selectImage" />
    </div>
    <div v-if="!modelValue.trim() && view !== 'preview'" class="starter-row">
      <span>从空白开始，或用提纲起稿：</span>
      <button v-for="item in templates" :key="item.label" type="button" @click="useTemplate(item)">{{ item.label }}</button>
    </div>
    <div class="editor-panes" :class="{ 'is-split': view === 'split' }">
      <div v-show="view !== 'preview'" class="writing-pane" :class="{ 'drag-active': dragging }"
        @dragover.prevent="dragging = true" @dragleave.self="dragging = false" @drop.prevent="dropImage">
        <label class="visually-hidden" for="article-body">正文</label>
        <textarea id="article-body" ref="input" :value="modelValue" class="body-input" spellcheck="false"
          placeholder="在这里写下你想分享的内容…&#10;&#10;选中文字后，可以用上方按钮添加标题、加粗或链接。"
          @input="onInput" @compositionend="onInput" @select="rememberSelection" @click="rememberSelection" @keyup="rememberSelection"
          @keydown="onKeydown" @paste="$emit('paste', $event)"></textarea>
        <div v-if="dragging" class="drop-overlay"><ImagePlus :size="28" /><strong>松开以上传图片</strong></div>
      </div>
      <section v-if="view !== 'write'" class="preview-pane" aria-label="文章预览">
        <p class="preview-caption">文章预览<span v-if="view === 'split'"> · 图片可拖动右下角调整大小</span></p>
        <h2 class="preview-title">{{ title.trim() || '未命名文章' }}</h2>
        <p v-if="description.trim()" class="preview-description">{{ description }}</p>
        <MarkdownRenderer v-if="modelValue.trim()" :content="modelValue" :base-path="basePath" embedded resizable
          @resize-image="$emit('resize-image', $event)" />
        <p v-else class="preview-empty">写下正文后，就能在这里查看排版效果。</p>
      </section>
    </div>
    <div class="editor-status">
      <span role="status" :class="{ 'save-error': saveError }">{{ uploading ? `图片上传中 ${uploadProgress}%` : saveStatus }}</span>
      <span>{{ modelValue.length }} 字</span>
    </div>
    <el-dialog v-model="linkOpen" transition="article-dialog" title="插入链接" width="460px" @open-auto-focus="focusLinkLabel" @closed="restoreSelection">
      <form class="link-form" @submit.prevent="insertLink">
        <label for="link-label">显示文字</label>
        <input id="link-label" ref="linkLabel" v-model="linkLabel" class="link-input" placeholder="例如：学校官网" />
        <label for="link-url">链接地址</label>
        <input id="link-url" v-model="linkUrl" class="link-input" placeholder="https:// 或 /docs/文章路径" aria-describedby="link-help link-error" />
        <p id="link-help" class="field-help">支持网页、邮箱地址（mailto:）和站内链接。</p>
        <p v-if="linkError" id="link-error" class="link-error" role="alert">{{ linkError }}</p>
        <div class="link-actions"><button type="button" class="dialog-button" @click="linkOpen = false">取消</button><button type="submit" class="dialog-button primary">插入链接</button></div>
      </form>
    </el-dialog>
  </section>
</template>

<script>
import { markRaw } from 'vue'
import { Bold, Italic, Heading2, List, ListOrdered, Quote, Code, Table2, Link, ImagePlus, CircleHelp, Undo2, Redo2 } from 'lucide-vue-next'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { applyMarkdownAction, createMarkdownLink, createEditorHistory } from '@/utils/markdownEditing.js'

const formatTools = [
  { action: 'heading', label: '小标题', icon: Heading2 },
  { action: 'bold', label: '加粗', icon: Bold, hint: '加粗（Ctrl / ⌘ + B）' },
  { action: 'italic', label: '斜体', icon: Italic, hint: '斜体（Ctrl / ⌘ + I）' },
  { action: 'unordered-list', label: '列表', icon: List },
  { action: 'ordered-list', label: '步骤', icon: ListOrdered },
  { action: 'quote', label: '引用', icon: Quote },
  { action: 'table', label: '表格', icon: Table2 },
  { action: 'code', label: '代码', icon: Code },
].map(tool => ({ ...tool, icon: markRaw(tool.icon) }))
const templates = [
  { label: '办事指南', content: '简要说明这篇指南能帮助读者完成什么。\n\n## 办理前准备\n\n- 需要准备的材料\n\n## 办理步骤\n\n1. 第一步\n2. 第二步\n\n## 注意事项\n\n补充时间、地点、费用或容易遗漏的细节。\n\n## 参考信息\n\n补充信息来源和适用时间。\n' },
  { label: '经验分享', content: '简要介绍你的经历，以及适合参考这篇文章的人。\n\n## 我的经历\n\n写下实际遇到的情况。\n\n## 实用建议\n\n- 一条有帮助的建议\n\n## 补充说明\n\n说明适用条件和信息的时间，方便读者判断。\n' },
  { label: '常见问题', content: '简要介绍这里回答哪些问题。\n\n## 第一个问题\n\n写下回答，必要时补充步骤或信息来源。\n\n## 第二个问题\n\n写下回答。\n' },
]

export default {
  name: 'ArticleEditor',
  components: { MarkdownRenderer, Link, ImagePlus, CircleHelp, Undo2, Redo2 },
  props: {
    modelValue: { type: String, default: '' }, title: { type: String, default: '' },
    description: { type: String, default: '' }, basePath: { type: String, default: '' },
    uploading: Boolean, uploadProgress: { type: Number, default: 0 },
    saveStatus: { type: String, default: '' }, saveError: Boolean,
  },
  emits: ['update:modelValue', 'image', 'paste', 'drop', 'resize-image', 'save'],
  data() {
    return { view: 'write', compact: false, helpOpen: false, dragging: false, formatTools, templates,
      history: markRaw(createEditorHistory(this.modelValue)), canUndo: false, canRedo: false,
      selection: { start: 0, end: 0 }, linkOpen: false, linkLabel: '', linkUrl: '', linkError: '' }
  },
  computed: {
    modes() { return this.compact ? [{ value: 'write', label: '编辑' }, { value: 'preview', label: '预览' }]
      : [{ value: 'write', label: '编辑' }, { value: 'preview', label: '预览' }, { value: 'split', label: '对照' }] },
  },
  watch: {
    modelValue(value) {
      this.history.record(value, this.selection.start, this.selection.end)
      this.updateHistoryState()
    },
  },
  mounted() {
    this.media = window.matchMedia('(max-width: 760px)')
    this.onViewportChange()
    this.media.addEventListener('change', this.onViewportChange)
  },
  beforeUnmount() { this.media?.removeEventListener('change', this.onViewportChange) },
  methods: {
    onViewportChange() { this.compact = this.media.matches; if (this.compact && this.view === 'split') this.view = 'write' },
    updateHistoryState() { this.canUndo = this.history.canUndo(); this.canRedo = this.history.canRedo() },
    removeUploadPlaceholderFromHistory(token) {
      this.history.removeText(token)
      this.updateHistoryState()
    },
    resetHistory() { this.history.reset(this.modelValue); this.selection = { start: 0, end: 0 }; this.updateHistoryState() },
    rememberSelection() {
      const input = this.$refs.input
      if (!input) return
      this.selection = { start: input.selectionStart, end: input.selectionEnd }
      this.history.setSelection(input.selectionStart, input.selectionEnd)
    },
    onInput(event) {
      if (event.isComposing) return
      this.selection = { start: event.target.selectionStart, end: event.target.selectionEnd }
      this.history.record(event.target.value, this.selection.start, this.selection.end)
      this.updateHistoryState()
      this.$emit('update:modelValue', event.target.value)
    },
    async restoreSelection() {
      await this.$nextTick()
      const input = this.$refs.input
      input?.focus({ preventScroll: true })
      input?.setSelectionRange(this.selection.start, this.selection.end)
    },
    commit(result, record = true) {
      if (!result) return
      this.selection = { start: result.selectionStart, end: result.selectionEnd }
      if (record) this.history.record(result.content, result.selectionStart, result.selectionEnd)
      this.updateHistoryState()
      this.$emit('update:modelValue', result.content)
      this.restoreSelection()
    },
    format(action) {
      this.rememberSelection()
      this.commit(applyMarkdownAction(this.modelValue, this.selection.start, this.selection.end, action))
    },
    undo() { this.commit(this.history.undo(), false) },
    redo() { this.commit(this.history.redo(), false) },
    useTemplate(item) {
      if (this.modelValue.trim()) return
      this.commit({ content: item.content, selectionStart: 0, selectionEnd: item.content.indexOf('\n') })
    },
    openLink() {
      this.rememberSelection()
      this.linkLabel = this.modelValue.slice(this.selection.start, this.selection.end)
      this.linkUrl = ''; this.linkError = ''; this.linkOpen = true
    },
    focusLinkLabel() {
      this.$refs.linkLabel?.focus()
    },
    insertLink() {
      try {
        const markdown = createMarkdownLink(this.linkLabel, this.linkUrl)
        const start = this.selection.start
        this.commit({ content: this.modelValue.slice(0, start) + markdown + this.modelValue.slice(this.selection.end),
          selectionStart: start + markdown.length, selectionEnd: start + markdown.length })
        this.linkOpen = false
      } catch (e) { this.linkError = e.message }
    },
    selectImage(event) {
      const file = event.target.files?.[0]
      event.target.value = ''
      if (file) this.$emit('image', file)
    },
    dropImage(event) { this.dragging = false; this.$emit('drop', event) },
    onKeydown(event) {
      if (event.isComposing || !(event.ctrlKey || event.metaKey) || event.altKey) return
      const key = event.key.toLowerCase()
      if (!['b', 'i', 'k', 'z', 'y', 's'].includes(key)) return
      event.preventDefault(); event.stopPropagation()
      if (key === 'b' || key === 'i') this.format(key === 'b' ? 'bold' : 'italic')
      else if (key === 'k') this.openLink()
      else if (key === 'z') event.shiftKey ? this.redo() : this.undo()
      else if (key === 'y') this.redo()
      else this.$emit('save')
    },
  },
}
</script>

<style scoped>
.article-editor { border: 1px solid var(--border-strong); border-radius: var(--radius); background: var(--bg-surface); min-width: 0; }
.editor-heading, .editor-status { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 16px; }
.editor-heading { border-bottom: 1px solid var(--border); }
.view-modes { display: flex; gap: 4px; }
button { font: inherit; font-size: 14px; cursor: pointer; border-radius: var(--radius-sm); transition: background var(--dur), color var(--dur); }
button:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
.view-modes button { padding: 6px 14px; min-height: 36px; border: 1px solid transparent; color: var(--text-secondary); background: transparent; }
.view-modes button.active { background: var(--accent-soft); color: var(--accent); border-color: var(--accent-soft-strong); font-weight: 600; }
.help-toggle { display: inline-flex; gap: 6px; align-items: center; border: 0; padding: 6px; color: var(--text-secondary); background: transparent; }
.help-toggle:hover, .format-toolbar button:hover:not(:disabled) { background: var(--bg-hover); color: var(--text-primary); }
.writing-help { padding: 12px 18px; border-bottom: 1px solid var(--border); background: var(--bg-subtle); color: var(--text-secondary); font-size: 13px; }
.writing-help p { margin: 4px 0; }
.writing-help code { font-family: var(--font-mono); }
.format-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 3px; padding: 8px 12px; border-bottom: 1px solid var(--border); background: var(--bg-subtle); }
.format-toolbar button { display: inline-flex; align-items: center; justify-content: center; gap: 5px; min-height: 36px; padding: 6px 8px; border: 0; background: transparent; color: var(--text-body); }
.format-toolbar button:disabled { opacity: .45; cursor: not-allowed; }
.tool-divider { width: 1px; height: 20px; margin: 0 3px; background: var(--border-strong); }
.file-input { display: none; }
.starter-row { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; padding: 12px 18px; border-bottom: 1px solid var(--border); color: var(--text-secondary); font-size: 13px; }
.starter-row button { color: var(--accent); background: var(--bg-surface); border: 1px solid var(--border); padding: 4px 8px; min-height: 32px; font-size: 13px; }
.starter-row button:hover { background: var(--accent-soft); }
.editor-panes { display: grid; grid-template-columns: minmax(0, 1fr); }
.editor-panes.is-split { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.writing-pane { display: flex; position: relative; min-width: 0; }
.body-input { width: 100%; min-height: 480px; height: 58vh; border: 0; resize: vertical; padding: 24px; font-family: var(--font-sans); font-size: 16px; line-height: var(--lh-read); background: var(--bg-surface); color: var(--text-body); border-radius: 0; }
.body-input::placeholder { color: var(--text-muted); }
.body-input:focus-visible { outline: 2px solid var(--accent); outline-offset: -2px; }
.is-split .body-input { font-family: var(--font-mono); font-size: 14px; }
.preview-pane { min-width: 0; min-height: 480px; padding: 24px; overflow-wrap: anywhere; }
.is-split .preview-pane { height: 58vh; overflow: auto; border-left: 1px solid var(--border); }
.preview-caption { margin: 0 0 18px; font-size: 12px; color: var(--text-muted); }
.preview-title { margin: 0 0 16px; font-size: 26px; line-height: 1.4; color: var(--text-primary); }
.preview-description { color: var(--text-secondary); font-size: 14px; padding-bottom: 16px; border-bottom: 1px solid var(--border); }
.preview-empty { color: var(--text-muted); font-size: 14px; }
.drop-overlay { position: absolute; inset: 0; display: flex; flex-direction: column; gap: 12px; align-items: center; justify-content: center; border: 2px dashed var(--accent); color: var(--accent); background: var(--bg-surface); pointer-events: none; }
.editor-status { border-top: 1px solid var(--border); font-size: 12px; color: var(--text-muted); font-variant-numeric: tabular-nums; }
.save-error, .link-error { color: var(--danger); }
.visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); }
.link-form { display: flex; flex-direction: column; gap: 8px; }
.link-form label { font-size: 14px; color: var(--text-body); }
.link-input { width: 100%; padding: 10px 12px; border: 1px solid var(--border-strong); border-radius: var(--radius-sm); color: var(--text-primary); background: var(--bg-surface); font: inherit; margin-bottom: 8px; }
.field-help, .link-error { font-size: 13px; margin: 0; }
.field-help { color: var(--text-muted); }
.link-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
.dialog-button { padding: 8px 14px; border: 1px solid var(--border-strong); background: var(--bg-surface); color: var(--text-body); }
.dialog-button.primary { background: var(--accent); border-color: var(--accent); color: var(--accent-contrast); }
@media (max-width: 760px) {
  .editor-heading { padding: 8px; gap: 6px; }
  .view-modes button { padding: 6px 12px; }
  .help-toggle { font-size: 13px; }
  .format-toolbar { padding: 6px; gap: 2px; }
  .format-toolbar button { min-height: 40px; padding: 6px 8px; }
  .tool-divider { display: none; }
  .starter-row { padding: 12px; }
  .starter-row > span { width: 100%; }
  .body-input { min-height: 380px; padding: 16px; }
  .preview-pane { padding: 16px; min-height: 380px; }
  .preview-title { font-size: 22px; }
  .editor-status { padding: 10px 12px; align-items: flex-start; }
  .editor-status > span:last-child { white-space: nowrap; }
}
</style>
