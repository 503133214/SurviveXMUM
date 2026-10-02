<template>
  <div class="edit-page">
    <div class="edit-head">
      <div>
        <h1 class="edit-title">{{ isUpdate ? '编辑文档' : '撰写新文档' }}</h1>
        <p class="edit-sub">
          {{ isUpdate ? `正在修改：${targetPath}` : '新建一篇 wiki 文档' }}
          · 提交后需管理员审核通过才会发布
        </p>
      </div>
      <div class="edit-actions">
        <span v-if="draftSavedAt" class="draft-saved-hint">已保存 {{ draftSavedAt.slice(11) || draftSavedAt }}</span>
        <button class="btn-ghost" @click="openDrafts">草稿箱{{ drafts.length ? ` (${drafts.length})` : '' }}</button>
        <button class="btn-ghost" :disabled="draftSaving" @click="manualSaveDraft">
          {{ draftSaving ? '保存中…' : '保存草稿' }}
        </button>
        <button class="btn-ghost" @click="$router.back()">取消</button>
        <button class="btn-solid" :disabled="submitting || uploadingImage" @click="submit">
          {{ uploadingImage ? '等待图片上传…' : submitting ? '提交中…' : '提交审核' }}
        </button>
      </div>
    </div>

    <el-dialog v-model="draftsOpen" title="草稿箱" width="560px">
      <div v-if="draftsLoading" class="drafts-loading">加载中…</div>
      <el-empty v-else-if="!drafts.length" description="暂无草稿" />
      <ul v-else class="draft-list">
        <li v-for="d in drafts" :key="d.id">
          <span class="draft-type" :class="d.type === 'UPDATE' ? 't-upd' : 't-new'">
            {{ d.type === 'UPDATE' ? '编辑' : '新建' }}
          </span>
          <!-- 用 button 而不是可点击的 div：键盘也能 Tab 到并回车打开草稿 -->
          <button type="button" class="draft-main" @click="loadDraftItem(d)">
            <span class="draft-title">{{ d.title || '（未命名草稿）' }}</span>
            <span class="draft-meta">
              <template v-if="d.type === 'UPDATE'">{{ d.targetPath }} · </template>{{ d.updatedAt }}
            </span>
          </button>
          <el-button link type="danger" size="small" @click="removeDraftItem(d)">删除</el-button>
        </li>
      </ul>
    </el-dialog>

    <div class="meta-card">
      <div class="meta-card-head">文档信息</div>
      <div class="meta-grid">
      <div class="field" v-if="!isUpdate">
        <label>分类</label>
        <select v-model="form.categorySlug" class="inp">
          <option value="">（顶级 / 无分类）</option>
          <option v-for="c in cats" :key="c.slug" :value="c.slug">{{ c.label }}</option>
        </select>
      </div>
      <div class="field" v-else>
        <label>分类</label>
        <input class="inp" :value="form.categorySlug || '（顶级）'" disabled />
      </div>

      <div class="field">
        <label>标题与图标</label>
        <div class="title-row">
          <div class="icon-picker" ref="iconPicker">
            <button
              type="button"
              class="icon-btn"
              :class="{ 'has-icon': form.icon }"
              :title="form.icon ? '更换或清除图标' : '选择图标（可选）'"
              @click="iconPanelOpen = !iconPanelOpen"
            >
              <WikiIcon v-if="form.icon" :icon="form.icon" :title="form.title" :category="form.categorySlug" :size="20" />
              <Plus v-else :size="18" :stroke-width="2" />
            </button>
            <div v-if="iconPanelOpen" class="icon-panel">
              <div class="icon-grid">
                <button
                  v-for="e in iconPresets"
                  :key="e"
                  type="button"
                  class="icon-cell"
                  :class="{ selected: sameIcon(form.icon, e) }"
                  @click="pickIcon(e)"
                ><component :is="iconFor(e)" :size="18" :stroke-width="1.75" /></button>
              </div>
              <div class="icon-panel-foot">
                <input
                  v-model="customIcon"
                  class="inp icon-custom"
                  placeholder="或粘贴 emoji 后回车，自动换成相近图标"
                  @keydown.enter.prevent="pickIcon(customIcon)"
                />
                <button v-if="form.icon" type="button" class="icon-clear" @click="pickIcon('')">清除</button>
              </div>
            </div>
          </div>
          <input v-model="form.title" class="inp" placeholder="例如：图书馆使用指南" :disabled="isUpdate" />
        </div>
      </div>

      <div class="field span2">
        <label>简介（可选，留空将自动从正文提取）</label>
        <input v-model="form.description" class="inp" maxlength="200" :placeholder="descPlaceholder" />
      </div>
      </div>
    </div>

    <div class="editor-grid">
      <div
        class="pane editor-pane"
        :class="{ 'drag-active': isDragging }"
        @dragover.prevent="isDragging = true"
        @dragleave="isDragging = false"
        @drop.prevent="handleDrop"
      >
        <div class="pane-head">
          <span>Markdown</span>
          <div class="pane-tools">
            <span v-if="uploadingImage" class="upload-progress">上传中 {{ uploadProgress }}%</span>
            <button
              type="button"
              class="image-upload-btn"
              :disabled="uploadingImage"
              title="上传图片，也可以直接粘贴或拖入图片"
              @click="$refs.imageInput.click()"
            >
              <el-icon><Picture /></el-icon>
              {{ uploadingImage ? '上传中' : '插入图片' }}
            </button>
            <span class="pane-hint">{{ charCount }} 字</span>
            <input
              ref="imageInput"
              class="visually-hidden"
              type="file"
              accept="image/jpeg,image/png,image/gif,image/webp"
              @change="handleFileSelect"
            />
          </div>
        </div>
        <textarea
          ref="markdownInput"
          v-model="form.content"
          class="md-input"
          placeholder="# 标题&#10;&#10;在这里用 Markdown 写正文…"
          spellcheck="false"
          @paste="handlePaste"
        ></textarea>
        <div v-if="isDragging" class="drop-overlay">
          <el-icon :size="28"><Picture /></el-icon>
          <strong>松开以上传图片</strong>
          <span>支持 JPG、PNG、GIF、WebP，最大 10MB</span>
        </div>
      </div>
      <div class="pane">
        <div class="pane-head">
          <span>实时预览</span>
        </div>
        <div class="md-preview markdown-scope">
          <MarkdownRenderer
            v-if="form.content.trim()"
            :content="form.content"
            :base-path="baseDir"
            embedded
            resizable
            @resize-image="onImageResize"
          />
          <p v-else class="preview-empty">预览将在这里实时显示…</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { markRaw, nextTick } from 'vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import WikiIcon from '@/components/WikiIcon.vue'
import { Plus } from 'lucide-vue-next'
import { ICON_CHOICES, emojiIcon, normalizeEmoji, resolveIcon } from '@/utils/icons.js'
import { ElMessage } from 'element-plus'
import { Picture } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { categories, fetchPageContent, loadManifest, state as wikiState } from '@/wiki'
import { submitRevision, uploadImage,
  saveDraft, listDrafts, getDraft, getDraftByPath, deleteDraft } from '@/net/index.js'

const ALLOWED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp'])
const MAX_IMAGE_SIZE = 10 * 1024 * 1024
// 标题拼进页面路径，这些字符会破坏路由（与后端 TitleUtil 一致）
const ILLEGAL_TITLE = /[/\\#?%]|\.\./
// 图标选择器：数据库里存的仍是 emoji（后端与历史内容的约定），
// 界面上显示成对应的线性图标，见 utils/icons.js

export default {
  name: 'EditPage',
  components: { MarkdownRenderer: markRaw(MarkdownRenderer), Picture, WikiIcon, Plus },
  props: { targetPath: { type: String, default: '' } },
  data() {
    return {
      submitting: false,
      uploadingImage: false,
      uploadProgress: 0,
      isDragging: false,
      form: { categorySlug: '', title: '', icon: '', description: '', content: '' },
      baseVersion: null,
      iconPanelOpen: false,
      customIcon: '',
      // 草稿
      draftId: null,
      draftSavedAt: '',
      draftSaving: false,
      draftsOpen: false,
      draftsLoading: false,
      drafts: [],
      autoSaveTimer: null,
      lastSavedSnapshot: '',
    }
  },
  watch: {
    form: { deep: true, handler() { this.scheduleAutoSave() } },
    // /edit/A → /edit/B（或 草稿箱 切换新建草稿）是同一路由记录，组件被复用、
    // mounted 不会重跑，这里手动重新初始化。
    targetPath() { if (this.$route.name === 'Edit') this.initFromRoute() },
    '$route.query.draft'() { if (this.$route.name === 'Edit') this.initFromRoute() },
  },
  computed: {
    isUpdate() {
      return !!this.targetPath
    },
    cats() {
      return categories()
    },
    iconPresets() {
      return ICON_CHOICES
    },
    // 与后端 MarkdownUtil.extractSummary 同一规则：正文首个普通段落
    autoSummary() {
      const lines = (this.form.content || '').split(/\r?\n/)
      let inFence = false
      const para = []
      for (const line of lines) {
        if (/^\s*(```|~~~)/.test(line)) { inFence = !inFence; continue }
        if (inFence) continue
        let t = line.trim()
        if (t.startsWith('>')) t = t.slice(1).trim()
        if (!t) { if (para.length) break; continue }
        if (/^(#|!\[|<|\|)/.test(t) || /^[-*_]{3,}$/.test(t)) {
          if (para.length) break
          continue
        }
        para.push(t.replace(/^([-*+]|\d+\.)\s+/, ''))
      }
      if (!para.length) return ''
      const s = para.join(' ')
        .replace(/!\[[^\]]*\]\([^)]*\)/g, '')
        .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
        .replace(/<[^>]+>/g, '')
        .replace(/[*_`~]+/g, '')
        .replace(/\s+/g, ' ')
        .trim()
      return s.length > 120 ? s.slice(0, 120) + '…' : s
    },
    descPlaceholder() {
      return this.autoSummary
        ? `留空自动使用：${this.autoSummary}`
        : '一句话描述这篇文档'
    },
    baseDir() {
      const p = this.targetPath || (this.form.categorySlug ? `${this.form.categorySlug}/x` : '')
      return p.includes('/') ? p.slice(0, p.lastIndexOf('/')) : ''
    },
    charCount() {
      return this.form.content.length
    },
  },
  async mounted() {
    if (!wikiState.loaded) await loadManifest()
    await this.initFromRoute()
    this.refreshDrafts()
    document.addEventListener('click', this.onDocClick)
  },
  beforeUnmount() {
    document.removeEventListener('click', this.onDocClick)
    clearTimeout(this.autoSaveTimer)
    // 离开页面前尽力保存未落盘的改动（不阻塞导航，失败静默）
    if (this.hasDraftWorthSaving() && this.snapshot() !== this.lastSavedSnapshot) {
      this.doSaveDraft(true)
    }
  },
  methods: {
    onDocClick(e) {
      if (this.iconPanelOpen && this.$refs.iconPicker && !this.$refs.iconPicker.contains(e.target)) {
        this.iconPanelOpen = false
      }
    },
    iconFor(e) {
      return markRaw(emojiIcon(e) || resolveIcon({}))
    },
    sameIcon(a, b) {
      return !!a && normalizeEmoji(a) === normalizeEmoji(b)
    },
    pickIcon(e) {
      this.form.icon = (e || '').trim()
      this.iconPanelOpen = false
      this.customIcon = ''
    },
    handleFileSelect(event) {
      const file = event.target.files?.[0]
      event.target.value = ''
      if (file) this.startImageUpload(file)
    },
    handlePaste(event) {
      const clipboardFiles = Array.from(event.clipboardData?.items || [])
        .filter((item) => item.kind === 'file')
        .map((item) => item.getAsFile())
        .filter(Boolean)
      const file = [...clipboardFiles, ...Array.from(event.clipboardData?.files || [])]
        .find((item) => ALLOWED_IMAGE_TYPES.has(item.type))
      if (!file) return
      event.preventDefault()
      this.startImageUpload(file)
    },
    handleDrop(event) {
      this.isDragging = false
      const file = Array.from(event.dataTransfer?.files || [])
        .find((item) => ALLOWED_IMAGE_TYPES.has(item.type))
      if (!file) {
        ElMessage.warning('请拖入 JPG、PNG、GIF 或 WebP 图片')
        return
      }
      this.startImageUpload(file)
    },
    validateImage(file) {
      if (!ALLOWED_IMAGE_TYPES.has(file.type)) {
        ElMessage.warning('仅支持 JPG、PNG、GIF 和 WebP 图片')
        return false
      }
      if (file.size > MAX_IMAGE_SIZE) {
        ElMessage.warning('图片不能超过 10MB')
        return false
      }
      return true
    },
    insertUploadPlaceholder(file) {
      const textarea = this.$refs.markdownInput
      const start = textarea?.selectionStart ?? this.form.content.length
      const end = textarea?.selectionEnd ?? start
      const id = `${Date.now()}-${Math.random().toString(36).slice(2)}`
      const placeholder = `<!-- image-upload:${id} -->`
      const prefix = start > 0 && this.form.content[start - 1] !== '\n' ? '\n' : ''
      const suffix = end < this.form.content.length && this.form.content[end] !== '\n' ? '\n' : ''
      const insertion = `${prefix}${placeholder}${suffix}`
      this.form.content = this.form.content.slice(0, start) + insertion + this.form.content.slice(end)
      return { placeholder, cursor: start + insertion.length }
    },
    replaceUploadPlaceholder(placeholder, replacement, fallbackCursor) {
      const index = this.form.content.indexOf(placeholder)
      if (index >= 0) {
        this.form.content = this.form.content.replace(placeholder, replacement)
        return index + replacement.length
      }
      const cursor = Math.min(fallbackCursor, this.form.content.length)
      this.form.content = this.form.content.slice(0, cursor) + replacement + this.form.content.slice(cursor)
      return cursor + replacement.length
    },
    removeUploadPlaceholder(placeholder) {
      this.form.content = this.form.content.replace(placeholder, '')
    },
    imageMarkdown(file, data) {
      if (data?.markdown && data.markdown !== `![](${data.url})`) return data.markdown
      const alt = file.name
        .replace(/\.[^.]+$/, '')
        .replace(/[\[\]]/g, '')
        .trim() || '图片'
      return `![${alt}](${data.url})`
    },
    startImageUpload(file) {
      if (this.uploadingImage) {
        ElMessage.warning('请等待当前图片上传完成')
        return
      }
      if (!this.validateImage(file)) return

      const { placeholder, cursor } = this.insertUploadPlaceholder(file)
      this.uploadingImage = true
      this.uploadProgress = 0
      uploadImage(
        file,
        (progress) => { this.uploadProgress = progress },
        async (data) => {
          if (!data?.url) {
            this.removeUploadPlaceholder(placeholder)
            this.uploadingImage = false
            this.uploadProgress = 0
            ElMessage.error('上传接口未返回图片地址')
            return
          }
          const markdown = this.imageMarkdown(file, data)
          const nextCursor = this.replaceUploadPlaceholder(placeholder, markdown, cursor)
          this.uploadingImage = false
          this.uploadProgress = 100
          await nextTick()
          const textarea = this.$refs.markdownInput
          textarea?.focus()
          textarea?.setSelectionRange(nextCursor, nextCursor)
          ElMessage.success('图片已插入')
        },
        (message) => {
          this.removeUploadPlaceholder(placeholder)
          this.uploadingImage = false
          this.uploadProgress = 0
          ElMessage.error(message || '图片上传失败')
        }
      )
    },
    onImageResize({ index, width }) {
      // 预览里第 index 张图片被拖动 → 把对应的 Markdown 图片宽度改写为 =<width>x。
      // 逐行处理并跳过代码围栏：围栏里的 ![](…) 不会渲染成图片，计数时必须排除，
      // 否则序号与预览错位、改错图。
      let inFence = false
      let i = -1
      this.form.content = this.form.content
        .split('\n')
        .map((line) => {
          if (/^\s*(```|~~~)/.test(line)) {
            inFence = !inFence
            return line
          }
          if (inFence) return line
          return line.replace(/!\[[^\]]*\]\([^)]*\)/g, (match) => {
            i += 1
            if (i !== index) return match
            const parsed = match.match(/^!\[([^\]]*)\]\((.*)\)$/)
            if (!parsed) return match
            const alt = parsed[1]
            const url = parsed[2].replace(/\s+=\d+x\d*\s*$/, '').trim()
            return `![${alt}](${url} =${width}x)`
          })
        })
        .join('\n')
    },
    // ---------- 初始化 / 路由复用 ----------
    resetFormState() {
      clearTimeout(this.autoSaveTimer)
      this.form = { categorySlug: '', title: '', icon: '', description: '', content: '' }
      this.baseVersion = null
      this.draftId = null
      this.draftSavedAt = ''
      this.lastSavedSnapshot = ''
    },
    async initFromRoute() {
      this.resetFormState()
      if (this.isUpdate) {
        try {
          const d = await fetchPageContent(this.targetPath)
          this.form.categorySlug = d.categorySlug || ''
          this.form.title = d.title || ''
          this.form.icon = d.icon || ''
          this.form.description = d.description || ''
          this.form.content = d.content || ''
          this.baseVersion = d.version ?? 0
        } catch (e) {
          ElMessage.error('无法加载原文内容')
        }
        // 灌入原文不算改动：先对齐快照，避免刚打开就自动存了一份与线上相同的草稿
        this.lastSavedSnapshot = this.snapshot()
        // 原文灌入后再检查是否有这页的编辑草稿，避免草稿被覆盖
        this.$nextTick(() => this.checkDraftForPath())
      } else {
        this.lastSavedSnapshot = this.snapshot()
        if (this.$route.query.draft) {
          // 从草稿箱进入：直接载入指定草稿
          getDraft(this.$route.query.draft, (d) => this.applyDraft(d), () => {})
        }
      }
    },

    // ---------- 草稿 ----------
    draftPayload() {
      return {
        id: this.draftId || undefined,
        type: this.isUpdate ? 'UPDATE' : 'CREATE',
        path: this.isUpdate ? this.targetPath : undefined,
        categorySlug: this.form.categorySlug || null,
        title: this.form.title,
        icon: this.form.icon,
        description: this.form.description,
        content: this.form.content,
        baseVersion: this.isUpdate ? this.baseVersion : undefined,
      }
    },
    snapshot() {
      const p = this.draftPayload()
      delete p.id
      return JSON.stringify(p)
    },
    hasDraftWorthSaving() {
      return !!(this.form.title.trim() || this.form.content.trim()
        || this.form.description.trim())
    },
    scheduleAutoSave() {
      clearTimeout(this.autoSaveTimer)
      if (this.submitting) return
      this.autoSaveTimer = setTimeout(() => {
        if (this.submitting) return
        if (!this.hasDraftWorthSaving()) return
        if (this.snapshot() === this.lastSavedSnapshot) return
        this.doSaveDraft(true)
      }, 3000)
    },
    doSaveDraft(silent) {
      if (!silent) {
        if (!this.hasDraftWorthSaving()) return ElMessage.warning('内容为空，无需保存草稿')
        this.draftSaving = true
      }
      const snap = this.snapshot()
      saveDraft(this.draftPayload(),
        (d) => {
          this.draftId = d.id
          this.draftSavedAt = d.savedAt || ''
          this.lastSavedSnapshot = snap
          this.draftSaving = false
          if (!silent) {
            ElMessage.success('草稿已保存')
            this.refreshDrafts()
          }
        },
        (msg) => {
          this.draftSaving = false
          if (!silent) ElMessage.error(msg || '草稿保存失败')
        },
        // 网络层错误：自动保存完全静默（离线打字不弹全局警告），手动保存才提示
        silent ? () => {} : undefined)
    },
    manualSaveDraft() {
      this.doSaveDraft(false)
    },
    refreshDrafts() {
      listDrafts((d) => { this.drafts = d || [] }, () => {})
    },
    openDrafts() {
      this.draftsOpen = true
      this.draftsLoading = true
      listDrafts(
        (d) => { this.drafts = d || []; this.draftsLoading = false },
        () => { this.draftsLoading = false })
    },
    applyDraft(d) {
      if (!d) return
      if (!this.isUpdate) this.form.categorySlug = d.categorySlug || ''
      this.form.title = d.title || this.form.title
      this.form.icon = d.icon || ''
      this.form.description = d.description || ''
      this.form.content = d.content || ''
      this.draftId = d.id
      this.draftSavedAt = d.updatedAt || ''
      // 灌入草稿本身不算“新改动”，避免马上又自动保存一遍
      this.$nextTick(() => { this.lastSavedSnapshot = this.snapshot() })
    },
    checkDraftForPath() {
      getDraftByPath(this.targetPath, (d) => {
        if (!d) return
        ElMessageBox.confirm(
          `检测到你在 ${d.updatedAt} 保存过这篇文档的草稿，是否恢复？`,
          '发现草稿',
          {
            confirmButtonText: '恢复草稿',
            cancelButtonText: '丢弃草稿',
            distinguishCancelAndClose: true,
            type: 'info',
          }
        ).then(() => {
          this.applyDraft(d)
        }).catch((action) => {
          // 明确点「丢弃」才删除；按 ESC / 点 X 保留草稿不动
          if (action === 'cancel') {
            deleteDraft(d.id, () => { this.refreshDrafts() }, () => {})
          }
        })
      }, () => {})
    },
    loadDraftItem(d) {
      this.draftsOpen = false
      if (d.type === 'UPDATE') {
        if (this.isUpdate && this.targetPath === d.targetPath) {
          getDraft(d.id, (full) => this.applyDraft(full), (m) => ElMessage.error(m || '草稿加载失败'))
        } else {
          this.$router.push(`/edit/${d.targetPath}`) // 进入编辑页后走「发现草稿」恢复流程
        }
      } else if (this.isUpdate) {
        this.$router.push({ path: '/edit', query: { draft: String(d.id) } })
      } else {
        getDraft(d.id, (full) => this.applyDraft(full), (m) => ElMessage.error(m || '草稿加载失败'))
      }
    },
    async removeDraftItem(d) {
      try {
        await ElMessageBox.confirm(`确定删除草稿「${d.title || '未命名草稿'}」？`, '提示', { type: 'warning' })
      } catch { return }
      deleteDraft(d.id, () => {
        ElMessage.success('已删除')
        if (this.draftId === d.id) { this.draftId = null; this.draftSavedAt = '' }
        this.refreshDrafts()
      }, (m) => ElMessage.error(m || '删除失败'))
    },

    submit() {
      if (this.uploadingImage) return ElMessage.warning('请等待图片上传完成')
      const title = this.form.title.trim()
      if (!title) return ElMessage.error('请填写标题')
      if (ILLEGAL_TITLE.test(title)) return ElMessage.error('标题不能包含 / \\ # ? % 或 .. 等字符')
      if (!this.form.content.trim()) return ElMessage.error('正文不能为空')
      this.submitting = true
      const payload = {
        type: this.isUpdate ? 'UPDATE' : 'CREATE',
        path: this.isUpdate ? this.targetPath : undefined,
        categorySlug: this.form.categorySlug || null,
        title,
        // 始终发字符串：空串在更新时表示“清空图标”（后端非 null 即覆盖）
        icon: this.form.icon.trim(),
        description: this.form.description.trim() || null,
        content: this.form.content,
        baseVersion: this.isUpdate ? this.baseVersion : undefined,
      }
      submitRevision(
        payload,
        () => {
          this.submitting = false
          // 投稿成功：清掉对应草稿，并对齐快照防止离开页面时又补存一份
          clearTimeout(this.autoSaveTimer)
          if (this.draftId) deleteDraft(this.draftId, () => {}, () => {})
          this.lastSavedSnapshot = this.snapshot()
          ElMessage.success('已提交，等待管理员审核')
          this.$router.push('/profile')
        },
        (msg) => {
          this.submitting = false
          ElMessage.error(msg || '提交失败')
        }
      )
    },
  },
}
</script>

<style scoped>
.edit-page {
  width: 100%;
  max-width: 1600px;
  margin: 0 auto;
  padding: 28px 24px 64px;
}
.edit-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--border);
}
.edit-title {
  font-size: 24px;
  font-weight: 700;
  line-height: var(--lh-tight);
  letter-spacing: 0;
  color: var(--text-primary);
  margin: 0;
}
.edit-sub { color: var(--text-secondary); font-size: 14px; margin: 6px 0 0; }
.edit-actions { display: flex; align-items: center; gap: 10px; flex-shrink: 0; flex-wrap: wrap; }
.draft-saved-hint {
  color: var(--text-muted);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

/* 草稿箱 */
.drafts-loading { padding: 20px; color: var(--text-muted); font-size: 14px; }
.draft-list { list-style: none; margin: 0; padding: 0; }
.draft-list li {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 6px;
  border-bottom: 1px solid var(--border);
}
.draft-list li:last-child { border-bottom: none; }
/* 类型徽标：颜色只用状态令牌对，亮暗主题由令牌自己切换，不再单独写 html.dark */
.draft-type {
  flex-shrink: 0;
  display: inline-block;
  padding: 0 6px;
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
  border-radius: var(--radius-xs);
}
.draft-type.t-new { background: var(--success-soft); color: var(--success); }
.draft-type.t-upd { background: var(--accent-soft); color: var(--accent); }
.draft-main {
  flex: 1;
  min-width: 0;
  display: block;
  padding: 0;
  border: none;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.draft-main:hover .draft-title { color: var(--accent); }
.draft-title {
  display: block;
  font-weight: 600;
  color: var(--text-primary);
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color var(--dur) ease;
}
.draft-meta {
  display: block;
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.btn-solid, .btn-ghost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
  padding: 0 16px;
  border-radius: var(--radius-sm);
  font-weight: 500;
  font-size: 14px;
  cursor: pointer;
  /* 只过渡颜色类属性，尺寸和阴影变化不做动画 */
  transition: background var(--dur), color var(--dur), border-color var(--dur);
}
.btn-solid { background: var(--accent); color: var(--accent-contrast); border: 1px solid transparent; }
.btn-solid:hover:not(:disabled) { background: var(--accent-hover); }
.btn-ghost { background: var(--bg-surface); color: var(--text-body); border: 1px solid var(--border-strong); }
.btn-ghost:hover:not(:disabled) { border-color: var(--text-muted); color: var(--text-primary); }
.btn-solid:disabled, .btn-ghost:disabled { opacity: 0.55; cursor: not-allowed; }

.meta-card {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-surface);
  margin-bottom: 20px;
  overflow: hidden;
}
.meta-card-head {
  padding: 10px 18px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  background: var(--bg-subtle);
  border-bottom: 1px solid var(--border);
}
.meta-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  padding: 18px;
}
.field { display: flex; flex-direction: column; gap: 6px; }
.field.span2 { grid-column: 1 / -1; }
.field label { font-size: 13px; font-weight: 600; color: var(--text-body); }
.inp {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  color: var(--text-primary);
  font-size: 14px;
  font-family: var(--font-sans);
  transition: border-color var(--dur) ease;
}
/* 输入框的焦点提示就是边框变成强调色；全局的 2px 焦点环在输入框上显得太重 */
.inp:focus { outline: none; border-color: var(--accent); }
.inp:disabled { background: var(--bg-subtle); color: var(--text-muted); }

/* ---- 标题 + 图标选择器 ---- */
.title-row { display: flex; gap: 8px; align-items: stretch; }
.title-row .inp { flex: 1; min-width: 0; }
.icon-picker { position: relative; flex-shrink: 0; }
.icon-btn {
  display: grid;
  place-items: center;
  width: 42px;
  height: 100%;
  min-height: 40px;
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  color: var(--text-muted);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  transition: border-color var(--dur) ease, color var(--dur) ease;
}
.icon-btn.has-icon { border-style: solid; color: var(--accent); }
.icon-btn:hover { border-color: var(--accent); color: var(--text-primary); }
.icon-panel {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 20;
  width: 320px;
  max-width: 78vw;
  padding: 10px;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-md);
}
.icon-grid {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 2px;
  /* 表情较多时在面板内滚动；否则面板高度超出视口且页面无法带着它下滑 */
  max-height: min(264px, 42vh);
  overflow-y: auto;
  overscroll-behavior: contain;
}
.icon-cell {
  display: grid;
  place-items: center;
  padding: 7px 0;
  color: var(--text-secondary);
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  font-size: 18px;
  line-height: 1.3;
  cursor: pointer;
}
.icon-cell:hover { background: var(--bg-hover); color: var(--text-primary); }
.icon-cell.selected { background: var(--accent-soft); color: var(--accent); outline: 2px solid var(--accent); }
.icon-panel-foot { display: flex; gap: 8px; margin-top: 10px; }
.icon-custom { flex: 1; padding: 6px 10px; font-size: 13px; }
.icon-clear {
  padding: 0 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
}
.icon-clear:hover { color: var(--text-primary); border-color: var(--border-strong); }

.editor-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  height: calc(100vh - 320px);
  min-height: 480px;
}
.pane {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: var(--bg-surface);
}
.editor-pane { position: relative; }
.pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  /* 两个窗格的标题栏等高（含 30px 工具按钮），拖拽遮罩从这条线下方开始 */
  min-height: 45px;
  padding: 7px 16px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  border-bottom: 1px solid var(--border);
  background: var(--bg-subtle);
}
.pane-hint { font-weight: 400; color: var(--text-muted); font-variant-numeric: tabular-nums; }
.pane-tools { display: flex; align-items: center; gap: 10px; }
/* 窗格标题栏里的工具按钮：30px 行内高度 */
.image-upload-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 30px;
  padding: 0 10px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  color: var(--text-secondary);
  font: inherit;
  font-weight: 500;
  cursor: pointer;
  transition: border-color var(--dur) ease, color var(--dur) ease;
}
.image-upload-btn:hover:not(:disabled) {
  border-color: var(--text-muted);
  color: var(--text-primary);
}
.image-upload-btn:disabled { cursor: wait; opacity: 0.55; }
.upload-progress {
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}
.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
.drop-overlay {
  position: absolute;
  inset: 45px 0 0;
  z-index: 3;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 2px dashed var(--accent);
  background: var(--bg-surface);
  color: var(--text-primary);
  pointer-events: none;
}
/* 支持 color-mix() 时让编辑区微微透出；不支持时用上面的不透明底色，不会变成全透明 */
@supports (color: color-mix(in srgb, red 50%, transparent)) {
  .drop-overlay { background: color-mix(in srgb, var(--bg-surface) 92%, transparent); }
}
.drop-overlay span { color: var(--text-secondary); font-size: 12px; }
.md-input {
  flex: 1;
  border: none;
  resize: none;
  padding: 18px;
  font-family: var(--font-mono);
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-body);
  background: var(--bg-surface);
}
/* 正文框没有自己的边框，焦点提示改由整个编辑窗格的边框承担，避免焦点完全不可见 */
.md-input:focus { outline: none; }
.editor-pane:focus-within { border-color: var(--accent); }
.md-preview { flex: 1; padding: 20px 24px; overflow-y: auto; }
.preview-empty { color: var(--text-muted); font-size: 14px; }

@media (max-width: 1024px) {
  .meta-grid { grid-template-columns: 1fr; }
  .editor-grid { grid-template-columns: 1fr; height: auto; }
  .pane { min-height: 360px; }
}

@media (max-width: 640px) {
  .edit-page { padding: 20px 14px 48px; }
  .meta-grid { gap: 13px; padding: 14px; }
  .field.span2 { grid-column: auto; }
  .editor-grid { gap: 12px; }
  .pane { min-height: 320px; }
  .md-input { padding: 14px; }
  .md-preview { padding: 16px 14px; }
  .pane-head { gap: 8px; }
  .pane-tools { gap: 6px; }
  .upload-progress, .pane-hint { display: none; }
  .image-upload-btn { padding: 0 8px; }
  /* 头部改纵向堆叠：否则标题被按钮挤成一列竖排、提交按钮溢出屏幕 */
  .edit-head { flex-direction: column; align-items: stretch; }
  .edit-actions { width: 100%; }
  .edit-actions .btn-solid,
  .edit-actions .btn-ghost { flex: 1 1 auto; min-height: 40px; padding: 0 8px; white-space: nowrap; }
  .draft-saved-hint { width: 100%; order: -1; }
  .draft-list li { align-items: flex-start; flex-wrap: wrap; }
}
</style>
