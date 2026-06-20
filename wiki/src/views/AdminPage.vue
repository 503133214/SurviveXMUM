<template>
  <div class="admin-page">
    <header class="ad-head">
      <h1>管理后台 · 投稿审核</h1>
      <div class="seg">
        <button v-for="s in tabs" :key="s.key" :class="{ active: status === s.key }" @click="switchStatus(s.key)">
          {{ s.label }}
        </button>
      </div>
    </header>

    <div class="ad-body">
      <!-- 列表 -->
      <aside class="ad-list">
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
      <main class="ad-detail">
        <div v-if="!current" class="placeholder">
          <p>👈 从左侧选择一条投稿进行审核</p>
        </div>
        <template v-else>
          <div class="dt-head">
            <div>
              <h2>{{ current.title }}</h2>
              <p class="dt-meta">
                <span class="rev-type" :class="current.type === 'CREATE' ? 't-create' : 't-update'">
                  {{ current.type === 'CREATE' ? '新建' : '修改' }}
                </span>
                路径 <code>{{ current.targetPath }}</code> · 投稿人 {{ current.authorEmail }}
              </p>
            </div>
            <div v-if="current.status === 'PENDING'" class="dt-actions">
              <button class="btn-reject" :disabled="acting" @click="reject">驳回</button>
              <button class="btn-approve" :disabled="acting" @click="approve">通过并发布</button>
            </div>
            <div v-else class="dt-status">
              <span class="status" :class="`s-${current.status.toLowerCase()}`">{{ statusText(current.status) }}</span>
            </div>
          </div>

          <div class="compare" :class="{ single: current.type === 'CREATE' }">
            <div v-if="current.type === 'UPDATE'" class="pane">
              <div class="pane-head">当前线上内容</div>
              <div class="pane-body markdown-scope">
                <MarkdownRenderer v-if="current.currentContent" :content="current.currentContent" :base-path="baseDir" />
                <p v-else class="muted">（原页面无内容或已删除）</p>
              </div>
            </div>
            <div class="pane highlight">
              <div class="pane-head">投稿内容（预览）</div>
              <div class="pane-body markdown-scope">
                <MarkdownRenderer :content="current.content || ''" :base-path="baseDir" />
              </div>
            </div>
          </div>
        </template>
      </main>
    </div>
  </div>
</template>

<script>
import { markRaw } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import MarkdownRenderer from '@/components/MarkdownRenderer.vue'
import {
  adminListRevisions, adminGetRevision, adminApproveRevision, adminRejectRevision,
} from '@/net/index.js'

export default {
  name: 'AdminPage',
  components: { MarkdownRenderer: markRaw(MarkdownRenderer) },
  data() {
    return {
      status: 'PENDING',
      tabs: [
        { key: 'PENDING', label: '待审核' },
        { key: 'APPROVED', label: '已通过' },
        { key: 'REJECTED', label: '已驳回' },
      ],
      list: [],
      current: null,
      loadingList: false,
      acting: false,
    }
  },
  computed: {
    currentLabel() { return this.tabs.find((t) => t.key === this.status)?.label || '' },
    baseDir() {
      const p = this.current?.targetPath || ''
      return p.includes('/') ? p.slice(0, p.lastIndexOf('/')) : ''
    },
  },
  mounted() { this.loadList() },
  methods: {
    statusText(s) { return { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }[s] || s },
    fmt(iso) {
      if (!iso) return ''
      const d = new Date(iso)
      return Number.isNaN(d.getTime()) ? '' :
        `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
    },
    switchStatus(s) { this.status = s; this.current = null; this.loadList() },
    loadList() {
      this.loadingList = true
      adminListRevisions(
        this.status,
        (data) => { this.list = data || []; this.loadingList = false },
        (msg) => { this.loadingList = false; ElMessage.error(msg || '加载失败') }
      )
    },
    openDetail(id) {
      adminGetRevision(id,
        (data) => { this.current = data },
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
    },
  },
}
</script>

<style scoped>
.admin-page { max-width: var(--maxw); margin: 0 auto; padding: 32px 24px 64px; }
.ad-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 24px; flex-wrap: wrap; }
.ad-head h1 { font-size: 1.5rem; font-weight: 800; letter-spacing: -0.02em; margin: 0; color: var(--text-primary); }
.seg { display: flex; background: var(--bg-subtle); border-radius: 999px; padding: 4px; }
.seg button {
  border: none; background: transparent; padding: 8px 18px;
  font-size: 14px; font-weight: 600; color: var(--text-secondary);
  border-radius: 999px; cursor: pointer; transition: all .2s var(--ease-out);
}
.seg button.active { background: var(--bg-surface); color: var(--text-primary); box-shadow: var(--shadow-sm); }

.ad-body { display: grid; grid-template-columns: 320px 1fr; gap: 20px; align-items: start; }
.ad-list {
  border: 1px solid var(--border); border-radius: var(--radius);
  overflow: hidden; background: var(--bg-surface);
  max-height: 72vh; overflow-y: auto;
}
.ad-list ul { list-style: none; margin: 0; padding: 0; }
.ad-list li { padding: 14px 16px; border-bottom: 1px solid var(--border); cursor: pointer; transition: background .15s ease; }
.ad-list li:hover { background: var(--bg-hover); }
.ad-list li.active { background: var(--bg-subtle); box-shadow: inset 3px 0 0 var(--accent); }
.li-top { display: flex; align-items: center; gap: 8px; margin-bottom: 5px; }
.li-title { font-weight: 600; color: var(--text-primary); font-size: 14.5px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.li-meta { display: flex; justify-content: space-between; font-size: 12px; color: var(--text-muted); }

.ad-detail { border: 1px solid var(--border); border-radius: var(--radius); background: var(--bg-surface); min-height: 60vh; }
.placeholder { display: flex; align-items: center; justify-content: center; min-height: 60vh; color: var(--text-muted); }
.dt-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; padding: 22px 24px; border-bottom: 1px solid var(--border); flex-wrap: wrap; }
.dt-head h2 { margin: 0; font-size: 1.3rem; font-weight: 800; color: var(--text-primary); }
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

.compare { display: grid; grid-template-columns: 1fr 1fr; gap: 0; }
.compare.single { grid-template-columns: 1fr; }
.pane { min-width: 0; }
.pane + .pane { border-left: 1px solid var(--border); }
.pane.highlight { background: var(--bg-page); }
.pane-head { padding: 10px 18px; font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: .04em; color: var(--text-muted); border-bottom: 1px solid var(--border); background: var(--bg-subtle); }
.pane-body { padding: 18px 22px; max-height: 60vh; overflow-y: auto; }
.muted { color: var(--text-muted); }
.pad { padding: 16px; }

@media (max-width: 900px) {
  .ad-body { grid-template-columns: 1fr; }
  .compare { grid-template-columns: 1fr; }
  .pane + .pane { border-left: none; border-top: 1px solid var(--border); }
}
</style>
