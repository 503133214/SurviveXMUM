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
      <AdminFeedbackPanel v-else-if="activeSection === 'feedback'" />
      <AdminCommentsPanel v-else-if="activeSection === 'comments'" />
      <AdminWallPanel v-else-if="activeSection === 'wall' && isSuperAdmin" />
      <AdminCategoriesPanel v-else-if="activeSection === 'categories' && isSuperAdmin" />
      <AdminBroadcastPanel v-else-if="activeSection === 'broadcast' && isSuperAdmin" />
      <AdminAuditPanel v-else-if="activeSection === 'audit' && isSuperAdmin" />

      <div v-else class="admin-page">
        <header class="rv-head">
          <h1>投稿审核</h1>
      <div class="seg">
        <button v-for="s in tabs" :key="s.key" :class="{ active: status === s.key }" @click="switchStatus(s.key)">
          {{ s.label }}<span v-if="counts[s.key] != null" class="seg-count">{{ counts[s.key] }}</span>
        </button>
      </div>
    </header>

    <div class="rv-filter">
      <el-date-picker
        v-model="filterDates"
        type="daterange"
        unlink-panels
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        :disabled-date="disableFutureDate"
        @change="onFilterChange"
      />
      <el-input
        v-model="filterKeyword"
        placeholder="搜索标题 / 路径 / 作者邮箱"
        clearable
        class="rv-filter-kw"
        @keyup.enter="onFilterChange"
        @clear="onFilterChange"
      />
      <el-button @click="onFilterChange">查询</el-button>
      <el-button v-if="hasFilter" text @click="resetFilter">重置</el-button>
    </div>

    <div v-if="isMobileAdmin" class="mobile-admin-notice" role="status">
      <strong>手机端为只读模式</strong>
      <span>你可以查看投稿内容，但通过和驳回操作需要在电脑端完成。</span>
    </div>

    <div class="rv-body" :class="{ 'list-collapsed': listCollapsed }">
      <!-- 列表 -->
      <aside v-show="(!isMobileAdmin || !current) && !listCollapsed" class="rv-list">
        <div v-if="loadingList" class="muted pad">加载中…</div>
        <el-empty v-else-if="!list.length" :description="`暂无${currentLabel}投稿`" />
        <ul v-else>
          <li v-for="r in list" :key="r.id">
            <!-- 整行是 button：键盘可以 Tab 到每条投稿并回车打开 -->
            <button
              type="button"
              class="rv-row"
              :class="{ active: current && current.id === r.id }"
              :aria-current="current && current.id === r.id ? 'true' : null"
              @click="openDetail(r.id)"
            >
              <span class="li-top">
                <span class="rev-type" :class="r.type === 'CREATE' ? 't-create' : 't-update'">
                  {{ r.type === 'CREATE' ? '新建' : '修改' }}
                </span>
                <span class="li-title">{{ r.title }}</span>
              </span>
              <span class="li-meta">
                <span>{{ r.authorEmail }}</span>
                <span>{{ fmt(r.createdAt) }}</span>
              </span>
            </button>
          </li>
        </ul>
      </aside>

      <!-- 详情 -->
      <section v-show="!isMobileAdmin || current" class="rv-detail">
        <div v-if="!current" class="placeholder">
          <strong>还没有选中投稿</strong>
          <p>从左侧列表选择一条投稿开始审核</p>
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
                <!-- 昵称与邮箱写在同一个插值里：模板里的尾随空格会被压缩掉，两者会粘在一起 -->
                · 投稿人 {{ current.authorNickname ? `${current.authorNickname}（${current.authorEmail}）` : current.authorEmail }}
                · 提交于 {{ fmt(current.createdAt) }}
                <template v-if="current.status !== 'PENDING'">
                  <br />
                  <span class="dt-review">
                    {{ statusText(current.status) }}
                    <!-- 分隔符前留空格：两个 template 之间的换行会被编译器去掉，否则文字会粘在一起 -->
                    <template v-if="current.reviewerEmail"> · 审核人 {{ current.reviewerEmail }}</template>
                    <template v-if="current.reviewedAt"> · {{ fmt(current.reviewedAt) }}</template>
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
              <!-- 超管对已通过/已驳回的改判操作（移动端只读） -->
              <template v-if="isSuperAdmin && !isMobileAdmin">
                <template v-if="current.status === 'REJECTED'">
                  <button class="btn-approve" :disabled="acting" @click="reapprove">改判通过并发布</button>
                  <button class="btn-ghost-sm" :disabled="acting" @click="editComment">修改驳回原因</button>
                </template>
                <template v-else-if="current.status === 'APPROVED'">
                  <button class="btn-reject" :disabled="acting" @click="revokeApproved">撤销通过</button>
                </template>
                <button class="btn-ghost-sm danger" :disabled="acting" @click="purgeRevisionRecord">删除记录</button>
              </template>
            </div>
          </div>

          <div v-if="current.status === 'REJECTED'" class="rejection-note">
            <strong>驳回原因</strong>
            <p>{{ current.reviewComment || '未填写驳回原因' }}</p>
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
      </section>
    </div>
      </div>
    </section>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, Tickets, User, ChatDotRound, ChatLineSquare, Trophy, FolderOpened, Bell, List } from '@element-plus/icons-vue'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import MarkdownDiff from '@/components/MarkdownDiff.vue'
import AdminPagesPanel from '@/components/AdminPagesPanel.vue'
import AdminUsersPanel from '@/components/AdminUsersPanel.vue'
import AdminFeedbackPanel from '@/components/AdminFeedbackPanel.vue'
import AdminCommentsPanel from '@/components/AdminCommentsPanel.vue'
import AdminWallPanel from '@/components/AdminWallPanel.vue'
import AdminCategoriesPanel from '@/components/AdminCategoriesPanel.vue'
import AdminBroadcastPanel from '@/components/AdminBroadcastPanel.vue'
import AdminAuditPanel from '@/components/AdminAuditPanel.vue'
import { useUserStore } from '@/store/userStore.js'
import { disableFutureDate } from '@/utils/dateLimits.js'
import {
  adminListRevisions, adminGetRevision, adminApproveRevision, adminRejectRevision, adminRevisionCounts,
  adminReapproveRevision, adminRevokeRevision, adminUpdateRevisionComment, adminPurgeRevision,
} from '@/net/index.js'

export default {
  name: 'AdminPage',
  components: {
    MarkdownRenderer: markRaw(MarkdownRenderer),
    MarkdownDiff: markRaw(MarkdownDiff),
    AdminPagesPanel: markRaw(AdminPagesPanel),
    AdminUsersPanel: markRaw(AdminUsersPanel),
    AdminFeedbackPanel: markRaw(AdminFeedbackPanel),
    AdminCommentsPanel: markRaw(AdminCommentsPanel),
    AdminWallPanel: markRaw(AdminWallPanel),
    AdminCategoriesPanel: markRaw(AdminCategoriesPanel),
    AdminBroadcastPanel: markRaw(AdminBroadcastPanel),
    AdminAuditPanel: markRaw(AdminAuditPanel),
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
      filterDates: [],
      filterKeyword: '',
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
        { key: 'feedback', label: '反馈管理', icon: markRaw(ChatDotRound) },
        { key: 'comments', label: '评论管理', icon: markRaw(ChatLineSquare) },
      ]
      // 以下仅超级管理员可见
      if (this.isSuperAdmin) {
        s.push({ key: 'users', label: '用户管理', icon: markRaw(User) })
        s.push({ key: 'categories', label: '分类管理', icon: markRaw(FolderOpened) })
        s.push({ key: 'wall', label: '致谢墙', icon: markRaw(Trophy) })
        s.push({ key: 'broadcast', label: '发布公告', icon: markRaw(Bell) })
        s.push({ key: 'audit', label: '审计日志', icon: markRaw(List) })
      }
      return s
    },
    hasFilter() {
      return (Array.isArray(this.filterDates) && this.filterDates.length === 2) || !!this.filterKeyword
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
    disableFutureDate,
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
    onFilterChange() {
      this.current = null
      this.loadList()
    },
    resetFilter() {
      this.filterDates = []
      this.filterKeyword = ''
      this.onFilterChange()
    },
    loadList() {
      this.loadingList = true
      const [from, to] = Array.isArray(this.filterDates) ? this.filterDates : []
      adminListRevisions(
        { status: this.status, from, to, keyword: this.filterKeyword },
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

    // ---------- 超管改判 ----------
    async reapprove() {
      try {
        await ElMessageBox.confirm(
          '将把这篇被驳回的投稿发布上线；若目标页面在驳回后被编辑过，其当前内容会被本投稿覆盖（可先在「当前线上」视图核对）。确认改判通过？',
          '改判通过', { type: 'warning', confirmButtonText: '通过并发布', cancelButtonText: '取消' })
      } catch { return }
      this.acting = true
      adminReapproveRevision(this.current.id,
        () => { this.acting = false; ElMessage.success('已改判通过并发布'); this.afterAction() },
        (m) => { this.acting = false; ElMessage.error(m || '操作失败') })
    },
    async revokeApproved() {
      let comment = ''
      try {
        const { value } = await ElMessageBox.prompt(
          '将撤销这次通过：能回滚则回滚到上一通过版本，首篇内容则移入回收站。请输入撤销原因（投稿人可见）',
          '撤销通过', { confirmButtonText: '确认撤销', cancelButtonText: '取消', inputType: 'textarea' })
        comment = value || ''
      } catch { return }
      this.acting = true
      adminRevokeRevision(this.current.id, comment,
        (d) => {
          this.acting = false
          const msgMap = {
            ROLLED_BACK: '已撤销：页面已回滚到上一通过版本',
            PAGE_DELETED: '已撤销：页面已移入回收站（页面管理可恢复）',
            CONTENT_KEPT: '已撤销：无可回滚快照，页面内容保留，请到页面管理手工调整',
          }
          ElMessage.success(msgMap[d && d.pageAction] || '已撤销')
          this.afterAction()
        },
        (m) => { this.acting = false; ElMessage.error(m || '操作失败') })
    },
    async editComment() {
      let comment = ''
      try {
        const { value } = await ElMessageBox.prompt('修改驳回原因（投稿人可见并会收到通知）', '修改驳回原因', {
          confirmButtonText: '保存', cancelButtonText: '取消', inputType: 'textarea',
          inputValue: this.current.reviewComment || '',
        })
        comment = value || ''
      } catch { return }
      this.acting = true
      const id = this.current.id
      adminUpdateRevisionComment(id, comment,
        () => { this.acting = false; ElMessage.success('已更新'); this.openDetail(id); this.loadList() },
        (m) => { this.acting = false; ElMessage.error(m || '操作失败') })
    },
    async purgeRevisionRecord() {
      const isApproved = this.current.status === 'APPROVED'
      try {
        await ElMessageBox.confirm(
          isApproved
            ? '将永久删除这条投稿记录，不可恢复！这会减少作者的贡献榜计数，并丢失该页面的一版回滚快照（线上页面内容不受影响）。'
            : '将永久删除这条投稿记录，不可恢复！',
          '彻底删除记录',
          { type: 'error', confirmButtonText: '永久删除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' })
      } catch { return }
      this.acting = true
      adminPurgeRevision(this.current.id,
        () => { this.acting = false; ElMessage.success('记录已删除'); this.afterAction() },
        (m) => { this.acting = false; ElMessage.error(m || '删除失败') })
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
  font-size: 13px;
  font-weight: 600;
}
.ac-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 36px;
  padding: 0 12px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  text-align: left;
  cursor: pointer;
  transition: background var(--dur) ease, color var(--dur) ease;
}
.ac-item:hover { background: var(--bg-subtle); color: var(--text-primary); }
/* 选中项与文档侧栏一致：强调色文字 + 浅强调底，不再叠加左侧色条 */
.ac-item.active {
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 600;
}
.ac-item .el-icon { color: var(--text-muted); font-size: 16px; }
.ac-item.active .el-icon { color: var(--accent); }
.ac-main { min-width: 0; }
.admin-page { width: 100%; }
.rv-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 24px; flex-wrap: wrap; }
.rv-head h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 700;
  line-height: var(--lh-tight);
  letter-spacing: 0;
  color: var(--text-primary);
}
/* 状态筛选：小圆角分段按钮，选中项用白底 + 1px 描边区分，不用投影 */
.seg {
  display: flex;
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-subtle);
}
.seg button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 30px;
  padding: 0 14px;
  border: none;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background var(--dur) ease, color var(--dur) ease;
}
.seg button:hover { color: var(--text-primary); }
.seg button.active {
  background: var(--bg-surface);
  color: var(--text-primary);
  box-shadow: 0 0 0 1px var(--border);
}
.seg-count {
  display: inline-block;
  min-width: 18px;
  margin-left: 6px;
  padding: 0 5px;
  border-radius: var(--radius-xs);
  background: var(--bg-hover);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 500;
  line-height: 18px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.seg button.active .seg-count { background: var(--accent-soft); color: var(--accent); }
.dt-review { color: var(--text-secondary); font-size: 13px; }

.rv-filter {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}
.rv-filter .rv-filter-kw { width: 260px; max-width: 100%; }
@media (max-width: 768px) {
  .rv-filter { gap: 8px; }
  .rv-filter .rv-filter-kw { width: 100%; }
}

.mobile-admin-notice {
  display: none;
  padding: 12px 16px;
  margin-bottom: 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-subtle);
}
.mobile-admin-notice strong { color: var(--text-primary); font-size: 14px; font-weight: 600; }
.mobile-admin-notice span { color: var(--text-secondary); font-size: 13px; }

.rv-body { display: grid; grid-template-columns: clamp(280px, 22vw, 360px) minmax(0, 1fr); gap: 20px; align-items: start; }
.rv-body.list-collapsed { grid-template-columns: minmax(0, 1fr); }
.rv-list {
  border: 1px solid var(--border); border-radius: var(--radius);
  overflow: hidden auto; background: var(--bg-surface);
  max-height: calc(100vh - 180px);
  position: sticky; top: calc(var(--header-height) + 20px);
}
.rv-list ul { list-style: none; margin: 0; padding: 0; }
.rv-list li { border-bottom: 1px solid var(--border); }
.rv-list li:last-child { border-bottom: none; }
.rv-row {
  display: block;
  width: 100%;
  padding: 12px 16px;
  border: none;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background var(--dur) ease;
}
/* 焦点环画在行内侧，不被列表容器的 overflow 裁掉 */
.rv-row:focus-visible { outline-offset: -2px; }
.rv-row:hover { background: var(--bg-subtle); }
.rv-row.active { background: var(--accent-soft); box-shadow: inset 2px 0 0 var(--accent); }
.li-top { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.li-title { font-weight: 600; color: var(--text-primary); font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.li-meta { display: flex; justify-content: space-between; gap: 10px; font-size: 12px; color: var(--text-muted); }
.li-meta span:first-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.li-meta span:last-child { flex-shrink: 0; font-variant-numeric: tabular-nums; }

.rv-detail { border: 1px solid var(--border); border-radius: var(--radius); background: var(--bg-surface); min-height: 64vh; overflow: hidden; }
.placeholder {
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px;
  min-height: 64vh; padding: 24px; text-align: center;
}
.placeholder strong { color: var(--text-primary); font-size: 16px; font-weight: 600; }
.placeholder p { margin: 0; color: var(--text-secondary); font-size: 14px; }
.dt-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; padding: 22px 24px; border-bottom: 1px solid var(--border); flex-wrap: wrap; }
.dt-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  line-height: var(--lh-tight);
  letter-spacing: 0;
  color: var(--text-primary);
  overflow-wrap: anywhere;
}
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
.mobile-detail-back:hover { color: var(--text-primary); }
.dt-meta { margin: 8px 0 0; font-size: 13px; color: var(--text-secondary); display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.dt-meta code { background: var(--bg-subtle); padding: 1px 6px; border-radius: var(--radius-xs); font-size: 12px; overflow-wrap: anywhere; }
.dt-actions { display: flex; gap: 10px; flex-shrink: 0; }
.btn-approve, .btn-reject, .btn-ghost-sm {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  /* 只过渡颜色类属性，尺寸和阴影变化不做动画 */
  transition: background var(--dur) ease, color var(--dur) ease, border-color var(--dur) ease;
}
.btn-approve, .btn-reject { min-height: 36px; padding: 0 16px; }
/* 通过 / 驳回用状态色而不是主强调色：审核页的「主操作」语义就是这两个结果。
   这里不用 color-mix()：部分内置浏览器不认，含 var() 的值会整条失效退回初始值，
   悬停时「通过」的底色会变透明、白字直接看不见。只用两套主题都定义好的状态令牌对 */
.btn-approve { background: var(--success); color: var(--bg-page); border: 1px solid var(--success); }
/* 没有「深一号的绿」令牌，悬停换成浅底 + 绿字 + 绿边，与「驳回」的悬停同一套写法 */
.btn-approve:hover:not(:disabled) { background: var(--success-soft); color: var(--success); }
.btn-reject {
  background: transparent;
  color: var(--danger);
  border: 1px solid var(--border-strong);
}
.btn-reject:hover:not(:disabled) { background: var(--danger-soft); border-color: var(--danger); }
.dt-status { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.dt-status .btn-approve, .dt-status .btn-reject { min-height: 30px; padding: 0 12px; font-size: 13px; }
.btn-ghost-sm {
  min-height: 30px;
  padding: 0 12px;
  border: 1px solid var(--border-strong);
  background: var(--bg-surface);
  color: var(--text-body);
  font-size: 13px;
}
.btn-ghost-sm:hover:not(:disabled) { border-color: var(--text-muted); color: var(--text-primary); }
.btn-ghost-sm.danger {
  background: transparent;
  color: var(--danger);
}
.btn-ghost-sm.danger:hover:not(:disabled) {
  background: var(--danger-soft);
  color: var(--danger);
  border-color: var(--danger);
}
.btn-approve:disabled, .btn-reject:disabled, .btn-ghost-sm:disabled { opacity: 0.55; cursor: not-allowed; }

/* 类型 / 状态徽标：只用状态令牌对，亮暗主题由令牌切换 */
.rev-type, .status {
  display: inline-block;
  flex-shrink: 0;
  padding: 0 6px;
  border-radius: var(--radius-xs);
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
}
.t-create { background: var(--success-soft); color: var(--success); }
.t-update { background: var(--accent-soft); color: var(--accent); }
.s-approved { background: var(--success-soft); color: var(--success); }
.s-rejected { background: var(--danger-soft); color: var(--danger); }
.s-pending { background: var(--warning-soft); color: var(--warning); }
.mobile-review-lock {
  padding: 6px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
}
.rejection-note {
  display: flex;
  gap: 12px;
  padding: 12px 24px;
  border-bottom: 1px solid var(--border);
  background: var(--danger-soft);
  color: var(--danger);
  font-size: 13px;
}
.rejection-note strong { flex-shrink: 0; font-weight: 600; }
.rejection-note p { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }

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
  gap: 2px;
  padding: 3px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
}
.review-tabs button,
.list-toggle {
  min-height: 28px;
  border: 0;
  border-radius: var(--radius-xs);
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background var(--dur) ease, color var(--dur) ease;
}
.review-tabs button { padding: 0 12px; }
.review-tabs button:hover { color: var(--text-primary); }
.review-tabs button.active {
  background: var(--accent-soft);
  color: var(--accent);
}
.list-toggle { padding: 0 10px; }
.list-toggle:hover { color: var(--text-primary); background: var(--bg-hover); }
.review-canvas { min-width: 0; }
.preview-pane { min-width: 0; }
.pane-head {
  padding: 10px 18px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  border-bottom: 1px solid var(--border);
  background: var(--bg-subtle);
}
.pane-body { padding: 24px 28px; min-height: 48vh; overflow: auto; }
.muted { color: var(--text-muted); }
.pad { padding: 16px; }

@media (max-width: 900px) {
  .rv-body { grid-template-columns: 1fr; }
}

@media (max-width: 860px) {
  .admin-console { grid-template-columns: 1fr; gap: 16px; }
  .ac-nav { position: static; flex-direction: row; overflow-x: auto; }
  .ac-title { display: none; }
  .ac-item { flex: 1 0 auto; justify-content: center; white-space: nowrap; }
  /* 横排时改成底部 2px 下划线，与顶栏导航的选中样式一致 */
  .ac-item.active { box-shadow: inset 0 -2px 0 var(--accent); }
}

@media (max-width: 768px) {
  .admin-console { padding: 20px 16px 48px; }
  .rv-head { align-items: flex-start; margin-bottom: 16px; }
  .rv-head h1 { width: 100%; }
  .seg { width: 100%; }
  .seg button { flex: 1; min-height: 34px; padding-inline: 8px; }
  .mobile-admin-notice { display: flex; flex-direction: column; gap: 3px; }
  .rv-list { position: static; max-height: none; }
  .rv-row { padding: 14px 16px; }
  .rv-detail { min-height: 0; }
  .placeholder { min-height: 240px; }
  .dt-head { padding: 18px 16px; }
  .mobile-detail-back { display: inline-flex; min-height: 32px; align-items: center; }
  .review-toolbar { align-items: stretch; padding: 10px; }
  .review-tabs { width: 100%; overflow-x: auto; }
  .review-tabs button { flex: 1; min-height: 32px; white-space: nowrap; }
  .pane-body { max-height: none; padding: 18px 16px; }
}
</style>
