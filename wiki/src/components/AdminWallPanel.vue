<template>
  <div class="awp">
    <header class="awp-head">
      <h1>致谢墙管理</h1>
      <el-button type="primary" @click="openCreate">新增条目</el-button>
    </header>

    <el-table :data="list" v-loading="loading" style="width: 100%">
      <el-table-column label="头像" width="80">
        <template #default="{ row }">
          <el-avatar :size="40" :src="row.avatar || undefined">{{ initial(row.name) }}</el-avatar>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
      <el-table-column label="分组" width="130">
        <template #default="{ row }">
          <el-tag v-if="row.category" size="small">{{ row.category }}</el-tag>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="简介" min-width="180" show-overflow-tooltip />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !list.length" description="暂无条目" />

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑条目' : '新增条目'" width="520px">
      <el-form label-width="72px">
        <el-form-item label="头像">
          <div class="awp-avatar">
            <el-avatar :size="64" :src="form.avatar || undefined">{{ initial(form.name) }}</el-avatar>
            <el-upload :show-file-list="false" :before-upload="onAvatarPick" accept="image/*">
              <el-button :loading="uploading">{{ form.avatar ? '更换头像' : '上传头像' }}</el-button>
            </el-upload>
            <el-button v-if="form.avatar" link type="danger" @click="form.avatar = ''">移除</el-button>
          </div>
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="120" placeholder="姓名 / 机构名称" />
        </el-form-item>
        <el-form-item label="分组">
          <el-input v-model="form.category" maxlength="60" placeholder="如 赞助商 / 特别鸣谢 / 核心贡献者（同组会归到一起）" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="链接">
          <el-input v-model="form.link" maxlength="500" placeholder="可选，点击卡片跳转（如个人主页）" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
          <span class="awp-hint">数字越小越靠前</span>
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
  adminListWall, adminCreateWall, adminUpdateWall, adminDeleteWall, uploadImage,
} from '@/net/index.js'

const empty = () => ({ id: null, name: '', avatar: '', description: '', link: '', category: '', sortOrder: 0 })

export default {
  name: 'AdminWallPanel',
  data() {
    return { list: [], loading: false, dialogVisible: false, editing: false, saving: false, uploading: false, form: empty() }
  },
  mounted() { this.load() },
  methods: {
    initial(name) { return (name || '?').trim().charAt(0).toUpperCase() },
    load() {
      this.loading = true
      adminListWall(
        (d) => { this.list = d || []; this.loading = false },
        (m) => { this.loading = false; ElMessage.error(m || '加载失败') }
      )
    },
    openCreate() { this.editing = false; this.form = empty(); this.dialogVisible = true },
    openEdit(row) { this.editing = true; this.form = { ...row, sortOrder: row.sortOrder || 0 }; this.dialogVisible = true },
    onAvatarPick(file) {
      this.uploading = true
      uploadImage(file, () => {},
        (data) => { this.form.avatar = data.url; this.uploading = false; ElMessage.success('头像已上传') },
        (m) => { this.uploading = false; ElMessage.error(m || '上传失败') })
      return false // 阻止 el-upload 默认上传
    },
    save() {
      if (!this.form.name || !this.form.name.trim()) { ElMessage.warning('请填写名称'); return }
      this.saving = true
      const payload = {
        name: this.form.name, avatar: this.form.avatar, description: this.form.description,
        link: this.form.link, category: this.form.category, sortOrder: this.form.sortOrder,
      }
      const done = () => { this.saving = false; this.dialogVisible = false; ElMessage.success('已保存'); this.load() }
      const fail = (m) => { this.saving = false; ElMessage.error(m || '操作失败') }
      if (this.editing) adminUpdateWall(this.form.id, payload, done, fail)
      else adminCreateWall(payload, done, fail)
    },
    remove(row) {
      ElMessageBox.confirm(`确定删除「${row.name}」？`, '提示', { type: 'warning' }).then(() => {
        adminDeleteWall(row.id,
          () => { ElMessage.success('已删除'); this.load() },
          (m) => ElMessage.error(m || '删除失败'))
      }).catch(() => {})
    },
  },
}
</script>

<style scoped>
.awp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}
.awp-head h1 { font-size: 1.5rem; font-weight: 800; letter-spacing: -0.02em; margin: 0; color: var(--text-primary); }
.awp-avatar { display: flex; align-items: center; gap: 12px; }
.awp-hint { margin-left: 10px; color: var(--text-muted); font-size: 12px; }
.muted { color: var(--text-muted); }
</style>
