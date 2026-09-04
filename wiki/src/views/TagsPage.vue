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
        v-for="item in visibleTags"
        :key="item.tag"
        type="button"
        class="tp-chip"
        :class="{ active: sameTag(item.tag, activeTag), [`heat-${heat(item.count)}`]: true }"
        @click="select(item.tag)"
      >
        {{ item.tag }}
        <span class="tp-chip-count">{{ item.count }}</span>
      </button>
      <button
        v-if="tags.length > COLLAPSED_LIMIT"
        type="button"
        class="tp-more"
        @click="expanded = !expanded"
      >
        {{ expanded ? '收起' : `展开全部 ${tags.length} 个` }}
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

const COLLAPSED_LIMIT = 24

export default {
  name: 'TagsPage',
  props: {
    // 路由 /tags/:tag，无参数时只显示标签云
    tag: { type: String, default: '' },
  },
  data() {
    return { expanded: false, COLLAPSED_LIMIT }
  },
  computed: {
    // pages 是 reactive 数组，manifest 异步到达后这些计算属性会自动重算
    tags() {
      return allTags()
    },
    // 线上有 82 个标签，全铺开在手机上有 1500px 高，结果列表被埋在两屏之下。
    // 默认只露出最热门的一批；当前选中的标签即使排在后面也一定可见。
    visibleTags() {
      if (this.expanded || this.tags.length <= COLLAPSED_LIMIT) return this.tags
      const head = this.tags.slice(0, COLLAPSED_LIMIT)
      if (this.activeTag && !head.some((t) => this.sameTag(t.tag, this.activeTag))) {
        const active = this.tags.find((t) => this.sameTag(t.tag, this.activeTag))
        if (active) return [...head.slice(0, COLLAPSED_LIMIT - 1), active]
      }
      return head
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
    // 标签数量是长尾的（线上 82 个标签里 65 个只有 1 篇），按“占最大值的比例”
    // 分档会把几乎所有标签压进最小一档，字号差别等于没有。改成按不同计数值的
    // 名次分四档：同样多的文档永远同一档，分布再偏也能拉开层次。
    heatByCount() {
      const distinct = [...new Set(this.tags.map((t) => t.count))].sort((a, b) => b - a)
      const map = new Map()
      distinct.forEach((count, rank) => {
        map.set(count, Math.max(1, 4 - Math.floor((rank * 4) / distinct.length)))
      })
      return map
    },
  },
  mounted() {
    loadManifest()
  },
  methods: {
    heat(count) {
      return this.heatByCount.get(count) || 1
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
.tp-chip.active .tp-more {
  padding: 7px 13px;
  border: 1px dashed var(--border-strong, var(--border));
  border-radius: 999px;
  background: none;
  color: var(--text-muted);
  font: inherit;
  font-size: 12.5px;
  cursor: pointer;
}
.tp-more:hover { border-color: var(--brand); color: var(--brand); }

.tp-chip-count { background: rgba(255, 255, 255, .25); color: #fff; }

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
.tp-card-tags span.hit { color: var(--brand); font-weight: 600; }

.tp-hint { margin: 18px 0 0; color: var(--text-muted); font-size: 13px; text-align: center; }

@media (max-width: 600px) {
  .tags-page { padding: 24px 14px 48px; }
  .tp-head h1 { font-size: 1.5rem; }
  .tp-cloud { padding: 14px; gap: 8px; }
}
</style>
