<template>
  <el-drawer
    v-model="visible"
    class="revision-drawer"
    :title="drawerTitle"
    :size="drawerSize"
    append-to-body
  >
    <div class="history-shell">
      <p class="history-note">仅展示已经审核并公开发布的版本，不包含待审核内容与审核信息。</p>

      <div v-if="historyLoading" class="history-loading">
        <el-skeleton :rows="7" animated />
      </div>

      <div v-else-if="historyError" class="history-state">
        <el-empty :description="historyError">
          <el-button type="primary" @click="loadHistory">重新加载</el-button>
        </el-empty>
      </div>

      <div v-else-if="!revisions.length" class="history-state">
        <el-empty description="此页面暂无可公开的版本记录" />
      </div>

      <div v-else class="history-layout">
        <aside class="revision-list" aria-label="公开版本列表">
          <button
            v-for="(revision, index) in revisions"
            :key="revision.id"
            type="button"
            class="revision-item"
            :class="{ active: revision.id === selectedId }"
            :aria-pressed="revision.id === selectedId"
            @click="selectRevision(revision)"
          >
            <span class="revision-topline">
              <strong>{{ versionLabel(revision, index) }}</strong>
              <el-tag v-if="revision.current" size="small" effect="plain" type="success">当前</el-tag>
            </span>
            <span class="revision-title">{{ revision.title || pageTitle }}</span>
            <span class="revision-summary">{{ revision.summary || sourceLabel(revision.sourceType) }}</span>
            <span class="revision-meta">
              {{ authorLabel(revision) }} · {{ formatDate(revision.createdAt) }}
            </span>
          </button>
        </aside>

        <section class="revision-detail" aria-live="polite">
          <div v-if="detailLoading" class="detail-loading">
            <el-skeleton :rows="9" animated />
          </div>

          <div v-else-if="detailError" class="history-state compact">
            <el-empty :description="detailError">
              <el-button type="primary" @click="retryDetail">重新加载</el-button>
            </el-empty>
          </div>

          <template v-else-if="detail">
            <header class="detail-header">
              <div>
                <h3>{{ selectedVersionTitle }}</h3>
                <p>
                  {{ authorLabel(detail) }} 发布于 {{ formatDate(detail.createdAt) }}
                </p>
              </div>
              <div class="detail-actions">
                <el-tag v-if="detail.current" effect="plain" type="success">当前版本</el-tag>
                <el-button
                  v-if="userStore.isSuperAdmin && !detail.current"
                  type="danger"
                  link
                  size="small"
                  :loading="purgeLoading"
                  @click="purgeSelectedVersion"
                >删除此历史版本</el-button>
              </div>
            </header>

            <div v-if="changedFieldLabels.length" class="changed-fields">
              <span>本版本变更</span>
              <el-tag
                v-for="field in changedFieldLabels"
                :key="field"
                size="small"
                effect="plain"
              >{{ field }}</el-tag>
            </div>

            <MarkdownDiff
              v-if="contentChanged"
              :before="beforeContent"
              :after="afterContent"
              :before-title="beforeTitle"
              :after-title="selectedVersionTitle"
              aria-label="公开版本前后 Markdown 差异"
            />
            <div v-else class="metadata-only-state">
              <strong>正文没有变化</strong>
              <span>{{ detail.summary || '本版本仅调整了页面信息或公开状态。' }}</span>
            </div>
          </template>
        </section>
      </div>
    </div>
  </el-drawer>
</template>

<script>
import { ElMessage, ElMessageBox } from 'element-plus'
import MarkdownDiff from '@/components/MarkdownDiff.vue'
import {
  adminPurgePageVersion,
  getPageRevisionHistory,
  getPageRevisionHistoryDetail,
} from '@/net/index.js'
import { useUserStore } from '@/store/userStore.js'

const FIELD_LABELS = {
  title: '标题',
  categorySlug: '分类',
  icon: '图标',
  description: '简介',
  tags: '标签',
  content: '正文',
}

export default {
  name: 'PageRevisionHistory',
  components: { MarkdownDiff },
  props: {
    docPath: { type: String, required: true },
    pageTitle: { type: String, default: '' },
  },
  data() {
    return {
      userStore: useUserStore(),
      visible: false,
      isMobile: false,
      revisions: [],
      selectedId: null,
      detail: null,
      historyLoading: false,
      detailLoading: false,
      historyError: '',
      detailError: '',
      purgeLoading: false,
      historyRequestKey: 0,
      detailRequestKey: 0,
    }
  },
  computed: {
    drawerTitle() {
      return this.pageTitle ? `版本历史 · ${this.pageTitle}` : '版本历史'
    },
    drawerSize() {
      return this.isMobile ? '100%' : 'min(1040px, 92vw)'
    },
    selectedRevision() {
      return this.revisions.find((item) => item.id === this.selectedId) || null
    },
    selectedVersionTitle() {
      const revision = this.detail || this.selectedRevision || {}
      const index = this.revisions.findIndex((item) => item.id === this.selectedId)
      return this.versionLabel(revision, index < 0 ? 0 : index)
    },
    beforeContent() {
      return this.detail?.beforeContent ?? this.detail?.previousContent ?? ''
    },
    afterContent() {
      return this.detail?.afterContent ?? this.detail?.content ?? ''
    },
    beforeTitle() {
      if (!this.beforeContent) return '此前无公开快照'
      return '上一公开版本'
    },
    changedFieldLabels() {
      const fields = Array.isArray(this.detail?.changedFields)
        ? this.detail.changedFields
        : []
      return fields.map((field) => FIELD_LABELS[field] || field)
    },
    contentChanged() {
      if (Array.isArray(this.detail?.changedFields)) {
        return this.detail.changedFields.includes('content')
      }
      return this.beforeContent !== this.afterContent
    },
  },
  watch: {
    docPath() {
      this.reset()
      if (this.visible) this.loadHistory()
    },
  },
  mounted() {
    this.updateViewport()
    window.addEventListener('resize', this.updateViewport)
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.updateViewport)
  },
  methods: {
    open() {
      this.visible = true
      this.loadHistory()
    },
    reset() {
      this.historyRequestKey++
      this.detailRequestKey++
      this.revisions = []
      this.selectedId = null
      this.detail = null
      this.historyError = ''
      this.detailError = ''
      this.purgeLoading = false
      this.historyLoading = false
      this.detailLoading = false
    },
    updateViewport() {
      this.isMobile = window.innerWidth <= 767
    },
    loadHistory() {
      const requestKey = ++this.historyRequestKey
      this.detailRequestKey++
      this.historyLoading = true
      this.historyError = ''
      this.revisions = []
      this.selectedId = null
      this.detail = null
      this.detailLoading = false
      this.detailError = ''
      getPageRevisionHistory(
        this.docPath,
        (data) => {
          if (requestKey !== this.historyRequestKey) return
          this.historyLoading = false
          this.revisions = Array.isArray(data) ? data : []
          if (this.revisions.length) this.selectRevision(this.revisions[0])
        },
        (message) => {
          if (requestKey !== this.historyRequestKey) return
          this.historyLoading = false
          this.historyError = message || '版本历史加载失败'
        },
      )
    },
    selectRevision(revision) {
      if (!revision || revision.id === null || revision.id === undefined) return
      this.selectedId = revision.id
      const requestKey = ++this.detailRequestKey
      this.detailLoading = true
      this.detailError = ''
      this.detail = null
      getPageRevisionHistoryDetail(
        revision.id,
        (data) => {
          if (requestKey !== this.detailRequestKey) return
          this.detailLoading = false
          this.detail = data || null
          if (!this.detail) this.detailError = '该版本详情不存在'
        },
        (message) => {
          if (requestKey !== this.detailRequestKey) return
          this.detailLoading = false
          this.detailError = message || '版本详情加载失败'
        },
      )
    },
    retryDetail() {
      if (this.selectedRevision) this.selectRevision(this.selectedRevision)
    },
    purgeSelectedVersion() {
      if (!this.userStore.isSuperAdmin || !this.detail || this.detail.current || this.purgeLoading) return
      const versionId = this.detail.id
      ElMessageBox.confirm(
        '删除后该版本及其旧正文将不再公开，且无法恢复。确定继续吗？',
        '删除历史版本',
        {
          confirmButtonText: '永久删除',
          cancelButtonText: '取消',
          type: 'warning',
        },
      ).then(() => {
        this.purgeLoading = true
        adminPurgePageVersion(
          versionId,
          () => {
            this.purgeLoading = false
            ElMessage.success('历史版本已删除')
            this.loadHistory()
          },
          (message) => {
            this.purgeLoading = false
            ElMessage.error(message || '删除失败')
          },
        )
      }).catch(() => {})
    },
    versionLabel(revision, index) {
      if (revision?.version !== null && revision?.version !== undefined) {
        return `版本 v${revision.version}`
      }
      return `版本 ${Math.max(this.revisions.length - index, 1)}`
    },
    sourceLabel(type) {
      if (type?.includes('CREATE')) return '创建页面'
      if (type?.includes('RESTORE')) return '恢复页面'
      if (type === 'ROLLBACK') return '回滚到上一公开版本'
      if (type === 'MIGRATION') return '历史基线版本'
      if (type?.startsWith('ADMIN')) return '管理员更新'
      return '更新页面'
    },
    authorLabel(revision) {
      if (revision?.sourceType === 'ROLLBACK' || revision?.sourceType?.startsWith('ADMIN')) {
        return 'Wiki 管理员'
      }
      return revision?.authorName || revision?.authorNickname || 'Wiki 贡献者'
    },
    formatDate(value) {
      if (!value) return '未知时间'
      const date = new Date(value)
      if (Number.isNaN(date.getTime())) return '未知时间'
      return new Intl.DateTimeFormat('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      }).format(date)
    },
  },
}
</script>

<style scoped>
.history-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.history-note {
  flex: 0 0 auto;
  margin: -4px 0 16px;
  padding: 10px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-subtle);
  color: var(--text-muted);
  font-size: 12.5px;
  line-height: 1.55;
}

.history-loading,
.history-state {
  padding: 24px;
}

.history-state.compact { padding: 8px; }

.history-layout {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  flex: 1;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-surface);
}

.revision-list {
  overflow-y: auto;
  border-right: 1px solid var(--border);
  background: var(--bg-subtle);
}

.revision-item {
  display: flex;
  flex-direction: column;
  width: 100%;
  gap: 5px;
  padding: 14px 16px;
  border: 0;
  border-bottom: 1px solid var(--border);
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background-color .18s ease, box-shadow .18s ease;
}

.revision-item:hover { background: var(--bg-hover); }
.revision-item.active {
  background: var(--bg-surface);
  box-shadow: inset 3px 0 0 var(--brand);
}

.revision-topline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--text-primary);
  font-size: 13px;
}

.revision-title,
.revision-summary,
.revision-meta {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.revision-title { color: var(--text-secondary); font-size: 12.5px; }
.revision-summary { color: var(--text-muted); font-size: 12px; }
.revision-meta { color: var(--text-muted); font-size: 11.5px; }

.revision-detail {
  min-width: 0;
  overflow: auto;
}

.detail-loading { padding: 24px; }

.detail-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--border);
}

.detail-header h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 16px;
}

.detail-header p {
  margin: 5px 0 0;
  color: var(--text-muted);
  font-size: 12.5px;
}

.detail-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.changed-fields {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  padding: 11px 18px;
  border-bottom: 1px solid var(--border);
  background: var(--bg-subtle);
}
.changed-fields > span:first-child {
  margin-right: 2px;
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 600;
}

.metadata-only-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 220px;
  gap: 8px;
  padding: 32px;
  color: var(--text-muted);
  text-align: center;
}
.metadata-only-state strong {
  color: var(--text-primary);
  font-size: 15px;
}
.metadata-only-state span {
  max-width: 460px;
  font-size: 13px;
  line-height: 1.6;
}

@media (max-width: 767px) {
  .history-note { margin-top: 0; }
  .history-layout {
    display: flex;
    flex-direction: column;
    overflow: auto;
  }
  .revision-list {
    display: flex;
    flex: 0 0 auto;
    max-height: 210px;
    overflow-x: auto;
    overflow-y: hidden;
    border-right: 0;
    border-bottom: 1px solid var(--border);
  }
  .revision-item {
    flex: 0 0 215px;
    border-right: 1px solid var(--border);
    border-bottom: 0;
  }
  .revision-item.active {
    box-shadow: inset 0 -3px 0 var(--brand);
  }
  .revision-detail {
    flex: 1;
    overflow: visible;
  }
  .detail-header { align-items: flex-start; }
  .detail-actions { justify-content: flex-start; }
}
</style>
