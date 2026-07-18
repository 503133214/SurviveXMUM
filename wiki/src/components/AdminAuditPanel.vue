<template>
  <div class="aap">
    <header class="aap-head"><h1>审计日志</h1></header>

    <div class="aap-filter">
      <el-date-picker
        v-model="dates" type="daterange" unlink-panels value-format="YYYY-MM-DD"
        range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期"
        :disabled-date="disableFutureDate"
        @change="search"
      />
      <el-select v-model="action" placeholder="全部动作" clearable style="width: 160px" @change="search" @clear="search">
        <el-option v-for="(label, key) in actionLabels" :key="key" :label="label" :value="key" />
      </el-select>
      <el-input v-model="keyword" placeholder="搜索操作者邮箱 / 详情" clearable class="aap-kw"
                @keyup.enter="search" @clear="search" />
      <el-button @click="search">查询</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" style="width: 100%">
      <el-table-column prop="createdAt" label="时间" width="170" />
      <el-table-column prop="actorEmail" label="操作者" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.actorEmail || '（系统）' }}</template>
      </el-table-column>
      <el-table-column label="动作" width="120">
        <template #default="{ row }">
          <el-tag size="small" :type="actionTag(row.action)">{{ actionLabels[row.action] || row.action }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="detail" label="详情" min-width="260" show-overflow-tooltip />
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="暂无记录" />

    <div class="aap-pager">
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
import { ElMessage } from 'element-plus'
import { adminAuditQuery } from '@/net/index.js'
import { disableFutureDate } from '@/utils/dateLimits.js'

const ACTION_LABELS = {
  REVISION_APPROVE: '通过投稿',
  REVISION_REJECT: '驳回投稿',
  PAGE_CREATE: '创建页面',
  PAGE_UPDATE: '编辑页面',
  PAGE_DELETE: '删除页面',
  PAGE_RESTORE: '恢复页面',
  USER_CREATE: '创建用户',
  USER_UPDATE: '更新用户',
  USER_DELETE: '删除用户',
  USER_RESTORE: '恢复用户',
  FEEDBACK_REPLY: '回复反馈',
  WALL_CREATE: '新增致谢',
  WALL_UPDATE: '编辑致谢',
  WALL_DELETE: '删除致谢',
  CATEGORY_CREATE: '创建分类',
  CATEGORY_UPDATE: '编辑分类',
  CATEGORY_DELETE: '删除分类',
  BROADCAST: '发布公告',
}

export default {
  name: 'AdminAuditPanel',
  data() {
    return {
      dates: [], keyword: '', action: '',
      rows: [], total: 0, page: 1, size: 20, loading: false,
      actionLabels: ACTION_LABELS,
    }
  },
  mounted() { this.load() },
  methods: {
    disableFutureDate,
    actionTag(a) {
      if (!a) return 'info'
      if (a.endsWith('_DELETE') || a === 'REVISION_REJECT') return 'danger'
      if (a === 'BROADCAST') return 'warning'
      if (a.endsWith('_CREATE') || a === 'REVISION_APPROVE') return 'success'
      return 'info'
    },
    search() { this.page = 1; this.load() },
    load() {
      this.loading = true
      const [from, to] = Array.isArray(this.dates) && this.dates ? this.dates : []
      adminAuditQuery(
        { from, to, keyword: this.keyword, action: this.action, page: this.page, size: this.size },
        (d) => {
          this.rows = (d && d.list) || []
          this.total = Number(d && d.total) || 0
          this.loading = false
        },
        (m) => { this.loading = false; ElMessage.error(m || '加载失败') })
    },
  },
}
</script>

<style scoped>
.aap-head h1 { font-size: 1.5rem; font-weight: 800; letter-spacing: -0.02em; margin: 0 0 16px; color: var(--text-primary); }
.aap-filter { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.aap-kw { width: 240px; max-width: 100%; }
.aap-pager { display: flex; justify-content: flex-end; margin-top: 14px; }
@media (max-width: 768px) {
  .aap-kw { width: 100%; }
  .aap-filter :deep(.el-date-editor),
  .aap-filter :deep(.el-select),
  .aap-filter :deep(.el-button) {
    width: 100% !important;
    margin-inline: 0;
  }
  .aap-pager { justify-content: center; }
}
@media (max-width: 420px) {
  .aap-pager :deep(.el-pagination__total) { display: none; }
}
</style>
