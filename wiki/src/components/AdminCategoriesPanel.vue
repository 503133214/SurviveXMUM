<template>
  <div class="acp">
    <header class="acp-head">
      <h1>分类管理</h1>
      <el-button type="primary" @click="openCreate">新建分类</el-button>
    </header>
    <p class="acp-note">分类标识(slug)已固化进页面路径，创建后不可修改；仅空分类可删除。改动即时生效于侧边栏与首页。</p>

    <el-table :data="list" v-loading="loading" style="width: 100%">
      <el-table-column label="图标" width="70">
        <template #default="{ row }"><span class="acp-icon">{{ row.icon || '📁' }}</span></template>
      </el-table-column>
      <el-table-column prop="label" label="名称" min-width="120" show-overflow-tooltip />
      <el-table-column prop="slug" label="标识 (slug)" min-width="120" show-overflow-tooltip />
      <el-table-column label="页面数" width="90">
        <template #default="{ row }">{{ row.pageCount }}</template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column prop="description" label="简介" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-tooltip :disabled="Number(row.pageCount) === 0" content="分类下还有页面（含回收站），不能删除">
            <span>
              <el-button link type="danger" :disabled="Number(row.pageCount) > 0" @click="removeItem(row)">删除</el-button>
            </span>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !list.length" description="暂无分类" />

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑分类' : '新建分类'" width="480px">
      <el-form label-width="88px">
        <el-form-item label="标识 (slug)" required>
          <el-input v-model="form.slug" :disabled="editing" maxlength="120"
                    placeholder="如 学习篇（将出现在页面路径中，创建后不可改）" />
        </el-form-item>
        <el-form-item label="显示名称">
          <el-input v-model="form.label" maxlength="120" placeholder="留空则与标识相同" />
        </el-form-item>
        <el-form-item label="图标">
          <el-input v-model="form.icon" maxlength="8" placeholder="单个 emoji，如 📚" style="width: 140px" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
          <span class="acp-hint">数字越小越靠前</span>
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminListCategories, adminCreateCategory, adminUpdateCategory, adminDeleteCategory,
} from '@/net/index.js'

const empty = () => ({ id: null, slug: '', label: '', icon: '', description: '', sortOrder: 999 })

export default {
  name: 'AdminCategoriesPanel',
  data() {
    return { list: [], loading: false, dialogVisible: false, editing: false, saving: false, form: empty() }
  },
  mounted() { this.load() },
  methods: {
    load() {
      this.loading = true
      adminListCategories(
        (d) => { this.list = d || []; this.loading = false },
        (m) => { this.loading = false; ElMessage.error(m || '加载失败') })
    },
    openCreate() { this.editing = false; this.form = empty(); this.dialogVisible = true },
    openEdit(row) {
      this.editing = true
      this.form = { id: row.id, slug: row.slug, label: row.label, icon: row.icon,
        description: row.description || '', sortOrder: row.sortOrder ?? 999 }
      this.dialogVisible = true
    },
    save() {
      if (!this.editing && !this.form.slug.trim()) return ElMessage.warning('请填写分类标识')
      this.saving = true
      const payload = {
        slug: this.form.slug, label: this.form.label, icon: this.form.icon,
        description: this.form.description, sortOrder: this.form.sortOrder,
      }
      const done = () => { this.saving = false; this.dialogVisible = false; ElMessage.success('已保存'); this.load() }
      const fail = (m) => { this.saving = false; ElMessage.error(m || '操作失败') }
      if (this.editing) adminUpdateCategory(this.form.id, payload, done, fail)
      else adminCreateCategory(payload, done, fail)
    },
    removeItem(row) {
      ElMessageBox.confirm(`确定删除空分类「${row.label}」？`, '提示', { type: 'warning' }).then(() => {
        adminDeleteCategory(row.id,
          () => { ElMessage.success('已删除'); this.load() },
          (m) => ElMessage.error(m || '删除失败'))
      }).catch(() => {})
    },
  },
}
</script>

<style scoped>
.acp-head {
  display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 8px;
}
.acp-head h1 { font-size: 1.5rem; font-weight: 800; letter-spacing: -0.02em; margin: 0; color: var(--text-primary); }
.acp-note { margin: 0 0 16px; color: var(--text-muted); font-size: 13px; }
.acp-icon { font-size: 20px; }
.acp-hint { margin-left: 10px; color: var(--text-muted); font-size: 12px; }
</style>
