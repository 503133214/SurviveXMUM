<template>
  <div class="acp">
    <header class="acp-head"><h1>评论管理</h1></header>

    <div class="acp-filter">
      <el-select v-model="status" placeholder="全部状态" clearable style="width: 150px" @change="search" @clear="search">
        <el-option v-for="(label, key) in statusLabels" :key="key" :label="label" :value="key" />
      </el-select>
      <el-input v-model="keyword" placeholder="搜索评论内容 / 页面路径" clearable class="acp-kw"
                @keyup.enter="search" @clear="search" />
      <el-button @click="search">查询</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" style="width: 100%">
      <el-table-column prop="createdAt" label="时间" width="170" />
      <el-table-column label="作者" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <div class="acp-author">
            <span>{{ row.displayName }}</span>
            <small v-if="row.userEmail">{{ row.userEmail }}</small>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="页面" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          <router-link class="acp-link" :to="`/docs/${row.path}`">{{ row.pageTitle || row.path }}</router-link>
        </template>
      </el-table-column>
      <el-table-column label="内容" min-width="280">
        <template #default="{ row }">
          <p class="acp-content">{{ row.content }}</p>
          <p v-if="row.hiddenReason" class="acp-reason">隐藏理由：{{ row.hiddenReason }}</p>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="statusTag(row.status)">{{ statusLabels[row.status] || row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'VISIBLE'" link type="warning" @click="hide(row)">隐藏</el-button>
          <el-button v-else-if="row.status === 'HIDDEN'" link type="success" @click="show(row)">恢复</el-button>
          <el-button v-if="isSuperAdmin" link type="danger" @click="purge(row)">彻底删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="暂无评论" />

    <div class="acp-pager">
      <el-pagination
        layout="prev, pager, next, total"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="(p) => { page = p; load() }"
      />
    </div>
  </div>
</template>

<script>
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminListComments, adminSetCommentStatus, adminPurgeComment } from '@/net/index.js'
import { useUserStore } from '@/store/userStore.js'

const STATUS_LABELS = {
  VISIBLE: '显示中',
  HIDDEN: '已隐藏',
  DELETED: '作者已删',
}

export default {
  name: 'AdminCommentsPanel',
  data() {
    return {
      userStore: useUserStore(),
      status: '', keyword: '',
      rows: [], total: 0, page: 1, size: 20, loading: false,
      statusLabels: STATUS_LABELS,
    }
  },
  computed: {
    isSuperAdmin() { return this.userStore.isSuperAdmin },
  },
  mounted() { this.load() },
  methods: {
    statusTag(s) {
      if (s === 'HIDDEN') return 'warning'
      if (s === 'DELETED') return 'info'
      return 'success'
    },
    search() { this.page = 1; this.load() },
    load() {
      this.loading = true
      adminListComments(
        { status: this.status, keyword: this.keyword, page: this.page, size: this.size },
        (d) => {
          this.rows = (d && d.list) || []
          this.total = Number(d && d.total) || 0
          this.loading = false
        },
        (m) => { this.loading = false; ElMessage.error(m || '加载失败') })
    },
    async hide(row) {
      let reason = ''
      try {
        const { value } = await ElMessageBox.prompt('隐藏理由（会通知作者，可留空）', '隐藏这条评论', {
          type: 'warning',
          inputPlaceholder: '如：与本页主题无关',
          inputValidator: (v) => (v || '').length <= 200 || '理由最多 200 字',
        })
        reason = value || ''
      } catch { return }
      adminSetCommentStatus(row.id, { status: 'HIDDEN', reason },
        () => { ElMessage.success('已隐藏'); this.load() },
        (m) => ElMessage.error(m || '操作失败'))
    },
    show(row) {
      adminSetCommentStatus(row.id, { status: 'VISIBLE' },
        () => { ElMessage.success('已恢复显示'); this.load() },
        (m) => ElMessage.error(m || '操作失败'))
    },
    async purge(row) {
      const isRoot = !row.parentId
      try {
        await ElMessageBox.confirm(
          isRoot
            ? '彻底删除这条主楼评论？楼中的回复会一并清除，不可恢复。'
            : '彻底删除这条回复？不可恢复。',
          '危险操作', { type: 'warning', confirmButtonText: '彻底删除' })
      } catch { return }
      adminPurgeComment(row.id,
        () => { ElMessage.success('已彻底删除'); this.load() },
        (m) => ElMessage.error(m || '删除失败'))
    },
  },
}
</script>

<style scoped>
.acp-head h1 { font-size: 1.5rem; font-weight: 800; letter-spacing: -0.02em; margin: 0 0 16px; color: var(--text-primary); }
.acp-filter { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.acp-kw { width: 260px; max-width: 100%; }
.acp-author { display: flex; flex-direction: column; gap: 2px; }
.acp-author small { color: var(--text-muted); font-size: 11.5px; }
.acp-link { color: var(--brand); text-decoration: none; }
.acp-link:hover { text-decoration: underline; }
.acp-content { margin: 0; font-size: 13px; line-height: 1.6; overflow-wrap: anywhere; white-space: pre-wrap; }
.acp-reason { margin: 4px 0 0; color: var(--text-muted); font-size: 11.5px; }
.acp-pager { display: flex; justify-content: flex-end; margin-top: 14px; }
@media (max-width: 768px) {
  .acp-kw { width: 100%; }
}
</style>
