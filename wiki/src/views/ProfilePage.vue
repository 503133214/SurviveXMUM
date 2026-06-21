<template>
  <div class="profile-page">
    <header class="pf-head">
      <div class="pf-avatar">{{ initial }}</div>
      <div>
        <h1 class="pf-name">{{ nickname }}</h1>
        <p class="pf-email">{{ email }} <span v-if="isAdmin" class="role-badge">管理员</span></p>
      </div>
      <div class="pf-actions">
        <router-link to="/edit" class="btn-solid">写文章</router-link>
        <router-link v-if="isAdmin" to="/admin" class="btn-ghost">管理后台</router-link>
      </div>
    </header>

    <section class="pf-section">
      <div class="sec-head">
        <h2>我的投稿</h2>
        <span class="count">{{ revisions.length }} 条</span>
      </div>

      <div v-if="loading" class="muted">加载中…</div>
      <el-empty v-else-if="!revisions.length" description="还没有投稿，去写一篇吧" />

      <ul v-else class="rev-list">
        <li v-for="r in revisions" :key="r.id" class="rev-item">
          <div class="rev-main">
            <span class="rev-type" :class="r.type === 'CREATE' ? 't-create' : 't-update'">
              {{ r.type === 'CREATE' ? '新建' : '修改' }}
            </span>
            <router-link
              v-if="r.status === 'APPROVED'"
              class="rev-title"
              :to="`/docs/${r.targetPath}`"
            >{{ r.title }}</router-link>
            <span v-else class="rev-title">{{ r.title }}</span>
            <span class="rev-path">{{ r.targetPath }}</span>
          </div>
          <div class="rev-side">
            <span class="status" :class="`s-${r.status.toLowerCase()}`">{{ statusText(r.status) }}</span>
            <span class="rev-date">{{ fmt(r.createdAt) }}</span>
          </div>
          <p v-if="r.status === 'REJECTED' && r.reviewComment" class="rev-reason">
            驳回原因：{{ r.reviewComment }}
          </p>
        </li>
      </ul>
    </section>
  </div>
</template>

<script>
import { ElMessage } from 'element-plus'
import { getMyRevisions } from '@/net/index.js'
import { useUserStore } from '@/store/userStore.js'

export default {
  name: 'ProfilePage',
  data() {
    return { userStore: useUserStore(), revisions: [], loading: true }
  },
  computed: {
    email() { return this.userStore.userInfo?.userEmail || '' },
    nickname() { return this.userStore.userInfo?.nickname || this.userStore.username || '用户' },
    isAdmin() { return this.userStore.userInfo?.role === 'ADMIN' },
    initial() { return (this.nickname || 'U').charAt(0).toUpperCase() },
  },
  mounted() {
    if (!this.userStore.userInfo) this.userStore.fetchUserInfo()
    getMyRevisions(
      (data) => { this.revisions = data || []; this.loading = false },
      (msg) => { this.loading = false; ElMessage.error(msg || '加载投稿失败') }
    )
  },
  methods: {
    statusText(s) { return { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }[s] || s },
    fmt(iso) {
      if (!iso) return ''
      const d = new Date(iso)
      return Number.isNaN(d.getTime()) ? '' :
        `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    },
  },
}
</script>

<style scoped>
.profile-page { max-width: 880px; margin: 0 auto; padding: 40px 24px 64px; }
.pf-head {
  display: flex;
  align-items: center;
  gap: 18px;
  padding-bottom: 28px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 28px;
}
.pf-avatar {
  width: 64px; height: 64px;
  border-radius: 50%;
  background: var(--brand-gradient);
  color: #fff;
  font-size: 28px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.pf-name { font-size: 1.5rem; font-weight: 800; letter-spacing: -0.02em; margin: 0; color: var(--text-primary); }
.pf-email { color: var(--text-secondary); font-size: 14px; margin: 4px 0 0; }
.role-badge {
  display: inline-block; margin-left: 8px;
  font-size: 11.5px; font-weight: 700;
  padding: 2px 8px; border-radius: 999px;
  background: var(--accent); color: var(--accent-contrast);
}
.pf-actions { margin-left: auto; display: flex; gap: 10px; }
.btn-solid, .btn-ghost {
  padding: 9px 16px; border-radius: var(--radius-sm);
  font-weight: 700; font-size: 14px; cursor: pointer; transition: all .2s ease;
}
.btn-solid { background: var(--accent); color: var(--accent-contrast); }
.btn-solid:hover { opacity: .88; text-decoration: none; }
.btn-ghost { border: 1px solid var(--border); color: var(--text-secondary); }
.btn-ghost:hover { background: var(--bg-hover); color: var(--text-primary); text-decoration: none; }

.sec-head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; }
.sec-head h2 { font-size: 1.15rem; font-weight: 700; margin: 0; color: var(--text-primary); }
.count { color: var(--text-muted); font-size: 13px; }
.muted { color: var(--text-muted); }

.rev-list { list-style: none; padding: 0; margin: 0; }
.rev-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 6px 16px;
  padding: 16px 4px;
  border-bottom: 1px solid var(--border);
}
.rev-main { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; min-width: 0; }
.rev-type { font-size: 11.5px; font-weight: 700; padding: 2px 8px; border-radius: 6px; flex-shrink: 0; }
.t-create { background: #e6f4ec; color: #137a3f; }
.t-update { background: #eef1fb; color: #3a52c4; }
html.dark .t-create { background: rgba(19,122,63,.2); color: #6ee7a8; }
html.dark .t-update { background: rgba(58,82,196,.22); color: #aab8ff; }
.rev-title { font-weight: 600; color: var(--text-primary); }
a.rev-title:hover { text-decoration: underline; }
.rev-path { font-size: 12.5px; color: var(--text-muted); }
.rev-side { display: flex; align-items: center; gap: 12px; }
.status { font-size: 12.5px; font-weight: 700; padding: 3px 10px; border-radius: 999px; }
.s-pending { background: #fff4e0; color: #b3691a; }
.s-approved { background: #e6f4ec; color: #137a3f; }
.s-rejected { background: #fbe9e9; color: #c0392b; }
html.dark .s-pending { background: rgba(179,105,26,.2); color: #f0b66a; }
html.dark .s-approved { background: rgba(19,122,63,.2); color: #6ee7a8; }
html.dark .s-rejected { background: rgba(192,57,43,.2); color: #f3a097; }
.rev-date { font-size: 12.5px; color: var(--text-muted); }
.rev-reason { grid-column: 1 / -1; margin: 4px 0 0; font-size: 13px; color: #c0392b; }
html.dark .rev-reason { color: #f3a097; }

@media (max-width: 640px) {
  .pf-head { flex-wrap: wrap; }
  .pf-actions { margin-left: 0; width: 100%; }
}
</style>
