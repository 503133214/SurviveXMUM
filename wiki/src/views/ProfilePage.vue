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
          <p class="pf-email">
            <span class="pf-email-text">{{ email }}</span>
            <button
              v-if="!editingName"
              ref="editNameBtn"
              type="button"
              class="pf-edit-name"
              @click="startEditName"
            >修改昵称</button>
          </p>
        </div>
      </div>
      <div class="pf-actions">
        <router-link v-if="isAdmin" to="/admin" class="action-link secondary">
          <Settings :size="16" :stroke-width="1.75" aria-hidden="true" />
          <span>管理后台</span>
        </router-link>
        <router-link to="/edit" class="action-link primary">
          <PenLine :size="16" :stroke-width="1.75" aria-hidden="true" />
          <span>写文章</span>
        </router-link>
      </div>
    </header>

    <!-- 昵称就是公开署名：没设时后端在评论、贡献榜等处公开完整校园邮箱（前缀是学号），
         所以要能在这里自己改。只说它真正影响的范围：已发布版本的署名是发布时的快照，不会跟着变 -->
    <form v-if="editingName" class="pf-name-form" @submit.prevent="saveNickname" @keydown.esc="cancelEditName">
      <label class="pf-form-label" for="pf-nickname">公开昵称</label>
      <div class="pf-form-row">
        <el-input
          id="pf-nickname"
          ref="nameInput"
          v-model="nicknameDraft"
          class="pf-form-input"
          maxlength="30"
          show-word-limit
          placeholder="留空则显示完整校园邮箱"
          aria-describedby="pf-nickname-hint"
        />
        <div class="pf-form-actions">
          <el-button type="primary" native-type="submit" :loading="savingName">保存</el-button>
          <el-button @click="cancelEditName">取消</el-button>
        </div>
      </div>
      <p id="pf-nickname-hint" class="pf-form-hint">
        评论、贡献榜和贡献者页显示这个昵称；不设昵称时显示你的完整校园邮箱。改名不会更新已发布版本里的署名。
      </p>
    </form>

    <!-- 未完成草稿：有才显示；个人中心是续写最自然的入口 -->
    <section v-if="drafts.length" class="pf-section">
      <div class="sec-head">
        <h2>我的草稿 <span class="count">{{ drafts.length }}</span></h2>
      </div>
      <!-- 行本身是 router-link（键盘 Tab 可达、回车打开）；「删除」放在链接外面做兄弟元素，
           避免按钮嵌在 <a> 里，也不会在删除时顺带触发跳转 -->
      <ul class="rev-list">
        <li v-for="d in drafts" :key="d.id" class="rev-item">
          <router-link :to="draftTo(d)" class="rev-row">
            <span class="rev-mark" :class="d.type === 'CREATE' ? 't-create' : 't-update'">
              {{ d.type === 'CREATE' ? '新' : '改' }}
            </span>
            <span class="rev-main">
              <span class="rev-title">{{ d.title || '（未命名草稿）' }}</span>
              <span class="rev-meta">
                <span>{{ d.type === 'CREATE' ? '新文章草稿' : '编辑草稿' }}</span>
                <template v-if="d.targetPath">
                  <span class="meta-separator" aria-hidden="true"></span>
                  <code class="rev-path">{{ d.targetPath }}</code>
                </template>
              </span>
            </span>
            <span class="rev-date">{{ d.updatedAt }}</span>
          </router-link>
          <el-button class="rev-remove" link type="danger" size="small" @click="removeDraft(d)">删除</el-button>
        </li>
      </ul>
    </section>

    <!-- 我的讨论：只列本人发过的，自删的不再出现 -->
    <section v-if="comments.length" class="pf-section">
      <div class="sec-head">
        <h2>我的讨论 <span class="count">{{ comments.length }}</span></h2>
      </div>
      <ul class="rev-list">
        <li v-for="c in comments" :key="c.id" class="rev-item">
          <router-link :to="`/docs/${c.path}`" class="rev-row">
            <span class="rev-mark" :class="c.reply ? 't-update' : 't-create'">
              {{ c.reply ? '复' : '评' }}
            </span>
            <span class="rev-main">
              <span class="rev-title cm-text">{{ c.content }}</span>
              <span class="rev-meta">
                <span>{{ c.pageTitle }}</span>
                <span class="meta-separator" aria-hidden="true"></span>
                <span>{{ c.reply ? '回复' : '主楼' }}</span>
                <template v-if="c.status === 'HIDDEN'">
                  <span class="meta-separator" aria-hidden="true"></span>
                  <span class="cm-hidden">
                    已被管理员隐藏<template v-if="c.hiddenReason">：{{ c.hiddenReason }}</template>
                  </span>
                </template>
              </span>
            </span>
            <span class="rev-date">{{ c.createdAt }}</span>
          </router-link>
          <el-button
            v-if="c.status === 'VISIBLE'"
            class="rev-remove"
            link type="danger" size="small"
            @click="removeComment(c)"
          >删除</el-button>
        </li>
      </ul>
    </section>

    <section class="pf-section">
      <div class="sec-head">
        <h2>我的投稿 <span class="count">{{ revisions.length }}</span></h2>
      </div>

      <div v-if="loading" class="loading-state">加载投稿中…</div>
      <el-empty v-else-if="!revisions.length" description="还没有投稿，去写一篇吧" />

      <!-- 已通过的投稿直接链到文章，是 router-link；其余状态在本页弹出详情，是真正的 button。
           两者都原生支持键盘（链接回车、按钮回车 / 空格），不用再手写 role 和按键监听 -->
      <ul v-else class="rev-list">
        <li v-for="r in revisions" :key="r.id" class="rev-item">
          <component
            :is="r.status === 'APPROVED' ? 'router-link' : 'button'"
            v-bind="revisionRowProps(r)"
            class="rev-row"
          >
            <span class="rev-mark" :class="r.type === 'CREATE' ? 't-create' : 't-update'">
              {{ r.type === 'CREATE' ? '新' : '改' }}
            </span>
            <span class="rev-main">
              <span class="rev-title">{{ r.title }}</span>
              <span class="rev-meta">
                <span>{{ r.type === 'CREATE' ? '新建文章' : '修改文章' }}</span>
                <span class="meta-separator" aria-hidden="true"></span>
                <code class="rev-path">{{ r.targetPath }}</code>
              </span>
              <span v-if="r.status === 'REJECTED' && r.reviewComment" class="rev-reason">
                {{ r.reviewComment }}
              </span>
            </span>
            <span class="rev-side">
              <span class="status" :class="`s-${r.status.toLowerCase()}`">{{ statusText(r.status) }}</span>
              <span class="rev-date">{{ fmt(r.createdAt) }}</span>
            </span>
          </component>
        </li>
      </ul>
    </section>

    <el-dialog
      v-model="detailVisible"
      :title="selectedRevision?.title || '投稿详情'"
      width="min(820px, 92vw)"
      destroy-on-close
    >
      <div v-if="detailLoading" class="loading-state">加载投稿中…</div>
      <div v-else-if="selectedRevision" class="revision-detail">
        <div class="detail-meta">
          <span class="status" :class="`s-${selectedRevision.status.toLowerCase()}`">
            {{ statusText(selectedRevision.status) }}
          </span>
          <code>{{ selectedRevision.targetPath }}</code>
        </div>
        <div v-if="selectedRevision.status === 'REJECTED'" class="detail-reason">
          <strong>驳回原因</strong>
          <p>{{ selectedRevision.reviewComment || '管理员未填写驳回原因' }}</p>
        </div>
        <MarkdownRenderer :content="selectedRevision.content || ''" embedded />
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { ElMessage, ElMessageBox } from 'element-plus'
import { PenLine, Settings } from 'lucide-vue-next'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import { getMyRevision, getMyRevisions, listDrafts, deleteDraft, listMyComments, deleteComment, updateProfile } from '@/net/index.js'
import { useUserStore } from '@/store/userStore.js'

export default {
  name: 'ProfilePage',
  components: { MarkdownRenderer, PenLine, Settings },
  data() {
    return {
      userStore: useUserStore(),
      revisions: [],
      drafts: [],
      comments: [],
      loading: true,
      detailVisible: false,
      detailLoading: false,
      selectedRevision: null,
      editingName: false,
      nicknameDraft: '',
      savingName: false,
    }
  },
  computed: {
    email() { return this.userStore.userInfo?.userEmail || '' },
    nickname() { return this.userStore.userInfo?.nickname || this.userStore.username || '用户' },
    // 用 store 的 isAdmin（含 SUPER_ADMIN），否则超管看不到“管理后台”入口
    isAdmin() { return this.userStore.isAdmin },
    initial() { return (this.nickname || 'U').charAt(0).toUpperCase() },
  },
  mounted() {
    if (!this.userStore.userInfo) this.userStore.fetchUserInfo()
    getMyRevisions(
      (data) => { this.revisions = data || []; this.loading = false },
      (msg) => { this.loading = false; ElMessage.error(msg || '加载投稿失败') }
    )
    listDrafts((data) => { this.drafts = data || [] }, () => {})
    listMyComments((data) => { this.comments = data || [] }, () => {})
  },
  methods: {
    startEditName() {
      this.nicknameDraft = this.userStore.userInfo?.nickname || ''
      this.editingName = true
      this.$nextTick(() => this.$refs.nameInput?.focus())
    },
    cancelEditName() {
      this.editingName = false
      // 表单收起后焦点回到「修改昵称」，键盘用户不会被甩回页面顶部
      this.$nextTick(() => this.$refs.editNameBtn?.focus())
    },
    saveNickname() {
      if (this.savingName) return
      const name = this.nicknameDraft.trim()
      // 和后端同一条规则：带 @ 的署名只能是本人邮箱，防止用昵称冒充别人的学号
      if (name.includes('@')) return ElMessage.warning('昵称不能包含 @')
      this.savingName = true
      updateProfile(
        name,
        (info) => {
          this.savingName = false
          if (info) this.userStore.setUserInfo(info)
          else this.userStore.fetchUserInfo()
          ElMessage.success(name ? '昵称已保存' : '已清除昵称，公开处将显示完整校园邮箱')
          this.cancelEditName()
        },
        (message) => {
          this.savingName = false
          ElMessage.error(message || '保存失败')
        },
      )
    },
    statusText(s) {
      return { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回', REMOVED: '文章已删除' }[s] || s
    },
    // 已通过的投稿渲染成指向文章的 router-link，其余渲染成打开详情弹窗的按钮
    revisionRowProps(revision) {
      if (revision.status === 'APPROVED') return { to: `/docs/${revision.targetPath}` }
      return { type: 'button', onClick: () => this.openRevision(revision) }
    },
    openRevision(revision) {
      this.detailVisible = true
      this.detailLoading = true
      this.selectedRevision = { ...revision, content: '' }
      getMyRevision(
        revision.id,
        (data) => {
          this.selectedRevision = { ...data, status: revision.status }
          this.detailLoading = false
        },
        (msg) => {
          this.detailVisible = false
          this.detailLoading = false
          ElMessage.error(msg || '加载投稿详情失败')
        }
      )
    },
    fmt(iso) {
      if (!iso) return ''
      const d = new Date(iso)
      return Number.isNaN(d.getTime()) ? '' :
        `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    },
    async removeComment(c) {
      try {
        await ElMessageBox.confirm('确定删除这条讨论？删除后原帖只会留下一个占位。', '提示', { type: 'warning' })
      } catch { return }
      deleteComment(c.id, () => {
        ElMessage.success('已删除')
        this.comments = this.comments.filter((x) => x.id !== c.id)
      }, (m) => ElMessage.error(m || '删除失败'))
    },
    draftTo(d) {
      // 改已有文章的草稿回到该文章的编辑页，进入后走「发现草稿」恢复流程；
      // 新文章草稿没有路径，用 query 指明是哪一份。草稿 id 是雪花 ID，按字符串传
      if (d.type === 'UPDATE' && d.targetPath) return `/edit/${d.targetPath}`
      return { path: '/edit', query: { draft: String(d.id) } }
    },
    async removeDraft(d) {
      try {
        await ElMessageBox.confirm(`确定删除草稿「${d.title || '未命名草稿'}」？`, '提示', { type: 'warning' })
      } catch { return }
      deleteDraft(d.id, () => {
        ElMessage.success('已删除')
        this.drafts = this.drafts.filter((x) => x.id !== d.id)
      }, (m) => ElMessage.error(m || '删除失败'))
    },
  },
}
</script>

<style scoped>
.profile-page { width: 100%; max-width: 1040px; margin: 0 auto; padding: 40px 20px 64px; }
.pf-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
  padding: 0 0 32px;
  border-bottom: 1px solid var(--border);
}
.pf-identity {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}
/* 头像用中性底：页头里已经有「写文章」这一个强调色主按钮，不再并排第二块强调色；
   与贡献者主页的头像同一配色 */
.pf-avatar {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border-radius: var(--radius);
  background: var(--bg-hover);
  color: var(--text-primary);
  font-size: 24px;
  font-weight: 600;
  flex-shrink: 0;
}
.pf-copy { min-width: 0; }
.pf-name-row { display: flex; align-items: center; gap: 10px; min-width: 0; }
.pf-name {
  margin: 0;
  overflow: hidden;
  color: var(--text-primary);
  font-size: 24px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pf-email {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin: 4px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
}
.pf-email-text { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pf-edit-name {
  flex-shrink: 0;
  padding: 0;
  border: 0;
  background: none;
  color: var(--accent);
  font: inherit;
  cursor: pointer;
  transition: color var(--dur);
}
.pf-edit-name:hover { color: var(--accent-hover); text-decoration: underline; }
.pf-edit-name:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; border-radius: var(--radius-xs); }

.pf-name-form {
  margin-top: 20px;
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-surface);
}
.pf-form-label { display: block; margin-bottom: 8px; color: var(--text-primary); font-size: 14px; font-weight: 600; }
.pf-form-row { display: flex; align-items: center; gap: 8px; }
.pf-form-input { flex: 1; max-width: 360px; }
.pf-form-actions { display: flex; gap: 8px; flex-shrink: 0; }
.pf-form-actions .el-button + .el-button { margin-left: 0; }
.pf-form-hint { margin: 8px 0 0; color: var(--text-muted); font-size: 13px; line-height: 1.6; }
/* 徽标统一规格：0 6px 内边距、20px 行高、12px/500、2px 圆角，颜色只取令牌 */
.role-badge,
.count,
.rev-mark,
.status {
  display: inline-block;
  flex-shrink: 0;
  padding: 0 6px;
  border-radius: var(--radius-xs);
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
  white-space: nowrap;
}
.role-badge { background: var(--bg-subtle); color: var(--text-secondary); }

.pf-actions { display: flex; gap: 8px; flex-shrink: 0; }
.action-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 36px;
  padding: 0 14px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 500;
  transition: background-color var(--dur), border-color var(--dur), color var(--dur);
}
.action-link:hover { text-decoration: none; }
.action-link.primary { border-color: var(--accent); background: var(--accent); color: var(--accent-contrast); }
.action-link.primary:hover { border-color: var(--accent-hover); background: var(--accent-hover); }
.action-link.secondary { background: var(--bg-surface); color: var(--text-primary); }
.action-link.secondary:hover { border-color: var(--text-muted); }

.pf-section { padding-top: 32px; }
.sec-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.sec-head h2 { margin: 0; color: var(--text-primary); font-size: 18px; font-weight: 600; line-height: 1.3; }
.count {
  margin-left: 6px;
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-variant-numeric: tabular-nums;
  vertical-align: 2px;
}
.loading-state { padding: 24px 0; color: var(--text-muted); font-size: 13px; }

.rev-list {
  overflow: hidden;
  margin: 0;
  padding: 0;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  list-style: none;
}
/* 行容器只管底色和分隔线；可点的部分是里面的 .rev-row（链接或按钮），
   「删除」作为兄弟元素排在它右边，悬停整行一起换底色 */
.rev-item {
  display: flex;
  align-items: center;
  background: var(--bg-surface);
  transition: background-color var(--dur);
}
.rev-item + .rev-item { border-top: 1px solid var(--border); }
.rev-item:hover { background: var(--bg-subtle); }
.rev-row {
  display: grid;
  flex: 1;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  min-width: 0;
  min-height: 64px;
  padding: 12px 16px;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.rev-row:hover { text-decoration: none; }
/* 列表容器 overflow: hidden 会裁掉外扩的焦点框，所以收到行内 */
.rev-row:focus-visible { outline-offset: -2px; }
.rev-remove { flex-shrink: 0; margin-right: 16px; }
/* 类型徽标贴着标题第一行，而不是在整行里垂直居中 */
.rev-mark { align-self: start; margin-top: 1px; }
.t-create { background: var(--success-soft); color: var(--success); }
.t-update { background: var(--accent-soft); color: var(--accent); }
.rev-main { display: block; min-width: 0; }
.rev-title {
  display: block;
  overflow: hidden;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rev-meta { display: flex; align-items: center; gap: 8px; margin-top: 4px; color: var(--text-muted); font-size: 12px; }
.meta-separator { flex-shrink: 0; width: 3px; height: 3px; border-radius: 50%; background: var(--border-strong); }
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
/* 日期列定宽，状态长短不一时各行日期仍右对齐 */
.rev-side { display: grid; grid-template-columns: auto 80px; align-items: center; gap: 12px; }
.cm-text { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cm-hidden { color: var(--warning); }
.s-pending { background: var(--warning-soft); color: var(--warning); }
.s-approved { background: var(--success-soft); color: var(--success); }
.s-rejected { background: var(--danger-soft); color: var(--danger); }
.s-removed { background: var(--bg-subtle); color: var(--text-muted); }
.rev-date {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  color: var(--text-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.rev-reason { display: block; margin: 6px 0 0; color: var(--danger); font-size: 12px; }
.revision-detail { min-height: 160px; }
.detail-meta { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; color: var(--text-muted); }
.detail-reason {
  margin-bottom: 20px;
  padding: 12px 14px;
  /* 不再用 color-mix() 调半透明红边：部分内置浏览器不认，含 var() 的值会整条失效。
     浅红底加红字已经足够标出这块，两套主题都不需要额外描边 */
  border-radius: var(--radius);
  background: var(--danger-soft);
  color: var(--danger);
}
.detail-reason p { margin: 6px 0 0; }

@media (max-width: 720px) {
  .profile-page { padding: 24px 16px 48px; }
  .pf-head { align-items: stretch; flex-direction: column; gap: 20px; padding-bottom: 24px; }
  .pf-actions { width: 100%; }
  .action-link { flex: 1; height: 40px; }
  .pf-section { padding-top: 28px; }
  /* 文字按钮在手机上把可点区域撑到 44px 高，版面不变 */
  .pf-edit-name { padding: 12px 0; margin: -12px 0; }
  .pf-form-row { flex-direction: column; align-items: stretch; }
  .pf-form-input { max-width: none; }
  /* 输入框字号低于 16px 时 iOS 聚焦会放大整页 */
  .pf-form-input :deep(.el-input__wrapper) { min-height: 40px; }
  .pf-form-input :deep(.el-input__inner) { font-size: 16px; }
  .pf-form-actions .el-button { flex: 1; height: 40px; }
  /* 窄屏下状态和日期挪到标题下方，标题才有足够宽度 */
  .rev-row { grid-template-columns: auto minmax(0, 1fr); gap: 4px 12px; padding: 12px; }
  .rev-side,
  .rev-row > .rev-date {
    grid-column: 2;
  }
  .rev-side {
    grid-template-columns: auto 1fr;
    justify-content: start;
    gap: 10px;
  }
  .rev-date { justify-content: flex-start; }
  .rev-remove { margin-right: 12px; }
}

@media (max-width: 420px) {
  .pf-avatar { width: 48px; height: 48px; font-size: 20px; }
  .role-badge { display: none; }
  .rev-meta { max-width: 100%; }
}
</style>
