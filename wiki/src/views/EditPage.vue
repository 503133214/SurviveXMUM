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
        <button class="btn-ghost" @click="$router.back()">取消</button>
        <button class="btn-solid" :disabled="submitting || uploadingImage" @click="submit">
          {{ uploadingImage ? '等待图片上传…' : submitting ? '提交中…' : '提交审核' }}
        </button>
      </div>
    </div>

    <div class="meta-card">
      <div class="meta-card-head">文档信息</div>
      <div class="meta-grid">
      <div class="field" v-if="!isUpdate">
        <label>分类</label>
        <select v-model="form.categorySlug" class="inp">
          <option value="">（顶级 / 无分类）</option>
          <option v-for="c in cats" :key="c.slug" :value="c.slug">{{ c.icon }} {{ c.label }}</option>
        </select>
      </div>
      <div class="field" v-else>
        <label>分类</label>
        <input class="inp" :value="form.categorySlug || '（顶级）'" disabled />
      </div>

      <div class="field">
        <label>标题</label>
        <input v-model="form.title" class="inp" placeholder="例如：图书馆使用指南" :disabled="isUpdate" />
      </div>

      <div class="field">
        <label>图标（可选）</label>
        <input v-model="form.icon" class="inp" placeholder="单个 emoji，如 📖" maxlength="4" />
      </div>

      <div class="field">
        <label>标签（逗号分隔，可选）</label>
        <input v-model="tagsText" class="inp" placeholder="校园, 设施" />
      </div>

      <div class="field span2">
        <label>简介（可选，留空将自动从正文提取）</label>
        <input v-model="form.description" class="inp" placeholder="一句话描述这篇文档" />
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
import { ElMessage } from 'element-plus'
import { Picture } from '@element-plus/icons-vue'
import { categories, fetchPageContent, loadManifest, state as wikiState } from '@/wiki'
import { submitRevision, uploadImage } from '@/net/index.js'

const ALLOWED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp'])
const MAX_IMAGE_SIZE = 10 * 1024 * 1024

export default {
  name: 'EditPage',
  components: { MarkdownRenderer: markRaw(MarkdownRenderer), Picture },
  props: { targetPath: { type: String, default: '' } },
  data() {
    return {
      submitting: false,
      uploadingImage: false,
      uploadProgress: 0,
      isDragging: false,
      form: { categorySlug: '', title: '', icon: '', description: '', content: '' },
      baseVersion: null,
      tagsText: '',
    }
  },
  computed: {
    isUpdate() {
      return !!this.targetPath
    },
    cats() {
      return categories()
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
    if (this.isUpdate) {
      try {
        const d = await fetchPageContent(this.targetPath)
        this.form.categorySlug = d.categorySlug || ''
        this.form.title = d.title || ''
        this.form.icon = d.icon || ''
        this.form.description = d.description || ''
        this.form.content = d.content || ''
        this.baseVersion = d.version ?? 0
        this.tagsText = (d.tags || []).join(', ')
      } catch (e) {
        ElMessage.error('无法加载原文内容')
      }
    }
  },
  methods: {
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
      // 预览里第 index 张图片被拖动 → 把对应的 Markdown 图片宽度改写为 =<width>x
      let i = -1
      this.form.content = this.form.content.replace(/!\[[^\]]*\]\([^)]*\)/g, (match) => {
        i += 1
        if (i !== index) return match
        const parsed = match.match(/^!\[([^\]]*)\]\((.*)\)$/)
        if (!parsed) return match
        const alt = parsed[1]
        const url = parsed[2].replace(/\s+=\d+x\d*\s*$/, '').trim()
        return `![${alt}](${url} =${width}x)`
      })
    },
    submit() {
      if (this.uploadingImage) return ElMessage.warning('请等待图片上传完成')
      if (!this.form.title.trim()) return ElMessage.error('请填写标题')
      if (!this.form.content.trim()) return ElMessage.error('正文不能为空')
      this.submitting = true
      const tags = this.tagsText.split(/[,，]/).map((t) => t.trim()).filter(Boolean)
      const payload = {
        type: this.isUpdate ? 'UPDATE' : 'CREATE',
        path: this.isUpdate ? this.targetPath : undefined,
        categorySlug: this.form.categorySlug || null,
        title: this.form.title.trim(),
        icon: this.form.icon || null,
        description: this.form.description || null,
        tags,
        content: this.form.content,
        baseVersion: this.isUpdate ? this.baseVersion : undefined,
      }
      submitRevision(
        payload,
        () => {
          this.submitting = false
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
  font-size: clamp(1.5rem, 3vw, 2rem);
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--text-primary);
  margin: 0;
}
.edit-sub { color: var(--text-secondary); font-size: 14px; margin: 6px 0 0; }
.edit-actions { display: flex; gap: 10px; flex-shrink: 0; }

.btn-solid, .btn-ghost {
  padding: 10px 20px;
  border-radius: var(--radius-sm);
  font-weight: 700;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.btn-solid { background: var(--accent); color: var(--accent-contrast); border: none; }
.btn-solid:hover:not(:disabled) { opacity: 0.88; }
.btn-solid:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-ghost { background: transparent; color: var(--text-secondary); border: 1px solid var(--border); }
.btn-ghost:hover { background: var(--bg-hover); color: var(--text-primary); }

.meta-card {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-surface);
  margin-bottom: 20px;
  overflow: hidden;
}
.meta-card-head {
  padding: 10px 18px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--text-muted);
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
  transition: border-color 0.2s ease;
}
.inp:focus { outline: none; border-color: var(--accent); }
.inp:disabled { background: var(--bg-subtle); color: var(--text-muted); }

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
  padding: 9px 16px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--text-muted);
  border-bottom: 1px solid var(--border);
  background: var(--bg-subtle);
}
.pane-hint { text-transform: none; letter-spacing: 0; font-weight: 500; color: var(--text-muted); }
.pane-tools { display: flex; align-items: center; gap: 10px; }
.image-upload-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 8px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--bg-surface);
  color: var(--text-secondary);
  font: inherit;
  font-weight: 650;
  letter-spacing: 0;
  text-transform: none;
  cursor: pointer;
}
.image-upload-btn:hover:not(:disabled) {
  border-color: var(--border-strong);
  color: var(--text-primary);
}
.image-upload-btn:disabled { cursor: wait; opacity: .6; }
.upload-progress {
  color: var(--text-secondary);
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0;
  text-transform: none;
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
  inset: 42px 0 0;
  z-index: 3;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 2px dashed var(--accent);
  background: color-mix(in srgb, var(--bg-surface) 92%, transparent);
  color: var(--text-primary);
  pointer-events: none;
}
.drop-overlay span { color: var(--text-secondary); font-size: 12px; }
.md-input {
  flex: 1;
  border: none;
  resize: none;
  padding: 18px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 14px;
  line-height: 1.7;
  color: var(--text-body);
  background: var(--bg-surface);
}
.md-input:focus { outline: none; }
.md-preview { flex: 1; padding: 20px 24px; overflow-y: auto; }
.preview-empty { color: var(--text-muted); font-size: 14px; }

@media (max-width: 1024px) {
  .meta-grid { grid-template-columns: 1fr; }
  .editor-grid { grid-template-columns: 1fr; height: auto; }
  .pane { min-height: 360px; }
}

@media (max-width: 640px) {
  .pane-head { align-items: flex-start; gap: 8px; }
  .pane-tools { gap: 6px; }
  .upload-progress, .pane-hint { display: none; }
  .image-upload-btn { padding: 4px 7px; }
}
</style>
