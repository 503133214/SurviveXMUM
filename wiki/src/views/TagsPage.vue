<template>
  <div class="tags-page">
    <header class="tp-head">
      <p class="tp-eyebrow">换个方式找文档</p>
      <h1>标签广场</h1>
      <p class="tp-sub">
        <template v-if="tags.length">
          {{ tags.length }} 个标签 · 覆盖 {{ taggedPageCount }} 篇文档，点标签看这一类都写了什么
        </template>
        <template v-else>还没有文档打上标签——编辑任意一篇文档时都可以添加</template>
      </p>
    </header>

    <div v-if="tags.length" class="tp-cloud">
      <button
        v-for="item in tags"
        :key="item.tag"
        type="button"
        class="tp-chip"
        :class="{ active: item.tag === activeTag, [`heat-${heat(item.count)}`]: true }"
        @click="select(item.tag)"
      >
        {{ item.tag }}
        <span class="tp-chip-count">{{ item.count }}</span>
      </button>
    </div>

    <section v-if="activeTag" class="tp-result">
      <div class="tp-result-head">
        <h2>标签「{{ activeTag }}」下的 {{ matched.length }} 篇文档</h2>
        <button type="button" class="tp-clear" @click="select('')">清除筛选</button>
      </div>

      <el-empty v-if="!matched.length" description="这个标签下暂时没有文档" />
      <ul v-else class="tp-list">
        <li v-for="page in matched" :key="page.path">
          <router-link class="tp-card" :to="`/docs/${page.path}`">
            <span class="tp-card-icon">{{ page.icon || '📄' }}</span>
            <span class="tp-card-body">
              <strong>{{ page.title }}</strong>
              <small v-if="page.description">{{ page.description }}</small>
              <span class="tp-card-tags">
                <span v-for="t in page.tags || []" :key="t" :class="{ hit: sameTag(t, activeTag) }">
                  #{{ t }}
                </span>
              </span>
            </span>
          </router-link>
        </li>
      </ul>
    </section>

    <p v-else-if="tags.length" class="tp-hint">↑ 选一个标签开始浏览</p>
  </div>
</template>

<script>
import { allTags, pagesByTag, orderedPages, loadManifest } from '@/wiki'

export default {
  name: 'TagsPage',
  props: {
    // 路由 /tags/:tag，无参数时只显示标签云
    tag: { type: String, default: '' },
  },
  computed: {
    // pages 是 reactive 数组，manifest 异步到达后这些计算属性会自动重算
    tags() {
      return allTags()
    },
    activeTag() {
      return (this.tag || '').trim()
    },
    matched() {
      return this.activeTag ? pagesByTag(this.activeTag) : []
    },
    taggedPageCount() {
      return orderedPages().filter((p) => (p.tags || []).some((t) => (t || '').trim())).length
    },
    maxCount() {
      return this.tags.reduce((m, t) => Math.max(m, t.count), 0)
    },
  },
  mounted() {
    loadManifest()
  },
  methods: {
    // 按占最高热度的比例分四档，标签少时也不会全挤在同一档
    heat(count) {
      if (!this.maxCount) return 1
      const ratio = count / this.maxCount
      if (ratio > 0.75) return 4
      if (ratio > 0.5) return 3
      if (ratio > 0.25) return 2
      return 1
    },
    sameTag(a, b) {
      return (a || '').trim().toLowerCase() === (b || '').trim().toLowerCase()
    },
    select(tag) {
      if (!tag || this.sameTag(tag, this.activeTag)) {
        this.$router.push('/tags')
        return
      }
      this.$router.push(`/tags/${encodeURIComponent(tag)}`)
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

.tp-head { margin-bottom: 24px; }
.tp-eyebrow {
  margin: 0 0 6px;
  color: var(--brand);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: .12em;
}
.tp-head h1 {
  margin: 0;
  color: var(--text-primary);
  font-size: 1.9rem;
  font-weight: 800;
  letter-spacing: -0.02em;
}
.tp-sub { margin: 8px 0 0; color: var(--text-muted); font-size: 13.5px; }

.tp-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 20px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-surface);
}

.tp-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 13px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--bg-page);
  color: var(--text-secondary);
  font: inherit;
  cursor: pointer;
  transition: border-color .18s ease, color .18s ease, transform .18s ease;
}
.tp-chip:hover { border-color: var(--brand); color: var(--brand); transform: translateY(-1px); }
.tp-chip.active { border-color: var(--brand); background: var(--brand); color: #fff; }
.tp-chip.active .tp-chip-count { background: rgba(255, 255, 255, .25); color: #fff; }

/* 热度只影响字号，颜色留给 hover/选中，避免整片花掉 */
.tp-chip.heat-1 { font-size: 12.5px; }
.tp-chip.heat-2 { font-size: 13.5px; }
.tp-chip.heat-3 { font-size: 15px; font-weight: 600; }
.tp-chip.heat-4 { font-size: 16.5px; font-weight: 700; }

.tp-chip-count {
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--bg-surface);
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 500;
}

.tp-result { margin-top: 28px; }
.tp-result-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}
.tp-result-head h2 {
  margin: 0;
  color: var(--text-primary);
  font-size: 1.05rem;
  font-weight: 700;
}
.tp-clear {
  border: none;
  background: none;
  color: var(--text-muted);
  font: inherit;
  font-size: 12.5px;
  cursor: pointer;
}
.tp-clear:hover { color: var(--brand); }

.tp-list { margin: 0; padding: 0; list-style: none; display: grid; gap: 12px; }

.tp-card {
  display: flex;
  gap: 13px;
  padding: 15px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  color: inherit;
  text-decoration: none;
  transition: border-color .18s ease, box-shadow .18s ease, transform .18s ease;
}
.tp-card:hover {
  border-color: var(--brand);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}

.tp-card-icon { flex-shrink: 0; font-size: 20px; line-height: 1.4; }
.tp-card-body { display: flex; min-width: 0; flex-direction: column; gap: 4px; }
.tp-card-body strong { color: var(--text-primary); font-size: 14.5px; }
.tp-card-body small {
  overflow: hidden;
  color: var(--text-muted);
  font-size: 12.5px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.tp-card-tags { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 2px; }
.tp-card-tags span { color: var(--text-muted); font-size: 11.5px; }
.tp-card-tags span.hit { color: var(--brand); font-weight: 600; }

.tp-hint { margin: 18px 0 0; color: var(--text-muted); font-size: 13px; text-align: center; }

@media (max-width: 600px) {
  .tags-page { padding: 24px 14px 48px; }
  .tp-head h1 { font-size: 1.5rem; }
  .tp-cloud { padding: 14px; gap: 8px; }
}
</style>
