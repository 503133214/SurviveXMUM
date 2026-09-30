<template>
  <div class="changes-page">
    <header class="cg-head">
      <p class="cg-eyebrow">WIKI 正在生长</p>
      <h1>站点动态</h1>
      <p class="cg-sub">每一次通过审核的投稿、每一次管理员的修订，都按时间记在这里。</p>
    </header>

    <el-skeleton v-if="loading && !items.length" :rows="6" animated />

    <div v-else-if="error && !items.length" class="cg-state">
      <p>{{ error }}</p>
      <el-button @click="reload">重试</el-button>
    </div>

    <el-empty v-else-if="!items.length" description="还没有公开发布记录" />

    <template v-else>
      <section v-for="group in groups" :key="group.key" class="cg-group">
        <h2 class="cg-date">{{ group.label }}</h2>
        <ol class="cg-list">
          <li v-for="item in group.items" :key="item.id" class="cg-item">
            <span class="cg-kind" :class="`k-${item.kind}`">{{ kindLabel(item.kind) }}</span>

            <div class="cg-main">
              <router-link class="cg-title" :to="`/docs/${item.path}`">
                <WikiIcon class="cg-icon" :icon="item.icon || ''" :title="item.title" :category="item.categorySlug || ''" :size="15" />
                {{ item.title }}
              </router-link>

              <div class="cg-meta">
                <component
                  :is="item.authorId ? 'router-link' : 'span'"
                  class="cg-author"
                  :to="item.authorId ? `/contributors/${item.authorId}` : undefined"
                >{{ item.authorName }}</component>
                <span class="cg-dot" aria-hidden="true"></span>
                <time :datetime="item.publishedAt">{{ timeOf(item.publishedAt) }}</time>
                <template v-if="categoryLabel(item.categorySlug)">
                  <span class="cg-dot" aria-hidden="true"></span>
                  <span>{{ categoryLabel(item.categorySlug) }}</span>
                </template>
              </div>

              <div v-if="item.changedFields.length || item.contentDelta !== null || item.kind === 'created'" class="cg-detail">
                <span v-for="f in item.changedFields" :key="f" class="cg-field">{{ fieldLabel(f) }}</span>
                <span
                  v-if="item.kind === 'created'"
                  class="cg-delta plus"
                >新增 {{ formatCount(item.contentLength) }} 字</span>
                <span
                  v-else-if="item.contentDelta"
                  class="cg-delta"
                  :class="item.contentDelta > 0 ? 'plus' : 'minus'"
                >{{ item.contentDelta > 0 ? '+' : '−' }}{{ formatCount(Math.abs(item.contentDelta)) }} 字</span>
              </div>
            </div>

            <!-- 新建没有可比的前一版本，打开差异只会是整篇标绿，不如直接点标题阅读 -->
            <router-link
              v-if="item.kind !== 'created'"
              class="cg-diff"
              :to="{ path: `/docs/${item.path}`, query: { rev: item.id } }"
            >查看改动</router-link>
          </li>
        </ol>
      </section>

      <div class="cg-more">
        <el-button v-if="hasMore" :loading="loading" @click="loadMore">加载更早的动态</el-button>
        <p v-else class="cg-end">以上就是全部公开发布记录</p>
      </div>
    </template>
  </div>
</template>

<script>
import { categories, loadManifest } from '@/wiki'
import WikiIcon from '@/components/WikiIcon.vue'
import { getSiteChanges } from '@/net/index.js'

const PAGE_SIZE = 20

const KIND_LABELS = { created: '新建', updated: '更新', restored: '恢复', reverted: '回滚' }
const FIELD_LABELS = {
  title: '标题', categorySlug: '分类', icon: '图标', description: '简介', tags: '标签', content: '正文',
}

export default {
  name: 'ChangesPage',
  components: { WikiIcon },
  data() {
    return {
      items: [],
      page: 0,
      hasMore: false,
      loading: false,
      error: '',
      requestToken: 0,
    }
  },
  computed: {
    // 按查看者本地日期分组：今天 / 昨天 / M月D日（跨年再带上年份）
    groups() {
      const now = new Date()
      const today = dayKey(now)
      const yesterday = dayKey(new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1))
      const out = []
      for (const item of this.items) {
        const d = new Date(item.publishedAt)
        const key = dayKey(d)
        let group = out[out.length - 1]
        if (!group || group.key !== key) {
          let label
          if (key === today) label = '今天'
          else if (key === yesterday) label = '昨天'
          else if (d.getFullYear() === now.getFullYear()) label = `${d.getMonth() + 1} 月 ${d.getDate()} 日`
          else label = `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日`
          group = { key, label, items: [] }
          out.push(group)
        }
        group.items.push(item)
      }
      return out
    },
  },
  mounted() {
    loadManifest()
    this.loadMore()
  },
  methods: {
    loadMore() {
      if (this.loading) return
      const token = ++this.requestToken
      const nextPage = this.page + 1
      this.loading = true
      this.error = ''
      getSiteChanges(
        { page: nextPage, size: PAGE_SIZE },
        (data) => {
          if (token !== this.requestToken) return
          const seen = new Set(this.items.map((i) => String(i.id)))
          // 翻页期间若有新发布，offset 分页会让边界条目重复出现一次，按 id 去掉
          const fresh = ((data && data.items) || []).filter((i) => !seen.has(String(i.id)))
          this.items = this.items.concat(fresh)
          this.page = nextPage
          this.hasMore = !!(data && data.hasMore)
          this.loading = false
        },
        (message) => {
          if (token !== this.requestToken) return
          this.error = message || '动态加载失败'
          this.loading = false
        },
      )
    },
    reload() {
      this.items = []
      this.page = 0
      this.hasMore = false
      this.loadMore()
    },
    kindLabel(kind) {
      return KIND_LABELS[kind] || '更新'
    },
    fieldLabel(field) {
      return FIELD_LABELS[field] || field
    },
    category(slug) {
      return slug ? categories().find((c) => c.slug === slug) : null
    },
    categoryLabel(slug) {
      const c = this.category(slug)
      return c ? c.label : ''
    },
    timeOf(iso) {
      const d = new Date(iso)
      if (Number.isNaN(d.getTime())) return ''
      return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
    },
    formatCount(n) {
      return Number(n || 0).toLocaleString('zh-CN')
    },
  },
}

function dayKey(d) {
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`
}
</script>

<style scoped>
.changes-page {
  max-width: 860px;
  margin: 0 auto;
  padding: 40px 20px 64px;
}

.cg-head { margin-bottom: 28px; }
.cg-eyebrow {
  margin: 0 0 6px;
  color: var(--brand);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: .12em;
}
.cg-head h1 {
  margin: 0;
  color: var(--text-primary);
  font-size: 1.9rem;
  font-weight: 800;
  letter-spacing: -0.02em;
}
.cg-sub { margin: 8px 0 0; color: var(--text-muted); font-size: 13.5px; }

.cg-state { padding: 32px 0; color: var(--text-muted); text-align: center; }

.cg-group + .cg-group { margin-top: 28px; }

.cg-date {
  position: sticky;
  top: var(--header-height, 68px);
  z-index: 1;
  margin: 0 0 10px;
  padding: 6px 0;
  background: var(--bg-page);
  color: var(--text-secondary);
  font-size: 12.5px;
  font-weight: 700;
  letter-spacing: .04em;
}

.cg-list {
  margin: 0;
  padding: 0;
  list-style: none;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-surface);
  overflow: hidden;
}

.cg-item {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) auto;
  align-items: start;
  gap: 14px;
  padding: 15px 18px;
}
.cg-item + .cg-item { border-top: 1px solid var(--border); }

/* 站点是黑白编辑风：用实心 / 描边 / 虚线区分动作，而不是四种颜色 */
.cg-kind {
  display: inline-flex;
  justify-content: center;
  margin-top: 1px;
  padding: 2px 0;
  border: 1px solid var(--border-strong);
  border-radius: 6px;
  color: var(--text-secondary);
  font-size: 11.5px;
  font-weight: 700;
  white-space: nowrap;
}
.cg-kind.k-created { border-color: var(--accent); background: var(--accent); color: var(--accent-contrast); }
.cg-kind.k-restored,
.cg-kind.k-reverted { border-style: dashed; color: var(--text-muted); }

.cg-main { display: flex; min-width: 0; flex-direction: column; gap: 5px; }

.cg-title {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 14.5px;
  font-weight: 700;
  text-decoration: none;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cg-title:hover { text-decoration: underline; }
.cg-icon { margin-right: 6px; color: var(--text-muted); vertical-align: -2px; }

.cg-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 8px;
  color: var(--text-muted);
  font-size: 12px;
}
.cg-author { color: var(--text-secondary); text-decoration: none; }
a.cg-author:hover { color: var(--brand); text-decoration: underline; }
.cg-dot { width: 3px; height: 3px; border-radius: 50%; background: var(--border-strong); }

.cg-detail { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-top: 2px; }
.cg-field {
  padding: 1px 7px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-size: 11px;
}
.cg-delta { font-size: 11.5px; font-variant-numeric: tabular-nums; }
.cg-delta.plus { color: #1f9254; }
.cg-delta.minus { color: #c0392b; }
html.dark .cg-delta.plus { color: #6ee7a8; }
html.dark .cg-delta.minus { color: #f3a097; }

.cg-diff {
  margin-top: 1px;
  color: var(--text-muted);
  font-size: 12px;
  text-decoration: none;
  white-space: nowrap;
}
.cg-diff:hover { color: var(--brand); text-decoration: underline; }

.cg-more { margin-top: 24px; text-align: center; }
.cg-end { margin: 0; color: var(--text-muted); font-size: 12.5px; }

@media (max-width: 600px) {
  .changes-page { padding: 24px 12px 48px; }
  .cg-head h1 { font-size: 1.5rem; }
  .cg-item { grid-template-columns: 40px minmax(0, 1fr); gap: 10px; padding: 13px 14px; }
  /* 窄屏把「查看改动」挪到正文列下方，而不是挤掉标题宽度 */
  .cg-diff { grid-column: 2; margin-top: -2px; }
  .cg-date { top: 56px; }
}
</style>
