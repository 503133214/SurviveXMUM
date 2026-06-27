<template>
  <div class="admin-console">
    <aside class="ac-nav">
      <div class="ac-title">管理后台</div>
      <button
        v-for="section in adminSections"
        :key="section.key"
        class="ac-item"
        :class="{ active: activeSection === section.key }"
        @click="activeSection = section.key"
      >
        <el-icon><component :is="section.icon" /></el-icon>
        <span>{{ section.label }}</span>
      </button>
    </aside>

    <section class="ac-main">
      <AdminPagesPanel v-if="activeSection === 'pages'" />
      <AdminUsersPanel v-else-if="activeSection === 'users' && isSuperAdmin" />

      <div v-else class="admin-page">
        <header class="ad-head">
          <h1>投稿审核</h1>
      <div class="seg">
        <button v-for="s in tabs" :key="s.key" :class="{ active: status === s.key }" @click="switchStatus(s.key)">
          {{ s.label }}<span v-if="counts[s.key] != null" class="seg-count">{{ counts[s.key] }}</span>
        </button>
      </div>
    </header>

    <div v-if="isMobileAdmin" class="mobile-admin-notice" role="status">
      <strong>手机端为只读模式</strong>
      <span>你可以查看投稿内容，但通过和驳回操作需要在电脑端完成。</span>
    </div>

    <div class="ad-body" :class="{ 'list-collapsed': listCollapsed }">
      <!-- 列表 -->
      <aside v-show="(!isMobileAdmin || !current) && !listCollapsed" class="ad-list">
        <div v-if="loadingList" class="muted pad">加载中…</div>
        <el-empty v-else-if="!list.length" :description="`暂无${currentLabel}投稿`" />
        <ul v-else>
          <li
            v-for="r in list"
            :key="r.id"
            :class="{ active: current && current.id === r.id }"
            @click="openDetail(r.id)"
          >
            <div class="li-top">
              <span class="rev-type" :class="r.type === 'CREATE' ? 't-create' : 't-update'">
                {{ r.type === 'CREATE' ? '新建' : '修改' }}
              </span>
              <span class="li-title">{{ r.title }}</span>
            </div>
            <div class="li-meta">
              <span>{{ r.authorEmail }}</span>
              <span>{{ fmt(r.createdAt) }}</span>
            </div>
          </li>
        </ul>
      </aside>

      <!-- 详情 -->
      <main v-show="!isMobileAdmin || current" class="ad-detail">
        <div v-if="!current" class="placeholder">
          <el-icon :size="40"><Tickets /></el-icon>
          <p>从左侧选择一条投稿开始审核</p>
        </div>
        <template v-else>
          <div class="dt-head">
            <div>
              <button v-if="isMobileAdmin" class="mobile-detail-back" type="button" @click="current = null">
                ← 返回投稿列表
              </button>
              <h2>{{ current.title }}</h2>
              <p class="dt-meta">
                <span class="rev-type" :class="current.type === 'CREATE' ? 't-create' : 't-update'">
                  {{ current.type === 'CREATE' ? '新建' : '修改' }}
                </span>
                路径 <code>{{ current.targetPath }}</code>
                · 投稿人 <template v-if="current.authorNickname">{{ current.authorNickname }} </template>{{ current.authorEmail }}
                · 提交于 {{ fmt(current.createdAt) }}
                <template v-if="current.status !== 'PENDING'">
                  <br />
                  <span class="dt-review">
                    {{ statusText(current.status) }}
                    <template v-if="current.reviewerEmail">· 审核人 {{ current.reviewerEmail }}</template>
                    <template v-if="current.reviewedAt">· {{ fmt(current.reviewedAt) }}</template>
                  </span>
                </template>
              </p>
            </div>
            <div v-if="current.status === 'PENDING' && !isMobileAdmin" class="dt-actions">
              <button class="btn-reject" :disabled="acting" @click="reject">驳回</button>
              <button class="btn-approve" :disabled="acting" @click="approve">通过并发布</button>
            </div>
            <div v-else-if="current.status === 'PENDING'" class="mobile-review-lock">
              请在电脑端完成审核
            </div>
            <div v-else class="dt-status">
              <span class="status" :class="`s-${current.status.toLowerCase()}`">{{ statusText(current.status) }}</span>
            </div>
          </div>

          <div class="review-toolbar">
            <div class="review-tabs" role="tablist" aria-label="审核视图">
              <button
                v-for="view in reviewViews"
                :key="view.key"
                type="button"
                role="tab"
                :aria-selected="reviewMode === view.key"
                :class="{ active: reviewMode === view.key }"
                @click="reviewMode = view.key"
              >
                {{ view.label }}
              </button>
            </div>
            <button
              v-if="!isMobileAdmin"
              class="list-toggle"
              type="button"
              @click="listCollapsed = !listCollapsed"
            >
              {{ listCollapsed ? '显示投稿列表' : '收起投稿列表' }}
            </button>
          </div>

          <div class="review-canvas">
            <MarkdownDiff
              v-if="current.type === 'UPDATE' && reviewMode === 'diff'"
              :before="current.currentContent || ''"
              :after="current.content || ''"
            />
            <div v-else class="preview-pane">
              <div class="pane-head">{{ previewTitle }}</div>
              <div class="pane-body markdown-scope">
                <MarkdownRenderer
                  v-if="previewContent"
                  :content="previewContent"
                  :base-path="baseDir"
                  embedded
                />
                <p v-else class="muted">（无内容或内容已删除）</p>
              </div>
            </div>
          </div>
        </template>
      </main>
    </div>
      </div>
    </section>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, Tickets, User } from '@element-plus/icons-vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import MarkdownDiff from '@/components/MarkdownDiff.vue'
import AdminPagesPanel from '@/components/AdminPagesPanel.vue'
import AdminUsersPanel from '@/components/AdminUsersPanel.vue'
import { useUserStore } from '@/store/userStore.js'
import {
  adminListRevisions, adminGetRevision, adminApproveRevision, adminRejectRevision, adminRevisionCounts,
} from '@/net/index.js'

export default {
  name: 'AdminPage',
  components: {
    MarkdownRenderer: markRaw(MarkdownRenderer),
    MarkdownDiff: markRaw(MarkdownDiff),
    AdminPagesPanel: markRaw(AdminPagesPanel),
    AdminUsersPanel: markRaw(AdminUsersPanel),
    Tickets,
  },
  data() {
    return {
      activeSection: 'review',
      status: 'PENDING',
      tabs: [
        { key: 'PENDING', label: '待审核' },
        { key: 'APPROVED', label: '已通过' },
        { key: 'REJECTED', label: '已驳回' },
      ],
      counts: {},
      list: [],
      current: null,
      loadingList: true,
      acting: false,
      isMobileAdmin: false,
      adminMediaQuery: null,
      reviewMode: 'diff',
      listCollapsed: false,
    }
  },
  computed: {
    isSuperAdmin() { return useUserStore().isSuperAdmin },
    adminSections() {
      const s = [
        { key: 'review', label: '投稿审核', icon: markRaw(Tickets) },
        { key: 'pages', label: '页面管理', icon: markRaw(Document) },
      ]
      // 用户管理仅超级管理员可见
      if (this.isSuperAdmin) s.push({ key: 'users', label: '用户管理', icon: markRaw(User) })
      return s
    },
    currentLabel() { return this.tabs.find((t) => t.key === this.status)?.label || '' },
    reviewViews() {
      if (this.current?.type !== 'UPDATE') return [{ key: 'submitted', label: '投稿预览' }]
      return [
        { key: 'diff', label: '差异' },
        { key: 'current', label: '当前线上' },
        { key: 'submitted', label: '投稿预览' },
      ]
    },
    previewContent() {
      return this.reviewMode === 'current'
        ? this.current?.currentContent || ''
        : this.current?.content || ''
    },
    previewTitle() {
      return this.reviewMode === 'current' ? '当前线上内容' : '投稿内容（预览）'
    },
    baseDir() {
      const p = this.current?.targetPath || ''
      return p.includes('/') ? p.slice(0, p.lastIndexOf('/')) : ''
    },
  },
  mounted() {
    this.adminMediaQuery = markRaw(window.matchMedia('(max-width: 768px)'))
    this.syncMobileAdmin()
    this.adminMediaQuery.addEventListener('change', this.syncMobileAdmin)
    this.loadList()
    this.loadCounts()
  },
  beforeUnmount() {
    this.adminMediaQuery?.removeEventListener('change', this.syncMobileAdmin)
  },
  methods: {
    syncMobileAdmin() {
      const wasMobile = this.isMobileAdmin
      this.isMobileAdmin = this.adminMediaQuery?.matches ?? false
      if (this.isMobileAdmin) this.listCollapsed = false
      if (wasMobile && !this.isMobileAdmin && !this.current && this.list.length) {
        this.openDetail(this.list[0].id)
      }
    },
    statusText(s) { return { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }[s] || s },
    fmt(iso) {
      if (!iso) return ''
      const d = new Date(iso)
      return Number.isNaN(d.getTime()) ? '' :
        `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
    },
    switchStatus(s) { this.status = s; this.current = null; this.loadList() },
    loadCounts() {
      adminRevisionCounts((data) => { this.counts = data || {} }, () => {})
    },
    loadList() {
      this.loadingList = true
      adminListRevisions(
        this.status,
        (data) => {
          this.list = data || []
          this.loadingList = false
          // 自动打开第一条，避免详情区空白
          if (this.list.length && !this.current && !this.isMobileAdmin) this.openDetail(this.list[0].id)
        },
        (msg) => { this.loadingList = false; ElMessage.error(msg || '加载失败') }
      )
    },
    openDetail(id) {
      adminGetRevision(id,
        (data) => {
          this.current = data
          this.reviewMode = data.type === 'UPDATE' ? 'diff' : 'submitted'
        },
        (msg) => ElMessage.error(msg || '加载详情失败'))
    },
    approve() {
      this.acting = true
      adminApproveRevision(this.current.id,
        () => {
          this.acting = false
          ElMessage.success('已通过并发布')
          this.afterAction()
        },
        (msg) => { this.acting = false; ElMessage.error(msg || '操作失败') })
    },
    async reject() {
      let comment = ''
      try {
        const { value } = await ElMessageBox.prompt('请输入驳回原因（投稿人可见）', '驳回投稿', {
          confirmButtonText: '确认驳回', cancelButtonText: '取消', inputType: 'textarea',
        })
        comment = value || ''
      } catch { return }
      this.acting = true
      adminRejectRevision(this.current.id, comment,
        () => {
          this.acting = false
          ElMessage.success('已驳回')
          this.afterAction()
        },
        (msg) => { this.acting = false; ElMessage.error(msg || '操作失败') })
    },
    afterAction() {
      this.current = null
      this.loadList()
      this.loadCounts()
    },
  },
}
</script>

<style scoped>
.admin-console {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  align-items: start;
  gap: 28px;
  width: 100%;
  max-width: 1600px;
  margin: 0 auto;
  padding: 28px 24px 64px;
}
.ac-nav {
  position: sticky;
  top: calc(var(--header-height) + 20px);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ac-title {
  padding: 6px 12px 12px;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: .06em;
  text-transform: uppercase;
}
.ac-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  transition: background .18s ease, color .18s ease;
}
.ac-item:hover { background: var(--bg-subtle); color: var(--text-primary); }
.ac-item.active {
  background: var(--bg-subtle);
  color: var(--text-primary);
  box-shadow: inset 3px 0 0 var(--accent);
}
.ac-item .el-icon { color: var(--text-muted); font-size: 17px; }
.ac-item.active .el-icon { color: var(--text-primary); }
.ac-main { min-width: 0; }
.admin-page { width: 100%; }
.ad-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 24px; flex-wrap: wrap; }
.ad-head h1 { font-size: 1.5rem; font-weight: 800; letter-spacing: -0.02em; margin: 0; color: var(--text-primary); }
.seg { display: flex; background: var(--bg-subtle); border-radius: 999px; padding: 4px; }
.seg button {
  border: none; background: transparent; padding: 8px 18px;
  font-size: 14px; font-weight: 600; color: var(--text-secondary);
  border-radius: 999px; cursor: pointer; transition: all .2s var(--ease-out);
}
.seg button.active { background: var(--bg-surface); color: var(--text-primary); box-shadow: var(--shadow-sm); }
.seg-count {
  display: inline-grid; place-items: center; min-width: 18px; height: 18px;
  margin-left: 6px; padding: 0 5px; border-radius: 999px;
  background: var(--bg-hover); color: var(--text-muted);
  font-size: 11px; font-weight: 700; vertical-align: 1px;
}
.seg button.active .seg-count { background: var(--accent); color: var(--accent-contrast); }
.dt-review { color: var(--text-secondary); font-size: 12.5px; }

.mobile-admin-notice {
  display: none;
  padding: 14px 16px;
  margin-bottom: 16px;
  border: 1px solid var(--border);
  border-left: 3px solid var(--accent);
  border-radius: var(--radius-sm);
  background: var(--bg-subtle);
}
.mobile-admin-notice strong { color: var(--text-primary); font-size: 14px; }
.mobile-admin-notice span { color: var(--text-secondary); font-size: 13px; }

.ad-body { display: grid; grid-template-columns: clamp(280px, 22vw, 360px) minmax(0, 1fr); gap: 20px; align-items: start; }
.ad-body.list-collapsed { grid-template-columns: minmax(0, 1fr); }
.ad-list {
  border: 1px solid var(--border); border-radius: var(--radius);
  overflow: hidden auto; background: var(--bg-surface);
  max-height: calc(100vh - 180px);
  position: sticky; top: calc(var(--header-height) + 20px);
}
.ad-list ul { list-style: none; margin: 0; padding: 0; }
.ad-list li { padding: 14px 16px; border-bottom: 1px solid var(--border); cursor: pointer; transition: background .15s ease; }
.ad-list li:last-child { border-bottom: none; }
.ad-list li:hover { background: var(--bg-hover); }
.ad-list li.active { background: var(--bg-subtle); box-shadow: inset 3px 0 0 var(--accent); }
.li-top { display: flex; align-items: center; gap: 8px; margin-bottom: 5px; }
.li-title { font-weight: 600; color: var(--text-primary); font-size: 14.5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.li-meta { display: flex; justify-content: space-between; gap: 10px; font-size: 12px; color: var(--text-muted); }
.li-meta span:first-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.li-meta span:last-child { flex-shrink: 0; }

.ad-detail { border: 1px solid var(--border); border-radius: var(--radius); background: var(--bg-surface); min-height: 64vh; overflow: hidden; }
.placeholder {
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 14px;
  min-height: 64vh; color: var(--text-muted);
}
.placeholder .el-icon { color: var(--border-strong); }
.placeholder p { margin: 0; font-size: 14px; }
.dt-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; padding: 22px 24px; border-bottom: 1px solid var(--border); flex-wrap: wrap; }
.dt-head h2 { margin: 0; font-size: 1.3rem; font-weight: 800; color: var(--text-primary); }
.mobile-detail-back {
  display: none;
  padding: 0;
  margin-bottom: 12px;
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.dt-meta { margin: 8px 0 0; font-size: 13px; color: var(--text-secondary); display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.dt-meta code { background: var(--bg-subtle); padding: 2px 6px; border-radius: 5px; font-size: 12.5px; }
.dt-actions { display: flex; gap: 10px; flex-shrink: 0; }
.btn-approve, .btn-reject { padding: 9px 18px; border-radius: var(--radius-sm); font-weight: 700; font-size: 14px; cursor: pointer; border: none; transition: all .2s ease; }
.btn-approve { background: #137a3f; color: #fff; }
.btn-approve:hover:not(:disabled) { opacity: .9; }
.btn-reject { background: transparent; color: #c0392b; border: 1px solid #e3b4ae; }
.btn-reject:hover:not(:disabled) { background: #fbe9e9; }
.btn-approve:disabled, .btn-reject:disabled { opacity: .6; cursor: not-allowed; }

.rev-type { font-size: 11.5px; font-weight: 700; padding: 2px 8px; border-radius: 6px; flex-shrink: 0; }
.t-create { background: #e6f4ec; color: #137a3f; }
.t-update { background: #eef1fb; color: #3a52c4; }
html.dark .t-create { background: rgba(19,122,63,.2); color: #6ee7a8; }
html.dark .t-update { background: rgba(58,82,196,.22); color: #aab8ff; }
.status { font-size: 12.5px; font-weight: 700; padding: 3px 10px; border-radius: 999px; }
.s-approved { background: #e6f4ec; color: #137a3f; }
.s-rejected { background: #fbe9e9; color: #c0392b; }
.s-pending { background: #fff4e0; color: #b3691a; }
.mobile-review-lock {
  padding: 7px 10px;
  border-radius: 999px;
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.review-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border);
  background: var(--bg-subtle);
}
.review-tabs {
  display: inline-flex;
  gap: 3px;
  padding: 3px;
  border: 1px solid var(--border);
  border-radius: 9px;
  background: var(--bg-surface);
}
.review-tabs button,
.list-toggle {
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 12.5px;
  font-weight: 650;
  cursor: pointer;
}
.review-tabs button { padding: 6px 12px; }
.review-tabs button.active {
  background: var(--accent);
  color: var(--accent-contrast);
}
.list-toggle { padding: 6px 9px; }
.list-toggle:hover { color: var(--text-primary); background: var(--bg-hover); }
.review-canvas { min-width: 0; }
.preview-pane { min-width: 0; }
.pane-head { padding: 10px 18px; font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: .04em; color: var(--text-muted); border-bottom: 1px solid var(--border); background: var(--bg-subtle); }
.pane-body { padding: 24px 28px; min-height: 48vh; overflow: auto; }
.muted { color: var(--text-muted); }
.pad { padding: 16px; }

@media (max-width: 900px) {
  .ad-body { grid-template-columns: 1fr; }
}

@media (max-width: 860px) {
  .admin-console { grid-template-columns: 1fr; gap: 16px; }
  .ac-nav { position: static; flex-direction: row; overflow-x: auto; }
  .ac-title { display: none; }
  .ac-item { flex: 1 0 auto; justify-content: center; white-space: nowrap; }
  .ac-item.active { box-shadow: inset 0 -3px 0 var(--accent); }
}

@media (max-width: 768px) {
  .admin-console { padding: 20px 16px 48px; }
  .ad-head { align-items: flex-start; margin-bottom: 16px; }
  .ad-head h1 { width: 100%; font-size: 1.25rem; }
  .seg { width: 100%; }
  .seg button { flex: 1; padding-inline: 8px; }
  .mobile-admin-notice { display: flex; flex-direction: column; gap: 3px; }
  .ad-list { position: static; max-height: none; }
  .ad-list li { padding: 16px; }
  .ad-detail { min-height: 0; }
  .placeholder { min-height: 240px; }
  .dt-head { padding: 18px 16px; }
  .mobile-detail-back { display: inline-flex; }
  .review-toolbar { align-items: stretch; padding: 10px; }
  .review-tabs { width: 100%; overflow-x: auto; }
  .review-tabs button { flex: 1; white-space: nowrap; }
  .pane-body { max-height: none; padding: 18px 16px; }
}
</style>
