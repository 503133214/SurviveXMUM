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
        <button class="btn-solid" :disabled="submitting" @click="submit">
          {{ submitting ? '提交中…' : '提交审核' }}
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
      <div class="pane">
        <div class="pane-head">
          <span>Markdown</span>
          <span class="pane-hint">{{ charCount }} 字</span>
        </div>
        <textarea
          v-model="form.content"
          class="md-input"
          placeholder="# 标题&#10;&#10;在这里用 Markdown 写正文…"
          spellcheck="false"
        ></textarea>
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
          />
          <p v-else class="preview-empty">预览将在这里实时显示…</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { ElMessage } from 'element-plus'
import { categories, fetchPageContent, loadManifest, state as wikiState } from '@/wiki'
import { submitRevision } from '@/net/index.js'

export default {
  name: 'EditPage',
  components: { MarkdownRenderer: markRaw(MarkdownRenderer) },
  props: { targetPath: { type: String, default: '' } },
  data() {
    return {
      submitting: false,
      form: { categorySlug: '', title: '', icon: '', description: '', content: '' },
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
        this.tagsText = (d.tags || []).join(', ')
      } catch (e) {
        ElMessage.error('无法加载原文内容')
      }
    }
  },
  methods: {
    submit() {
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
</style>
