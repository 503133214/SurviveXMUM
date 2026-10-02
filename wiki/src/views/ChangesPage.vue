<template>
  <div class="changes-page">
    <header class="cg-head">
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
              <router-link class="cg-title" :to="`/docs/${item.path}`">{{ item.title }}</router-link>

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

              <div v-if="hasDetail(item)" class="cg-detail">
                <span v-for="f in visibleFields(item)" :key="f" class="cg-field">{{ fieldLabel(f) }}</span>
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
import { getSiteChanges } from '@/net/index.js'

const PAGE_SIZE = 20

const KIND_LABELS = { created: '新建', updated: '更新', restored: '恢复', reverted: '回滚' }
const FIELD_LABELS = {
  title: '标题', categorySlug: '分类', icon: '图标', description: '简介', content: '正文',
}

export default {
  name: 'ChangesPage',
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
    // 只展示有中文名的字段：后端可能带回前端已不再展示的字段（已下线功能留下的历史记录），
    // 不把原始英文键名露给读者，也不依赖前后端的部署先后
    visibleFields(item) {
      return (item.changedFields || []).filter((f) => FIELD_LABELS[f])
    },
    fieldLabel(field) {
      return FIELD_LABELS[field]
    },
    // 有东西可写才渲染明细行。contentDelta 为 0 时下面不会输出字数，若这时也没有可展示的
    // 字段（例如历史上只改了已下线字段的版本），按 !== null 判断会留下一条带上边距的空行
    hasDetail(item) {
      return item.kind === 'created' || this.visibleFields(item).length > 0 || !!item.contentDelta
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

.cg-head { margin-bottom: 24px; }
.cg-head h1 {
  margin: 0;
  color: var(--text-primary);
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 0;
  line-height: 1.3;
}
.cg-sub { margin: 6px 0 0; color: var(--text-secondary); font-size: 14px; }

.cg-state { padding: 32px 0; color: var(--text-muted); text-align: center; }

.cg-group + .cg-group { margin-top: 28px; }

/* 吸顶位置跟随顶栏高度令牌：手机端令牌本身会变成单行顶栏的高度，不用再写死 */
.cg-date {
  position: sticky;
  top: var(--header-height);
  z-index: 1;
  margin: 0 0 10px;
  padding: 6px 0;
  background: var(--bg-page);
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0;
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

/* 只有「新建」用 success 浅底，与首页「最近更新」里的同名标签一致；
   其余动作保持中性，用描边 / 虚线区分，不再给每种动作配一种颜色 */
.cg-kind {
  display: inline-flex;
  justify-content: center;
  margin-top: 1px;
  padding: 0;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-xs);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
  white-space: nowrap;
}
.cg-kind.k-created { border-color: transparent; background: var(--success-soft); color: var(--success); }
.cg-kind.k-restored,
.cg-kind.k-reverted { border-style: dashed; color: var(--text-muted); }

.cg-main { display: flex; min-width: 0; flex-direction: column; gap: 5px; }

.cg-title {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cg-title:hover { text-decoration: underline; }

.cg-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 8px;
  color: var(--text-muted);
  font-size: 13px;
}
.cg-author { color: var(--text-secondary); text-decoration: none; }
a.cg-author:hover { color: var(--brand); text-decoration: underline; }
.cg-dot { width: 3px; height: 3px; border-radius: 50%; background: var(--border-strong); }

.cg-detail { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-top: 2px; }
.cg-field {
  padding: 0 6px;
  border: 1px solid var(--border);
  border-radius: var(--radius-xs);
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 20px;
}
/* 增删用状态令牌，暗色主题由 html.dark 下的同名变量自动接管 */
.cg-delta { font-size: 12px; font-variant-numeric: tabular-nums; }
.cg-delta.plus { color: var(--success); }
.cg-delta.minus { color: var(--danger); }

.cg-diff {
  margin-top: 1px;
  color: var(--text-muted);
  font-size: 13px;
  text-decoration: none;
  white-space: nowrap;
}
.cg-diff:hover { color: var(--brand); text-decoration: underline; }

.cg-more { margin-top: 24px; text-align: center; }
.cg-end { margin: 0; color: var(--text-muted); font-size: 13px; }

@media (max-width: 600px) {
  .changes-page { padding: 24px 12px 48px; }
  .cg-head h1 { font-size: 24px; }
  .cg-item { grid-template-columns: 40px minmax(0, 1fr); gap: 10px; padding: 13px 14px; }
  /* 窄屏把「查看改动」挪到正文列下方，而不是挤掉标题宽度 */
  .cg-diff { grid-column: 2; margin-top: -2px; }
}
</style>
