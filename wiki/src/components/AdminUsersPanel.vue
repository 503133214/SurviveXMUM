<template>
  <div class="panel">
    <div class="panel-head">
      <h2>用户管理</h2>
      <button class="btn-solid" @click="openCreate">新建用户</button>
    </div>

    <div class="filters">
      <el-input
        v-model="query.keyword"
        placeholder="搜索邮箱 / 昵称"
        clearable
        style="width: 220px"
        @keyup.enter="search"
        @clear="search"
      />
      <el-select v-model="query.role" placeholder="角色" clearable style="width: 120px" @change="search">
        <el-option label="管理员" value="ADMIN" />
        <el-option label="普通用户" value="USER" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="search">
        <el-option label="正常" value="ACTIVE" />
        <el-option label="已封禁" value="BANNED" />
      </el-select>
      <el-checkbox v-model="query.includeDeleted" @change="search">含已删除</el-checkbox>
      <button class="btn-ghost" @click="search">搜索</button>
    </div>

    <el-table v-loading="loading" :data="list" stripe size="large" class="tbl">
      <el-table-column label="邮箱" min-width="200">
        <template #default="{ row }">
          <span :class="{ 'is-deleted': row.deleted }">{{ row.email }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="nickname" label="昵称" min-width="120" />
      <el-table-column label="角色" width="100">
        <template #default="{ row }">
          <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" effect="plain" size="small">
            {{ row.role === 'ADMIN' ? '管理员' : '用户' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.deleted" type="info" size="small">已删除</el-tag>
          <el-tag
            v-else
            :type="row.status === 'BANNED' ? 'warning' : 'success'"
            effect="plain"
            size="small"
          >
            {{ row.status === 'BANNED' ? '已封禁' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="注册时间" width="160">
        <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.deleted" link type="primary" @click="restore(row)">恢复</el-button>
          <template v-else>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button
              v-if="!isSelf(row)"
              link
              :type="row.status === 'BANNED' ? 'success' : 'warning'"
              @click="toggleBan(row)"
            >
              {{ row.status === 'BANNED' ? '解封' : '封禁' }}
            </el-button>
            <el-button v-if="!isSelf(row)" link type="danger" @click="removeUser(row)">删除</el-button>
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
      :title="dialog.mode === 'create' ? '新建用户' : '编辑用户'"
      width="440px"
    >
      <el-form label-position="top" :model="form">
        <el-form-item label="邮箱">
          <el-input
            v-model="form.email"
            :disabled="dialog.mode === 'edit'"
            placeholder="user@example.com"
          />
        </el-form-item>
        <el-form-item :label="dialog.mode === 'create' ? '密码' : '重置密码（留空不改）'">
          <el-input v-model="form.password" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="可选" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="角色">
            <el-select v-model="form.role" style="width: 100%">
              <el-option label="普通用户" value="USER" />
              <el-option label="管理员" value="ADMIN" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="form.status" style="width: 100%">
              <el-option label="正常" value="ACTIVE" />
              <el-option label="已封禁" value="BANNED" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <button class="btn-ghost" @click="dialog.visible = false">取消</button>
        <button class="btn-solid" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/userStore.js'
import {
  adminListUsers, adminCreateUser, adminUpdateUser, adminDeleteUser, adminRestoreUser,
} from '@/net/index.js'

const emptyUser = () => ({
  id: null,
  email: '',
  password: '',
  nickname: '',
  role: 'USER',
  status: 'ACTIVE',
})

export default {
  name: 'AdminUsersPanel',
  data() {
    return {
      query: { keyword: '', role: '', status: '', includeDeleted: false, page: 1, size: 20 },
      list: [],
      total: 0,
      loading: false,
      dialog: { visible: false, mode: 'create' },
      form: emptyUser(),
      saving: false,
    }
  },
  computed: {
    myId() {
      return String(useUserStore().userInfo?.id || '')
    },
  },
  mounted() {
    this.load()
  },
  methods: {
    isSelf(user) {
      return String(user.id) === this.myId
    },
    fmt(iso) {
      if (!iso) return ''
      const date = new Date(iso)
      return Number.isNaN(date.getTime()) ? '' :
        `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
    },
    load() {
      this.loading = true
      adminListUsers(
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
      this.form = emptyUser()
    },
    openEdit(user) {
      this.dialog = { visible: true, mode: 'edit' }
      this.form = {
        id: user.id,
        email: user.email,
        password: '',
        nickname: user.nickname || '',
        role: user.role,
        status: user.status,
      }
    },
    save() {
      this.saving = true
      const done = (message) => {
        this.saving = false
        ElMessage.success(message)
        this.dialog.visible = false
        this.load()
      }
      const fail = (message) => {
        this.saving = false
        ElMessage.error(message || '操作失败')
      }

      if (this.dialog.mode === 'create') {
        adminCreateUser({
          email: this.form.email,
          password: this.form.password,
          nickname: this.form.nickname,
          role: this.form.role,
          status: this.form.status,
        }, () => done('已创建'), fail)
        return
      }

      const payload = {
        nickname: this.form.nickname,
        role: this.form.role,
        status: this.form.status,
      }
      if (this.form.password) payload.password = this.form.password
      adminUpdateUser(this.form.id, payload, () => done('已保存'), fail)
    },
    toggleBan(user) {
      const banning = user.status !== 'BANNED'
      adminUpdateUser(
        user.id,
        { status: banning ? 'BANNED' : 'ACTIVE' },
        () => {
          ElMessage.success(banning ? '已封禁' : '已解封')
          this.load()
        },
        (message) => ElMessage.error(message || '操作失败')
      )
    },
    async removeUser(user) {
      try {
        await ElMessageBox.confirm(
          `确定删除用户「${user.email}」？可在「含已删除」中恢复。`,
          '删除用户',
          { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
        )
      } catch {
        return
      }
      adminDeleteUser(
        user.id,
        () => {
          ElMessage.success('已删除')
          this.load()
        },
        (message) => ElMessage.error(message || '删除失败')
      )
    },
    restore(user) {
      adminRestoreUser(
        user.id,
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
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
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

@media (max-width: 640px) {
  .filters :deep(.el-input), .filters :deep(.el-select) { width: 100% !important; }
  .form-row { grid-template-columns: 1fr; gap: 0; }
}
</style>
