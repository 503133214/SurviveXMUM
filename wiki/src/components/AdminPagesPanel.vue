<template>
  <div class="panel">
    <div class="panel-head">
      <h2>页面管理</h2>
      <button class="btn-solid" @click="openCreate">新建页面</button>
    </div>

    <div class="filters">
      <el-input
        v-model="query.keyword"
        placeholder="搜索标题 / 路径"
        clearable
        style="width: 240px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select
        v-model="query.category"
        placeholder="分类"
        clearable
        style="width: 160px"
        @change="search"
      >
        <el-option v-for="category in cats" :key="category.slug" :label="category.label" :value="category.slug" />
      </el-select>
      <el-checkbox v-model="query.includeDeleted" @change="search">含已删除</el-checkbox>
      <button class="btn-ghost" @click="search">搜索</button>
    </div>

    <el-table v-loading="loading" :data="list" stripe size="large" class="tbl">
      <el-table-column label="标题" min-width="180">
        <template #default="{ row }">
          <span :class="{ 'is-deleted': row.deleted }">{{ row.title }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="path" label="路径" min-width="200" show-overflow-tooltip />
      <el-table-column prop="categorySlug" label="分类" width="120" />
      <el-table-column label="版本" width="80">
        <template #default="{ row }">v{{ row.version }}</template>
      </el-table-column>
      <el-table-column prop="viewCount" label="浏览" width="80" />
      <el-table-column label="更新时间" width="160">
        <template #default="{ row }">{{ fmt(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.deleted" link type="primary" @click="restore(row)">恢复</el-button>
          <template v-else>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <a class="link-btn" :href="`/docs/${row.path}`" target="_blank" rel="noopener">查看</a>
            <el-button link type="danger" @click="removePage(row)">删除</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        layout="total, prev, pager, next"
        :total="total"
        :current-page="query.page"
        :page-size="query.size"
        @current-change="changePage"
      />
    </div>

    <el-dialog
      v-model="dialog.visible"
      :title="dialog.mode === 'create' ? '新建页面' : `编辑页面 · v${form.version}`"
      width="92%"
      top="4vh"
      class="page-dialog"
    >
      <div v-loading="loadingDetail" class="editor-wrap">
        <div class="meta">
          <div class="f">
            <label>标题</label>
            <el-input
              v-model="form.title"
              :disabled="dialog.mode === 'edit'"
              placeholder="例如：图书馆使用指南"
            />
          </div>
          <div class="f">
            <label>分类</label>
            <el-select
              v-model="form.categorySlug"
              clearable
              placeholder="（顶级 / 无分类）"
              style="width: 100%"
              :disabled="dialog.mode === 'edit'"
            >
              <el-option
                v-for="category in cats"
                :key="category.slug"
                :label="category.label"
                :value="category.slug"
              />
            </el-select>
          </div>
          <div class="f">
            <label>图标</label>
            <el-input v-model="form.icon" placeholder="单个 emoji，如 📖" maxlength="4" />
          </div>
          <div class="f">
            <label>排序</label>
            <el-input v-model.number="form.sortOrder" type="number" />
          </div>
          <div class="f span2">
            <label>简介</label>
            <el-input v-model="form.description" placeholder="一句话描述" />
          </div>
          <div class="f span2">
            <label>标签（逗号分隔）</label>
            <el-input v-model="form.tagsText" placeholder="校园, 设施" />
          </div>
        </div>

        <div class="editor-grid">
          <div class="pane">
            <div class="pane-head">Markdown</div>
            <textarea
              v-model="form.content"
              class="md-input"
              spellcheck="false"
              placeholder="# 标题&#10;&#10;正文…"
            ></textarea>
          </div>
          <div class="pane">
            <div class="pane-head">实时预览</div>
            <div class="md-preview">
              <MarkdownRenderer
                v-if="form.content && form.content.trim()"
                :content="form.content"
                :base-path="baseDir"
                embedded
              />
              <p v-else class="muted">预览将在此显示…</p>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <button class="btn-ghost" @click="dialog.visible = false">取消</button>
        <button class="btn-solid" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : dialog.mode === 'create' ? '创建并发布' : '保存' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { categories, loadManifest, state as wikiState } from '@/wiki'
import {
  adminListPages, adminGetPage, adminCreatePage, adminUpdatePage, adminDeletePage, adminRestorePage,
} from '@/net/index.js'

const emptyPage = () => ({
  id: null,
  title: '',
  categorySlug: '',
  icon: '',
  description: '',
  tagsText: '',
  content: '',
  sortOrder: 999,
  status: 'PUBLISHED',
  version: 0,
})

export default {
  name: 'AdminPagesPanel',
  components: { MarkdownRenderer: markRaw(MarkdownRenderer) },
  data() {
    return {
      query: { keyword: '', category: '', includeDeleted: false, page: 1, size: 20 },
      list: [],
      total: 0,
      loading: false,
      dialog: { visible: false, mode: 'create' },
      form: emptyPage(),
      saving: false,
      loadingDetail: false,
    }
  },
  computed: {
    cats() {
      return categories()
    },
    baseDir() {
      const listedPage = this.list.find((page) => page.id === this.form.id)
      const fallback = this.form.categorySlug ? `${this.form.categorySlug}/x` : ''
      const path = listedPage?.path || fallback
      return path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : this.form.categorySlug || ''
    },
  },
  async mounted() {
    if (!wikiState.loaded) await loadManifest()
    this.load()
  },
  methods: {
    fmt(iso) {
      if (!iso) return ''
      const date = new Date(iso)
      return Number.isNaN(date.getTime()) ? '' :
        `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
    },
    load() {
      this.loading = true
      adminListPages(
        this.query,
        (data) => {
          this.list = data.list || []
          this.total = data.total || 0
          this.loading = false
        },
        (message) => {
          this.loading = false
          ElMessage.error(message || '加载失败')
        }
      )
    },
    search() {
      this.query.page = 1
      this.load()
    },
    changePage(page) {
      this.query.page = page
      this.load()
    },
    openCreate() {
      this.dialog = { visible: true, mode: 'create' }
      this.form = emptyPage()
    },
    openEdit(page) {
      this.dialog = { visible: true, mode: 'edit' }
      this.form = emptyPage()
      this.loadingDetail = true
      adminGetPage(
        page.id,
        (data) => {
          this.form = {
            id: data.id,
            title: data.title || '',
            categorySlug: data.categorySlug || '',
            icon: data.icon || '',
            description: data.description || '',
            tagsText: (data.tags || []).join(', '),
            content: data.content || '',
            sortOrder: data.sortOrder ?? 999,
            status: data.status || 'PUBLISHED',
            version: data.version ?? 0,
          }
          this.loadingDetail = false
        },
        (message) => {
          this.loadingDetail = false
          ElMessage.error(message || '加载详情失败')
        }
      )
    },
    save() {
      if (!this.form.title.trim()) return ElMessage.error('请填写标题')
      if (!this.form.content.trim()) return ElMessage.error('正文不能为空')

      this.saving = true
      const tags = this.form.tagsText.split(/[,，]/).map((tag) => tag.trim()).filter(Boolean)
      const done = (message) => {
        this.saving = false
        ElMessage.success(message)
        this.dialog.visible = false
        this.load()
      }
      const fail = (message) => {
        this.saving = false
        ElMessage.error(message || '保存失败')
      }
      const payload = {
        title: this.form.title.trim(),
        categorySlug: this.form.categorySlug || null,
        icon: this.form.icon || null,
        description: this.form.description || null,
        tags,
        content: this.form.content,
        sortOrder: this.form.sortOrder,
        status: this.form.status,
      }

      if (this.dialog.mode === 'create') {
        adminCreatePage({ ...payload, status: 'PUBLISHED' }, () => done('已创建并发布'), fail)
      } else {
        adminUpdatePage(this.form.id, { ...payload, version: this.form.version }, () => done('已保存'), fail)
      }
    },
    async removePage(page) {
      try {
        await ElMessageBox.confirm(
          `确定删除页面「${page.title}」？可在「含已删除」中恢复。`,
          '删除页面',
          { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
        )
      } catch {
        return
      }
      adminDeletePage(
        page.id,
        () => {
          ElMessage.success('已删除')
          this.load()
        },
        (message) => ElMessage.error(message || '删除失败')
      )
    },
    restore(page) {
      adminRestorePage(
        page.id,
        () => {
          ElMessage.success('已恢复')
          this.load()
        },
        (message) => ElMessage.error(message || '恢复失败')
      )
    },
  },
}
</script>

<style scoped>
.panel-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.panel-head h2 { margin: 0; color: var(--text-primary); font-size: 1.25rem; font-weight: 800; letter-spacing: -.02em; }
.filters { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.tbl { overflow: hidden; border: 1px solid var(--border); border-radius: var(--radius); }
.is-deleted { color: var(--text-muted); text-decoration: line-through; }
.link-btn { margin: 0 8px; color: var(--brand-blue); font-size: 13px; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
.editor-wrap { display: flex; flex-direction: column; gap: 16px; }
.meta { display: grid; grid-template-columns: repeat(2, 1fr); gap: 12px; }
.f { display: flex; flex-direction: column; gap: 5px; }
.f.span2 { grid-column: 1 / -1; }
.f label { color: var(--text-body); font-size: 12.5px; font-weight: 600; }
.editor-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; height: 58vh; }
.pane {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-surface);
}
.pane-head {
  padding: 8px 14px;
  border-bottom: 1px solid var(--border);
  background: var(--bg-subtle);
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: .04em;
  text-transform: uppercase;
}
.md-input {
  flex: 1;
  padding: 16px;
  border: none;
  resize: none;
  background: var(--bg-surface);
  color: var(--text-body);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13.5px;
  line-height: 1.7;
}
.md-input:focus { outline: none; }
.md-preview { flex: 1; padding: 16px 18px; overflow-y: auto; }
.muted { color: var(--text-muted); }
.btn-solid, .btn-ghost {
  padding: 8px 16px;
  border-radius: var(--radius-sm);
  font-size: 13.5px;
  font-weight: 700;
  cursor: pointer;
  transition: all .2s ease;
}
.btn-solid { border: none; background: var(--accent); color: var(--accent-contrast); }
.btn-solid:hover:not(:disabled) { opacity: .88; }
.btn-solid:disabled { cursor: not-allowed; opacity: .6; }
.btn-ghost { margin-right: 8px; border: 1px solid var(--border); background: transparent; color: var(--text-secondary); }
.btn-ghost:hover { background: var(--bg-hover); color: var(--text-primary); }

@media (max-width: 860px) {
  .filters :deep(.el-input), .filters :deep(.el-select) { width: 100% !important; }
  .meta, .editor-grid { grid-template-columns: 1fr; }
  .editor-grid { height: auto; }
  .pane { min-height: 360px; }
}
</style>
