<template>
  <div class="contrib-page">
    <header class="cp-header">
      <h1>贡献者与致谢</h1>
      <p class="cp-sub">感谢每一位为厦马生存指南添砖加瓦的你</p>
    </header>

    <!-- 赞助 / 致谢墙 -->
    <section v-if="wall.length" class="cp-section">
      <h2 class="cp-h2">赞助与致谢</h2>
      <div v-for="g in wallGroups" :key="g.category || '_'" class="wall-group">
        <h3 v-if="g.category" class="wall-cat">{{ g.category }}</h3>
        <div class="wall-grid">
          <component
            :is="item.link ? 'a' : 'div'"
            v-for="item in g.items"
            :key="item.id"
            class="wall-card"
            :href="item.link || undefined"
            :target="item.link ? '_blank' : undefined"
            rel="noopener noreferrer"
          >
            <el-avatar :size="56" :src="item.avatar || undefined">{{ initial(item.name) }}</el-avatar>
            <div class="wall-name">{{ item.name }}</div>
            <div v-if="item.description" class="wall-desc">{{ item.description }}</div>
          </component>
        </div>
      </div>
    </section>

    <!-- 贡献榜 -->
    <section class="cp-section">
      <h2 class="cp-h2">贡献榜</h2>
      <p class="cp-note">按已通过的投稿数量排名</p>
      <el-empty v-if="!contributors.length" description="暂无贡献数据" />
      <div v-else class="lb-list">
        <div
          v-for="(c, i) in contributors"
          :key="c.userId"
          class="lb-row"
          @click="$router.push(`/contributors/${c.userId}`)"
        >
          <span class="lb-rank" :class="{ top: i < 3 }">{{ i + 1 }}</span>
          <el-avatar :size="38" :src="c.avatar || undefined">{{ initial(c.displayName) }}</el-avatar>
          <span class="lb-name">{{ c.displayName }}</span>
          <span class="lb-count">{{ c.count }} 篇贡献</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script>
import { getContributors, getWall } from '@/net/index.js'

export default {
  name: 'ContributorsPage',
  data() {
    return { contributors: [], wall: [] }
  },
  computed: {
    wallGroups() {
      // 按 category 分组，保留后端返回顺序（sort_order）
      const groups = []
      const idx = {}
      for (const item of this.wall) {
        const cat = item.category || ''
        if (!(cat in idx)) { idx[cat] = groups.length; groups.push({ category: cat, items: [] }) }
        groups[idx[cat]].items.push(item)
      }
      return groups
    },
  },
  methods: {
    initial(name) { return (name || '?').trim().charAt(0).toUpperCase() },
  },
  mounted() {
    getContributors((d) => { this.contributors = d || [] }, () => {})
    getWall((d) => { this.wall = d || [] }, () => {})
  },
}
</script>

<style scoped>
.contrib-page {
  max-width: 1000px;
  margin: 0 auto;
  padding: 32px 24px 64px;
}
.cp-header { margin-bottom: 8px; }
.cp-header h1 { margin: 0; font-size: 1.9rem; font-weight: 800; letter-spacing: -0.02em; color: var(--text-primary); }
.cp-sub { margin: 8px 0 0; color: var(--text-muted); font-size: 14px; }

.cp-section { margin-top: 40px; }
.cp-h2 { font-size: 1.3rem; font-weight: 800; color: var(--text-primary); margin: 0 0 4px; }
.cp-note { margin: 0 0 18px; color: var(--text-muted); font-size: 13px; }

/* 致谢墙 */
.wall-group { margin-bottom: 24px; }
.wall-cat {
  font-size: 0.95rem; font-weight: 700; color: var(--text-secondary);
  margin: 18px 0 12px; padding-bottom: 6px; border-bottom: 1px solid var(--border);
}
.wall-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 16px;
}
.wall-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 8px;
  padding: 20px 14px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-surface);
  text-decoration: none;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}
a.wall-card:hover { border-color: var(--brand); box-shadow: var(--shadow-md); transform: translateY(-2px); text-decoration: none; }
.wall-name { font-size: 14.5px; font-weight: 700; color: var(--text-primary); }
.wall-desc { font-size: 12.5px; color: var(--text-secondary); line-height: 1.5; }

/* 贡献榜 */
.lb-list { display: flex; flex-direction: column; }
.lb-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 6px;
  border-bottom: 1px solid var(--border);
  cursor: pointer;
  transition: background 0.15s ease, padding-left 0.2s ease;
}
.lb-row:hover { background: var(--bg-subtle); padding-left: 12px; }
.lb-rank {
  flex-shrink: 0;
  display: inline-grid; place-items: center;
  width: 26px; height: 26px; border-radius: 8px;
  background: var(--bg-hover); color: var(--text-muted);
  font-size: 13px; font-weight: 800;
}
.lb-rank.top { background: var(--accent); color: var(--accent-contrast); }
.lb-name { flex: 1; min-width: 0; font-weight: 600; color: var(--text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.lb-count { flex-shrink: 0; color: var(--text-secondary); font-size: 13px; }

@media (max-width: 767px) {
  .contrib-page { padding: 20px 14px 48px; }
  .wall-grid { grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 12px; }
}
</style>
