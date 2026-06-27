<template>
  <div class="profile-page">
    <header class="pf-head">
      <div class="pf-identity">
        <div class="pf-avatar" aria-hidden="true">{{ initial }}</div>
        <div class="pf-copy">
          <div class="pf-name-row">
            <h1 class="pf-name">{{ nickname }}</h1>
            <span v-if="isAdmin" class="role-badge">管理员</span>
          </div>
          <p class="pf-email">{{ email }}</p>
        </div>
      </div>
      <div class="pf-actions">
        <router-link v-if="isAdmin" to="/admin" class="action-link secondary">
          <el-icon><Setting /></el-icon>
          <span>管理后台</span>
        </router-link>
        <router-link to="/edit" class="action-link primary">
          <el-icon><EditPen /></el-icon>
          <span>写文章</span>
        </router-link>
      </div>
    </header>

    <section class="pf-section">
      <div class="sec-head">
        <div>
          <p class="sec-kicker">CONTRIBUTIONS</p>
          <h2>我的投稿 <span class="count">{{ revisions.length }}</span></h2>
        </div>
      </div>

      <div v-if="loading" class="loading-state">加载投稿中…</div>
      <el-empty v-else-if="!revisions.length" description="还没有投稿，去写一篇吧" />

      <ul v-else class="rev-list">
        <li v-for="r in revisions" :key="r.id" class="rev-item">
          <div class="rev-mark" :class="r.type === 'CREATE' ? 't-create' : 't-update'">
            {{ r.type === 'CREATE' ? '新' : '改' }}
          </div>
          <div class="rev-main">
            <router-link
              v-if="r.status === 'APPROVED'"
              class="rev-title"
              :to="`/docs/${r.targetPath}`"
            >{{ r.title }}</router-link>
            <span v-else class="rev-title">{{ r.title }}</span>
            <div class="rev-meta">
              <span>{{ r.type === 'CREATE' ? '新建文章' : '修改文章' }}</span>
              <span class="meta-separator" aria-hidden="true"></span>
              <code class="rev-path">{{ r.targetPath }}</code>
            </div>
            <p v-if="r.status === 'REJECTED' && r.reviewComment" class="rev-reason">
              {{ r.reviewComment }}
            </p>
          </div>
          <div class="rev-side">
            <span class="status" :class="`s-${r.status.toLowerCase()}`">{{ statusText(r.status) }}</span>
            <span class="rev-date">
              <el-icon><Calendar /></el-icon>
              {{ fmt(r.createdAt) }}
            </span>
            <el-icon v-if="r.status === 'APPROVED'" class="row-arrow"><ArrowRight /></el-icon>
          </div>
        </li>
      </ul>
    </section>
  </div>
</template>

<script>
import { ElMessage } from 'element-plus'
import { ArrowRight, Calendar, EditPen, Setting } from '@element-plus/icons-vue'
import { getMyRevisions } from '@/net/index.js'
import { useUserStore } from '@/store/userStore.js'

export default {
  name: 'ProfilePage',
  components: { ArrowRight, Calendar, EditPen, Setting },
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
.profile-page { width: 100%; max-width: 1040px; margin: 0 auto; padding: 52px 28px 80px; }
.pf-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
  padding: 0 0 36px;
  border-bottom: 1px solid var(--border);
}
.pf-identity {
  display: flex;
  align-items: center;
  gap: 18px;
  min-width: 0;
}
.pf-avatar {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border: 1px solid var(--border-strong);
  border-radius: 8px;
  background: var(--text-primary);
  color: var(--bg-page);
  font-size: 24px;
  font-weight: 800;
  flex-shrink: 0;
}
.pf-copy { min-width: 0; }
.pf-name-row { display: flex; align-items: center; gap: 10px; min-width: 0; }
.pf-name {
  margin: 0;
  overflow: hidden;
  color: var(--text-primary);
  font-size: 1.45rem;
  font-weight: 780;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pf-email {
  margin: 5px 0 0;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: 13.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.role-badge {
  flex-shrink: 0;
  padding: 3px 7px;
  border: 1px solid var(--border-strong);
  border-radius: 5px;
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 700;
}
.pf-actions { display: flex; gap: 8px; flex-shrink: 0; }
.action-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 40px;
  padding: 0 14px;
  border: 1px solid var(--border);
  border-radius: 7px;
  font-size: 13.5px;
  font-weight: 700;
  transition: background-color .18s ease, border-color .18s ease, color .18s ease;
}
.action-link:hover { text-decoration: none; }
.action-link.primary { border-color: var(--accent); background: var(--accent); color: var(--accent-contrast); }
.action-link.primary:hover { opacity: .82; }
.action-link.secondary { background: var(--bg-surface); color: var(--text-secondary); }
.action-link.secondary:hover { border-color: var(--border-strong); background: var(--bg-hover); color: var(--text-primary); }

.pf-section { padding-top: 34px; }
.sec-head { display: flex; align-items: end; justify-content: space-between; margin-bottom: 16px; }
.sec-kicker { margin: 0 0 5px; color: var(--text-muted); font-size: 10px; font-weight: 800; letter-spacing: 0; }
.sec-head h2 { margin: 0; color: var(--text-primary); font-size: 1.15rem; font-weight: 750; }
.count {
  display: inline-grid;
  place-items: center;
  min-width: 22px;
  height: 22px;
  margin-left: 6px;
  border-radius: 5px;
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-size: 12px;
  vertical-align: 2px;
}
.loading-state { padding: 24px 0; color: var(--text-muted); font-size: 13px; }

.rev-list {
  overflow: hidden;
  margin: 0;
  padding: 0;
  border: 1px solid var(--border);
  border-radius: 8px;
  list-style: none;
}
.rev-item {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: 14px;
  min-height: 84px;
  padding: 15px 18px;
  background: var(--bg-surface);
  transition: background-color .16s ease;
}
.rev-item + .rev-item { border-top: 1px solid var(--border); }
.rev-item:hover { background: var(--bg-subtle); }
.rev-mark {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 7px;
  font-size: 12px;
  font-weight: 800;
}
.t-create { background: #e6f4ec; color: #137a3f; }
.t-update { background: #eef1fb; color: #3a52c4; }
html.dark .t-create { background: rgba(19,122,63,.2); color: #6ee7a8; }
html.dark .t-update { background: rgba(58,82,196,.22); color: #aab8ff; }
.rev-main { min-width: 0; }
.rev-title {
  display: block;
  overflow: hidden;
  color: var(--text-primary);
  font-size: 14.5px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}
a.rev-title:hover { text-decoration: underline; }
.rev-meta { display: flex; align-items: center; gap: 8px; margin-top: 5px; color: var(--text-muted); font-size: 12px; }
.meta-separator { width: 3px; height: 3px; border-radius: 50%; background: var(--border-strong); }
.rev-path {
  overflow: hidden;
  padding: 0;
  background: transparent;
  color: var(--text-muted);
  font-family: inherit;
  font-size: inherit;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rev-side { display: grid; grid-template-columns: auto 104px 16px; align-items: center; gap: 14px; }
.status { padding: 4px 8px; border-radius: 5px; font-size: 11.5px; font-weight: 750; white-space: nowrap; }
.s-pending { background: #fff4e0; color: #b3691a; }
.s-approved { background: #e6f4ec; color: #137a3f; }
.s-rejected { background: #fbe9e9; color: #c0392b; }
html.dark .s-pending { background: rgba(179,105,26,.2); color: #f0b66a; }
html.dark .s-approved { background: rgba(19,122,63,.2); color: #6ee7a8; }
html.dark .s-rejected { background: rgba(192,57,43,.2); color: #f3a097; }
.rev-date { display: inline-flex; align-items: center; gap: 5px; color: var(--text-muted); font-size: 12px; white-space: nowrap; }
.row-arrow { color: var(--text-muted); }
.rev-reason { margin: 7px 0 0; color: #c0392b; font-size: 12px; }
html.dark .rev-reason { color: #f3a097; }

@media (max-width: 720px) {
  .profile-page { padding: 28px 16px 56px; }
  .pf-head { align-items: stretch; flex-direction: column; gap: 22px; padding-bottom: 28px; }
  .pf-actions { width: 100%; }
  .action-link { flex: 1; }
  .pf-section { padding-top: 28px; }
  .rev-item { grid-template-columns: 34px minmax(0, 1fr); gap: 12px; padding: 15px 14px; }
  .rev-side {
    grid-column: 2;
    grid-template-columns: auto 1fr;
    justify-content: start;
    gap: 10px;
    margin-top: -2px;
  }
  .row-arrow { display: none; }
}

@media (max-width: 420px) {
  .pf-avatar { width: 48px; height: 48px; font-size: 21px; }
  .pf-name { font-size: 1.2rem; }
  .role-badge { display: none; }
  .rev-meta { max-width: 100%; }
}
</style>
