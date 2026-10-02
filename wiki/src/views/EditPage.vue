<template>
  <div class="edit-page" @keydown="onPageKeydown">
    <div class="edit-head">
      <div>
        <button type="button" class="back-link" @click="leaveEditor"><ArrowLeft :size="16" />返回</button>
        <h1 class="edit-title">{{ isUpdate ? '编辑文章' : '写文章' }}</h1>
        <p class="edit-sub">{{ isUpdate ? '完善已有内容，让后来的人更容易找到答案。' : '分享你了解的校园生活、办事方法与经验。' }}</p>
      </div>
      <div class="edit-actions">
        <button type="button" class="btn-ghost" @click="openDrafts">草稿箱{{ drafts.length ? ` (${drafts.length})` : '' }}</button>
        <button type="button" class="btn-ghost" :disabled="draftSaving || loading || !!loadError || uploadingImage" @click="manualSaveDraft">
          {{ draftSaving ? '保存中…' : '保存草稿' }}
        </button>
        <button type="button" class="btn-solid" :disabled="submitting || uploadingImage || loading || !!loadError" @click="reviewSubmission">
          {{ submitting ? '提交中…' : '提交审核' }}
        </button>
      </div>
    </div>

    <el-dialog transition="article-dialog" v-model="draftsOpen" title="草稿箱" width="560px">
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

    <p v-if="loading" class="load-notice" role="status">正在加载文章…</p>
    <div v-else-if="loadError" class="load-notice" role="alert">{{ loadError }} <button type="button" class="btn-ghost" @click="initFromRoute">重试</button></div>
    <template v-else>
      <div class="article-meta">
        <label class="field-label" for="article-title">文章标题</label>
        <div class="title-row">
          <ArticleIconPicker v-model="form.icon" :category="form.categorySlug" />
          <input id="article-title" ref="titleInput" v-model="form.title" class="inp title-input"
            placeholder="例如：图书馆使用指南" :disabled="isUpdate" :aria-describedby="isUpdate ? 'title-help' : undefined" />
        </div>
        <p v-if="isUpdate" id="title-help" class="field-help">标题与文章地址关联，编辑时保留原题。</p>
        <details class="article-settings">
          <summary>文章设置<span>{{ categoryLabel }} · 简介{{ form.description.trim() ? '已填写' : '自动提取' }}</span><ChevronDown :size="16" /></summary>
          <div class="meta-grid">
            <div class="field">
              <label for="article-category">所属分类</label>
              <select id="article-category" v-model="form.categorySlug" class="inp" :disabled="isUpdate">
                <option value="">未分类</option>
                <option v-for="c in cats" :key="c.slug" :value="c.slug">{{ c.label }}</option>
              </select>
              <p class="field-help">{{ isUpdate ? '编辑时保留文章原有分类。' : '选择合适的篇章，方便读者查找。' }}</p>
            </div>
            <div class="field">
              <label for="article-description">简介 <span class="optional">（可选）</span></label>
              <input id="article-description" v-model="form.description" class="inp" maxlength="200" :placeholder="descPlaceholder" />
              <p class="field-help">留空时从正文提取，用于列表和搜索结果。</p>
            </div>
          </div>
        </details>
      </div>
      <ArticleEditor ref="articleEditor" v-model="form.content" :title="form.title" :description="form.description"
        :base-path="baseDir" :uploading="uploadingImage" :upload-progress="uploadProgress"
        :save-status="saveStatus" :save-error="draftSaveError" @image="startImageUpload" @paste="handlePaste"
        @drop="handleDrop" @resize-image="onImageResize" @save="manualSaveDraft" />
      <p class="submission-note">提交后由管理员审核，通过后会公开发布。未完成的文章可以先存为草稿。</p>
    </template>
    <el-dialog transition="article-dialog" v-model="reviewOpen" title="提交前确认" width="760px" :close-on-click-modal="!submitting" :close-on-press-escape="!submitting" :show-close="!submitting">
      <p class="review-note">请检查标题、正文和图片。提交后可在个人中心查看审核结果。</p>
      <div class="review-content">
        <p class="review-category">{{ categoryLabel }}</p>
        <h2>{{ form.title }}</h2>
        <p v-if="form.description.trim()" class="review-description">{{ form.description }}</p>
        <MarkdownRenderer :content="form.content" :base-path="baseDir" embedded />
      </div>
      <template #footer>
        <button type="button" class="btn-ghost" :disabled="submitting" @click="reviewOpen = false">继续编辑</button>
        <button type="button" class="btn-solid review-submit" :disabled="submitting || uploadingImage" @click="submit">{{ submitting ? '提交中…' : '确认提交审核' }}</button>
      </template>
    </el-dialog>
  </div>
</template>
<script>
import { markRaw, nextTick } from 'vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import ArticleIconPicker from '@/components/ArticleIconPicker.vue'
import ArticleEditor from '@/components/ArticleEditor.vue'
import { ArrowLeft, ChevronDown } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { ElMessageBox } from 'element-plus'
import { categories, fetchPageContent, loadManifest, state as wikiState } from '@/wiki'
import { submitRevision, uploadImage,
  saveDraft, listDrafts, getDraft, getDraftByPath, deleteDraft } from '@/net/index.js'

const ALLOWED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp'])
const MAX_IMAGE_SIZE = 10 * 1024 * 1024
// 标题拼进页面路径，这些字符会破坏路由（与后端 TitleUtil 一致）
const ILLEGAL_TITLE = /[/\\#?%]|\.\./

export default {
  name: 'EditPage',
  components: { MarkdownRenderer: markRaw(MarkdownRenderer), ArticleIconPicker, ArticleEditor, ArrowLeft, ChevronDown },
  props: { targetPath: { type: String, default: '' } },
  data() {
    return {
      submitting: false,
      uploadingImage: false,
      uploadProgress: 0,
      loading: true,
      loadError: '',
      initVersion: 0,
      disposed: false,
      reviewOpen: false,
      submitted: false,
      form: { categorySlug: '', title: '', icon: '', description: '', content: '' },
      baseVersion: null,
      draftSaveError: false,
      savePromise: null,
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
    categoryLabel() { return this.cats.find(c => c.slug === this.form.categorySlug)?.label || this.form.categorySlug || '未分类' },
    dirty() { return this.hasDraftWorthSaving() && this.snapshot() !== this.lastSavedSnapshot },
    saveStatus() {
      if (this.draftSaving) return '正在保存草稿…'
      if (this.draftSaveError) return '草稿保存失败，请重试保存'
      if (this.dirty) return '有未保存的修改 · 停止输入后自动保存'
      if (this.draftSavedAt) return `草稿已保存 ${this.draftSavedAt.slice(11, 19) || this.draftSavedAt}`
      return this.isUpdate ? '尚未修改原文' : '开始写作后自动保存草稿'
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
  },
  async mounted() {
    window.addEventListener('beforeunload', this.onBeforeUnload)
    if (!wikiState.loaded) await loadManifest()
    if (this.disposed) return
    await this.initFromRoute()
    this.refreshDrafts()
  },
  beforeUnmount() {
    this.disposed = true
    this.initVersion += 1
    window.removeEventListener('beforeunload', this.onBeforeUnload)
    clearTimeout(this.autoSaveTimer)
  },
  async beforeRouteLeave() { return this.saveBeforeLeaving() },
  async beforeRouteUpdate() { return this.saveBeforeLeaving() },
  methods: {
    onBeforeUnload(event) {
      if (this.dirty || this.uploadingImage || this.submitting) { event.preventDefault(); event.returnValue = '' }
    },
    onPageKeydown(event) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
        event.preventDefault(); this.manualSaveDraft()
      }
    },
    leaveEditor() {
      if (window.history.state?.back) this.$router.back()
      else this.$router.push(this.isUpdate ? `/docs/${this.targetPath}` : '/')
    },
    async saveBeforeLeaving() {
      if (this.submitted) return true
      if (this.submitting) { ElMessage.warning('正在提交，请稍后再离开'); return false }
      if (this.uploadingImage) { ElMessage.warning('图片正在上传，请稍后再离开'); return false }
      while (this.savePromise) await this.savePromise
      if (this.loading || this.loadError) return true
      // 保存期间仍可输入，必须把最新一次修改也保存后才离开。
      while (this.dirty) {
        if (!await this.doSaveDraft(true)) break
      }
      if (!this.dirty) return true
      try {
        await ElMessageBox.confirm('草稿未能保存。离开会丢失尚未保存的修改。', '离开文章编辑？', {
          confirmButtonText: '放弃修改并离开', cancelButtonText: '继续编辑', type: 'warning',
        })
        return true
      } catch { return false }
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
    insertUploadPlaceholder() {
      const textarea = this.$refs.articleEditor?.$refs.input
      const start = textarea?.selectionStart ?? this.form.content.length
      const end = textarea?.selectionEnd ?? start
      const id = `${Date.now()}-${Math.random().toString(36).slice(2)}`
      const placeholder = `<!-- image-upload:${id} -->`
      const prefix = start > 0 && this.form.content[start - 1] !== '\n' ? '\n' : ''
      const suffix = end < this.form.content.length && this.form.content[end] !== '\n' ? '\n' : ''
      const insertion = `${prefix}${placeholder}${suffix}`
      this.form.content = this.form.content.slice(0, start) + insertion + this.form.content.slice(end)
      return { placeholder }
    },
    replaceUploadPlaceholder(placeholder, replacement) {
      this.$refs.articleEditor?.removeUploadPlaceholderFromHistory(placeholder)
      const index = this.form.content.indexOf(placeholder)
      if (index >= 0) {
        this.form.content = this.form.content.replace(placeholder, replacement)
        return index + replacement.length
      }
      // 上传中撤销或删除了占位符，就尊重用户的删除操作。
      return null
    },
    removeUploadPlaceholder(placeholder) {
      this.$refs.articleEditor?.removeUploadPlaceholderFromHistory(placeholder)
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

      const { placeholder } = this.insertUploadPlaceholder()
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
          const nextCursor = this.replaceUploadPlaceholder(placeholder, markdown)
          this.uploadingImage = false
          this.uploadProgress = 100
          if (nextCursor === null) { ElMessage.info('已取消插入图片'); this.scheduleAutoSave(); return }
          await nextTick()
          const textarea = this.$refs.articleEditor?.$refs.input
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
      this.draftSaveError = false
      this.reviewOpen = false
      this.submitted = false
    },
    async initFromRoute() {
      const version = ++this.initVersion
      this.loading = true
      this.loadError = ''
      this.resetFormState()
      if (this.isUpdate) {
        try {
          const d = await fetchPageContent(this.targetPath)
          if (version !== this.initVersion) return
          this.form = { categorySlug: d.categorySlug || '', title: d.title || '', icon: d.icon || '', description: d.description || '', content: d.content || '' }
          this.baseVersion = d.version ?? 0
        } catch {
          if (version === this.initVersion) { this.loadError = '无法加载原文，请重试。'; this.loading = false }
          return
        }
      } else if (this.$route.query.draft) {
        await new Promise(resolve => getDraft(this.$route.query.draft, d => {
          if (version === this.initVersion) this.applyDraft(d)
          resolve()
        }, () => { if (version === this.initVersion) this.loadError = '无法加载草稿，请重试。'; resolve() }))
      }
      if (version !== this.initVersion) return
      this.lastSavedSnapshot = this.snapshot()
      if (this.isUpdate) await this.checkDraftForPath()
      if (version !== this.initVersion) return
      await nextTick()
      this.loading = false
      await nextTick()
      this.$refs.articleEditor?.resetHistory()
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
      return !!(this.draftId || this.form.title.trim() || this.form.content.trim()
        || this.form.description.trim())
    },
    scheduleAutoSave() {
      clearTimeout(this.autoSaveTimer)
      if (this.submitting || this.loading || this.loadError || this.uploadingImage) return
      this.autoSaveTimer = setTimeout(() => {
        if (this.submitting || this.loading || this.loadError || this.uploadingImage) return
        if (!this.hasDraftWorthSaving()) return
        if (this.snapshot() === this.lastSavedSnapshot) return
        this.doSaveDraft(true)
      }, 3000)
    },
    async doSaveDraft(silent = true) {
      if (this.loading || this.loadError || this.uploadingImage || this.submitted) return false
      // 先完成在途保存，再保存最新内容，避免首次自动保存生成两份草稿。
      while (this.savePromise) await this.savePromise
      if (this.loading || this.loadError || this.uploadingImage || this.submitted || this.submitting) return false
      if (!this.hasDraftWorthSaving()) {
        if (!silent) ElMessage.warning('写下标题或正文后再保存草稿')
        return false
      }
      if (this.snapshot() === this.lastSavedSnapshot) return true
      clearTimeout(this.autoSaveTimer)
      const snap = this.snapshot()
      this.draftSaving = true
      this.draftSaveError = false
      this.savePromise = new Promise(resolve => {
        const failed = (message) => {
          this.draftSaveError = true
          if (!silent) ElMessage.error(typeof message === 'string' ? message : '草稿保存失败，请重试')
          resolve(false)
        }
        saveDraft(this.draftPayload(), d => {
          this.draftId = d.id
          this.draftSavedAt = d.savedAt || ''
          this.lastSavedSnapshot = snap
          if (!silent) ElMessage.success('草稿已保存')
          resolve(true)
        }, failed, failed)
      })
      const saved = await this.savePromise
      this.savePromise = null
      this.draftSaving = false
      if (saved && !silent) this.refreshDrafts()
      return saved
    },
    manualSaveDraft() {
      if (this.uploadingImage) return ElMessage.warning('图片上传完成后会自动保存')
      if (this.submitting) return
      return this.doSaveDraft(false)
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
      if (!this.isUpdate) this.form.title = d.title || ''
      this.form.icon = d.icon || ''
      this.form.description = d.description || ''
      this.form.content = d.content || ''
      this.draftId = d.id
      this.draftSavedAt = d.updatedAt || ''
      // 恢复编辑草稿时保留它基于的版本，交给后端检测期间的其他修改。
      if (this.isUpdate && d.baseVersion != null) this.baseVersion = d.baseVersion
      // 灌入草稿本身不算“新改动”，避免马上又自动保存一遍
      this.lastSavedSnapshot = this.snapshot()
      this.draftSaveError = false
      this.$nextTick(() => this.$refs.articleEditor?.resetHistory())
    },
    checkDraftForPath() {
      const version = this.initVersion
      // 恢复草稿前暂不开放正文，避免慢请求覆盖刚输入的文字。
      return new Promise(resolve => getDraftByPath(this.targetPath, async d => {
        if (!d || version !== this.initVersion) { resolve(); return }
        try {
          await ElMessageBox.confirm(
            `检测到你在 ${d.updatedAt} 保存过这篇文章的草稿，是否恢复？`, '发现草稿',
            { confirmButtonText: '恢复草稿', cancelButtonText: '丢弃草稿', distinguishCancelAndClose: true, type: 'info' }
          )
          if (version === this.initVersion) this.applyDraft(d)
        } catch (action) {
          // 明确点「丢弃」才删除；按 ESC / 点 X 保留草稿不动。
          if (action === 'cancel' && version === this.initVersion) {
            deleteDraft(d.id, () => { this.refreshDrafts() }, () => {})
          }
        } finally { resolve() }
      }, () => resolve()))
    },
    async loadDraftItem(d) {
      if (!await this.saveBeforeLeaving()) return
      this.draftsOpen = false
      if (d.type === 'UPDATE' && (!this.isUpdate || this.targetPath !== d.targetPath)) {
        this.$router.push(`/edit/${d.targetPath}`)
      } else if (d.type !== 'UPDATE' && this.isUpdate) {
        this.$router.push({ path: '/edit', query: { draft: String(d.id) } })
      } else {
        const version = ++this.initVersion
        this.loading = true
        clearTimeout(this.autoSaveTimer)
        getDraft(d.id, full => {
          if (version !== this.initVersion) return
          this.applyDraft(full)
          this.loading = false
        }, message => {
          if (version !== this.initVersion) return
          this.loading = false
          ElMessage.error(message || '草稿加载失败')
        })
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

    reviewSubmission() {
      if (!this.validateSubmission()) return
      this.reviewOpen = true
    },
    validateSubmission() {
      if (this.loading || this.loadError || this.submitting) return false
      if (this.uploadingImage) { ElMessage.warning('请等待图片上传完成'); return false }
      const title = this.form.title.trim()
      if (!title || ILLEGAL_TITLE.test(title)) {
        ElMessage.error(!title ? '请填写文章标题' : '标题不能包含 / \\ # ? % 或 .. 等字符')
        this.$refs.titleInput?.focus()
        return false
      }
      if (!this.form.content.trim()) {
        ElMessage.error('请先写下正文')
        this.$refs.articleEditor?.$refs.input?.focus()
        return false
      }
      return true
    },
    async submit() {
      if (!this.validateSubmission()) return
      const title = this.form.title.trim()
      this.submitting = true
      clearTimeout(this.autoSaveTimer)
      while (this.savePromise) await this.savePromise
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
          this.submitted = true
          this.reviewOpen = false
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
          this.scheduleAutoSave()
        },
        () => { this.submitting = false; ElMessage.error('网络异常，提交未完成，请重试'); this.scheduleAutoSave() }
      )
    },
  },
}
</script>

<style scoped>
.edit-page { width: 100%; max-width: 1240px; margin: 0 auto; padding: 24px 32px 56px; }
.edit-head { display: flex; align-items: center; justify-content: space-between; gap: 24px; margin-bottom: 28px; }
.back-link { display: inline-flex; align-items: center; gap: 6px; padding: 0; margin-bottom: 12px; color: var(--text-secondary); background: transparent; border: 0; font: inherit; font-size: 13px; cursor: pointer; }
.edit-title { margin: 0; color: var(--text-primary); font-size: 26px; line-height: var(--lh-tight); }
.edit-sub { margin: 8px 0 0; color: var(--text-secondary); font-size: 14px; }
.edit-actions { display: flex; flex-wrap: wrap; gap: 8px; flex-shrink: 0; }
.btn-solid, .btn-ghost { display: inline-flex; align-items: center; justify-content: center; min-height: 38px; padding: 8px 14px; border: 1px solid var(--border-strong); border-radius: var(--radius-sm); background: var(--bg-surface); color: var(--text-body); font: inherit; font-size: 14px; font-weight: 500; cursor: pointer; transition: background var(--dur), color var(--dur); }
.btn-solid { background: var(--accent); color: var(--accent-contrast); border-color: var(--accent); }
.btn-solid:hover:not(:disabled) { background: var(--accent-hover); }
.btn-ghost:hover:not(:disabled) { background: var(--bg-hover); }
button:focus-visible, summary:focus-visible, .inp:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
.btn-solid:disabled, .btn-ghost:disabled { opacity: .55; cursor: not-allowed; }
.article-meta { margin-bottom: 20px; }
.field-label, .field label { display: block; font-size: 14px; font-weight: 600; color: var(--text-body); margin-bottom: 8px; }
.title-row { display: flex; gap: 10px; }
.inp { width: 100%; padding: 10px 12px; border: 1px solid var(--border-strong); border-radius: var(--radius-sm); background: var(--bg-surface); color: var(--text-primary); font-family: var(--font-sans); font-size: 14px; }
.title-input { flex: 1; min-width: 0; font-size: 20px; padding: 10px 14px; }
.inp:disabled { color: var(--text-secondary); background: var(--bg-subtle); }
.inp::placeholder { color: var(--text-muted); }
.field-help { margin: 6px 0 0; font-size: 12px; color: var(--text-muted); }
.article-settings { margin-top: 18px; border: 1px solid var(--border); border-radius: var(--radius); }
.article-settings summary { display: flex; align-items: center; gap: 12px; padding: 12px 14px; color: var(--text-body); font-size: 14px; cursor: pointer; list-style: none; }
.article-settings summary::-webkit-details-marker { display: none; }
.article-settings summary span { flex: 1; color: var(--text-muted); font-size: 12px; }
.article-settings[open] summary { border-bottom: 1px solid var(--border); }
.article-settings[open] summary svg { transform: rotate(180deg); }
.meta-grid { display: grid; grid-template-columns: 1fr 2fr; gap: 20px; padding: 16px; }
.field { min-width: 0; }
.optional { font-weight: 400; color: var(--text-muted); }
.submission-note { font-size: 13px; color: var(--text-muted); margin: 14px 0 0; }
.load-notice { padding: 24px 0; color: var(--text-secondary); }
.review-note { margin: 0 0 16px; color: var(--text-secondary); font-size: 14px; }
.review-content { border-top: 1px solid var(--border); max-height: 56vh; overflow-y: auto; overflow-wrap: anywhere; padding: 16px 4px; }
.review-category { color: var(--text-muted); font-size: 13px; margin: 0; }
.review-content h2 { color: var(--text-primary); font-size: 24px; margin: 8px 0 16px; }
.review-description { color: var(--text-secondary); font-size: 14px; }
.review-submit { margin-left: 8px; }
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


@media (max-width: 760px) {
  .edit-page { padding: 18px 14px 40px; }
  .edit-head { flex-direction: column; align-items: stretch; gap: 18px; margin-bottom: 22px; }
  .edit-actions { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .edit-actions button { padding: 8px 6px; min-height: 42px; }
  .edit-title { font-size: 24px; }
  .edit-sub { font-size: 13px; }
  .title-input { font-size: 18px; }
  .meta-grid { grid-template-columns: 1fr; gap: 16px; padding: 14px; }
  .article-settings summary { gap: 8px; }
  .draft-list li { align-items: flex-start; flex-wrap: wrap; }
}
</style>
