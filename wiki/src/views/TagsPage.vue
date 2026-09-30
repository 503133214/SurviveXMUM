<template>
  <div class="tags-page">
    <header class="tp-head">
      <h1>标签</h1>
    </header>

    <div v-if="wikiState.loaded" class="tags-layout">
      <nav class="tag-side" aria-label="标签筛选">
        <router-link class="tag-item" :class="{ 'is-active': !activeTag }" to="/tags">
          <span>全部</span>
          <span class="tag-n">{{ allCount }}</span>
        </router-link>
        <router-link
          v-for="t in tags"
          :key="t.tag"
          class="tag-item"
          :class="{ 'is-active': sameTag(t.tag, activeTag) }"
          :to="`/tags/${encodeURIComponent(t.tag)}`"
        >
          <span class="tag-name">{{ t.tag }}</span>
          <span class="tag-n">{{ t.count }}</span>
        </router-link>
      </nav>

      <section class="tp-result">
        <h2 class="tp-result-title">{{ activeTag ? `标签「${activeTag}」` : '全部文档' }}</h2>

        <el-empty v-if="!matched.length" description="这个标签下暂时没有文档" />
        <ul v-else class="tp-list">
          <li v-for="page in matched" :key="page.path">
            <router-link class="tp-card" :to="`/docs/${page.path}`">
              <span class="tp-card-icon" aria-hidden="true">
                <WikiIcon :icon="page.icon" :title="page.title" :category="page.category" :size="18" />
              </span>
              <span class="tp-card-body">
                <strong>{{ page.title }}</strong>
                <small v-if="page.description">{{ page.description }}</small>
                <span v-if="(page.tags || []).length" class="tp-card-tags">
                  <span v-for="t in page.tags" :key="t">#{{ t }}</span>
                </span>
              </span>
            </router-link>
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>

<script>
import { allTags, pagesByTag, pages, loadManifest, HOME_PATH, state as wikiState } from '@/wiki'
import WikiIcon from '@/components/WikiIcon.vue'

export default {
  name: 'TagsPage',
  components: { WikiIcon },
  props: {
    // 路由 /tags/:tag，无参数时为「全部」
    tag: { type: String, default: '' },
  },
  data() {
    return { wikiState }
  },
  computed: {
    // pages 是 reactive 数组，manifest 异步到达后这些计算属性会自动重算
    tags() {
      return allTags()
    },
    activeTag() {
      return (this.tag || '').trim()
    },
    // 「全部」= 除文档首页 README 外的所有页面，按 manifest 的篇章顺序排列
    matched() {
      if (this.activeTag) return pagesByTag(this.activeTag)
      return pages.filter((p) => p.path !== HOME_PATH)
    },
    allCount() {
      return pages.filter((p) => p.path !== HOME_PATH).length
    },
  },
  mounted() {
    loadManifest()
  },
  methods: {
    sameTag(a, b) {
      return (a || '').trim().toLowerCase() === (b || '').trim().toLowerCase()
    },
  },
}
</script>

<style scoped>
.tags-page {
  max-width: 1000px;
  margin: 0 auto;
  padding: 40px 20px 64px;
}

.tp-head { margin-bottom: 22px; }
.tp-head h1 {
  margin: 0;
  color: var(--text-primary);
  font-size: 1.9rem;
  font-weight: 800;
  letter-spacing: -0.02em;
}

/* 左标签列表 + 右文档列表 */
.tags-layout {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: 32px;
  align-items: start;
}

.tag-side {
  position: sticky;
  top: calc(var(--header-height) + 16px);
  max-height: calc(100vh - var(--header-height) - 32px);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.tag-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 5px 10px;
  border-radius: var(--radius-xs);
  color: var(--text-secondary);
  font-size: 13.5px;
  transition: background 0.12s ease, color 0.12s ease;
}
.tag-item:hover { background: var(--bg-subtle); color: var(--text-primary); text-decoration: none; }
.tag-item.is-active { background: var(--accent-soft); color: var(--accent); font-weight: 600; }
.tag-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tag-n {
  flex-shrink: 0;
  color: var(--text-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.tag-item.is-active .tag-n { color: var(--accent); }

.tp-result-title {
  margin: 0 0 14px;
  color: var(--text-primary);
  font-size: 1.05rem;
  font-weight: 700;
}

.tp-list { margin: 0; padding: 0; list-style: none; display: grid; gap: 10px; }

.tp-card {
  display: flex;
  gap: 13px;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  color: inherit;
  text-decoration: none;
  transition: border-color 0.15s ease;
}
.tp-card:hover { border-color: var(--border-strong); }

.tp-card-icon {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--border);
  border-radius: var(--radius-xs);
  background: var(--bg-subtle);
  color: var(--accent);
}
.tp-card-body { display: flex; min-width: 0; flex-direction: column; gap: 4px; }
.tp-card-body strong { color: var(--text-primary); font-size: 14.5px; }
.tp-card-body small {
  overflow: hidden;
  overflow-wrap: anywhere;
  color: var(--text-muted);
  font-size: 12.5px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.tp-card-tags { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 2px; }
.tp-card-tags span { color: var(--text-muted); font-size: 11.5px; }

@media (max-width: 720px) {
  .tags-page { padding: 24px 14px 48px; }
  .tp-head h1 { font-size: 1.5rem; }
  .tags-layout { grid-template-columns: minmax(0, 1fr); gap: 18px; }
  /* 手机上标签列表变成单行横向滑动条，吸顶跟随 */
  .tag-side {
    position: sticky;
    top: var(--header-height);
    z-index: 5;
    max-height: none;
    overflow-x: auto;
    overflow-y: hidden;
    flex-direction: row;
    flex-wrap: nowrap;
    gap: 4px;
    padding: 6px max(14px, env(safe-area-inset-left));
    margin: 0 -14px 6px;
    border-bottom: 1px solid var(--border);
    background: var(--bg-page);
    scrollbar-width: none;
  }
  .tag-side::-webkit-scrollbar { display: none; }
  .tag-item { flex: 0 0 auto; gap: 5px; padding: 4px 9px; }
}
</style>
