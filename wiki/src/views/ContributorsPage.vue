<template>
  <div class="contrib-page">
    <header class="cp-head">
      <h1>贡献者</h1>
      <p>感谢每一位认真留下答案的人。</p>
    </header>

    <section v-if="loadingWall || wall.length" class="cp-section">
      <h2 class="cp-title">赞助与致谢</h2>

      <div v-if="loadingWall" class="wall-grid">
        <el-skeleton v-for="i in 4" :key="i" animated>
          <template #template>
            <div class="skeleton-card">
              <el-skeleton-item variant="circle" class="skeleton-avatar" />
              <el-skeleton-item variant="text" style="width: 58%" />
              <el-skeleton-item variant="text" style="width: 76%" />
            </div>
          </template>
        </el-skeleton>
      </div>

      <div v-else class="wall-groups">
        <div v-for="group in wallGroups" :key="group.category || '_'" class="wall-group">
          <h3 v-if="group.category" class="wall-group-name">{{ group.category }}</h3>
          <div class="wall-grid">
            <component
              :is="item.link ? 'a' : 'article'"
              v-for="item in group.items"
              :key="item.id"
              class="wall-card"
              :href="item.link || undefined"
              :target="item.link ? '_blank' : undefined"
              :aria-label="item.link ? `${item.name}，在新窗口打开链接` : undefined"
              rel="noopener noreferrer"
            >
              <el-avatar :size="44" :src="item.avatar || undefined">
                {{ initial(item.name) }}
              </el-avatar>
              <span class="wall-info">
                <span class="wall-name">{{ item.name }}<span v-if="item.link" class="wall-ext" aria-hidden="true"> ↗</span></span>
                <span class="wall-desc">{{ item.description || '感谢你的支持' }}</span>
              </span>
            </component>
          </div>
        </div>
      </div>
    </section>

    <section class="cp-section">
      <h2 class="cp-title">贡献榜</h2>
      <p class="cp-desc">按已通过的投稿数量排序。</p>

      <div v-if="loadingContributors">
        <el-skeleton :rows="6" animated />
      </div>
      <el-empty v-else-if="!contributors.length" description="暂无贡献数据" />
      <ol v-else class="board">
        <li v-for="(contributor, index) in contributors" :key="contributor.userId">
          <router-link class="board-row" :to="`/contributors/${contributor.userId}`">
            <span class="board-rank">{{ index + 1 }}</span>
            <el-avatar :size="36" :src="contributor.avatar || undefined">
              {{ initial(contributor.displayName) }}
            </el-avatar>
            <span class="board-name">{{ contributor.displayName }}</span>
            <ContributorBadges :badges="contributor.badges || []" :max="4" />
            <span class="board-count">{{ contributor.count }} 篇</span>
          </router-link>
        </li>
      </ol>
    </section>

    <footer class="cp-foot">
      <p>发现错误或想分享经验？</p>
      <router-link to="/docs/贡献指南">查看贡献指南 →</router-link>
    </footer>
  </div>
</template>

<script>
import ContributorBadges from '@/components/ContributorBadges.vue'
import { getContributors, getWall } from '@/net/index.js'

export default {
  name: 'ContributorsPage',
  components: { ContributorBadges },
  data() {
    return {
      contributors: [],
      wall: [],
      loadingContributors: true,
      loadingWall: true,
    }
  },
  computed: {
    wallGroups() {
      const groups = []
      const indexes = {}
      for (const item of this.wall) {
        const category = item.category || ''
        if (!(category in indexes)) {
          indexes[category] = groups.length
          groups.push({ category, items: [] })
        }
        groups[indexes[category]].items.push(item)
      }
      return groups
    },
  },
  methods: {
    initial(name) {
      return (name || '?').trim().charAt(0).toUpperCase()
    },
  },
  mounted() {
    getContributors(
      (data) => {
        this.contributors = data || []
        this.loadingContributors = false
      },
      () => {
        this.contributors = []
        this.loadingContributors = false
      },
    )
    getWall(
      (data) => {
        this.wall = data || []
        this.loadingWall = false
      },
      () => {
        this.wall = []
        this.loadingWall = false
      },
    )
  },
}
</script>

<style scoped>
.contrib-page {
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
  padding: 40px 20px 72px;
}

.cp-head { margin-bottom: 32px; }
.cp-head h1 {
  margin: 0;
  color: var(--text-primary);
  font-size: 1.9rem;
  font-weight: 750;
  letter-spacing: -0.02em;
}
.cp-head p { margin: 8px 0 0; color: var(--text-muted); font-size: 14px; }

.cp-section { margin-top: 40px; }
.cp-title {
  margin: 0;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border);
  color: var(--text-primary);
  font-size: 1.2rem;
  font-weight: 700;
}
.cp-desc { margin: 10px 0 0; color: var(--text-muted); font-size: 13px; }

/* ---- 赞助与致谢 ---- */
.wall-group { margin-top: 22px; }
.wall-group-name {
  margin: 0 0 10px;
  color: var(--text-secondary);
  font-size: 13.5px;
  font-weight: 650;
}
.wall-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.wall-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-surface);
  color: var(--text-primary);
  text-decoration: none;
  transition: border-color 0.15s ease;
}
a.wall-card:hover { border-color: var(--border-strong); text-decoration: none; }
.wall-card :deep(.el-avatar) {
  flex-shrink: 0;
  background: var(--bg-subtle);
  color: var(--text-primary);
  font-weight: 650;
}
.wall-info { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.wall-name {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.wall-ext { color: var(--text-muted); font-weight: 400; }
.wall-desc {
  overflow: hidden;
  color: var(--text-muted);
  font-size: 12.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.skeleton-card {
  display: flex;
  min-height: 76px;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
}
.skeleton-avatar { width: 44px !important; height: 44px !important; }

/* ---- 贡献榜 ---- */
.board { list-style: none; margin: 14px 0 0; border: 1px solid var(--border); border-radius: var(--radius); background: var(--bg-surface); }
.board li + li { border-top: 1px solid var(--border); }
.board-row {
  display: grid;
  grid-template-columns: 30px 36px minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 12px;
  min-height: 60px;
  padding: 9px 16px;
  color: var(--text-primary);
  text-decoration: none;
  transition: background 0.15s ease;
}
.board-row:hover { background: var(--bg-subtle); color: var(--text-primary); text-decoration: none; }
.board-row :deep(.el-avatar) { background: var(--bg-hover); color: var(--text-primary); font-weight: 650; }
.board-rank {
  color: var(--text-muted);
  font-size: 12.5px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  text-align: center;
}
.board-name {
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.board-row :deep(.cb-strip) { margin-right: 8px; }
.board-count {
  color: var(--text-muted);
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

/* ---- 页脚 ---- */
.cp-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 48px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
}
.cp-foot p { margin: 0; color: var(--text-muted); font-size: 13.5px; }
.cp-foot a { color: var(--brand-blue); font-size: 13.5px; font-weight: 500; }
.cp-foot a:hover { color: var(--accent-hover); }

@media (max-width: 700px) {
  .contrib-page { padding: 24px 16px 56px; }
  .wall-grid { grid-template-columns: minmax(0, 1fr); }
  .board-row { grid-template-columns: 24px 36px minmax(0, 1fr) auto; }
  .board-row :deep(.cb-strip) { display: none; }
  .cp-foot { flex-direction: column; align-items: flex-start; }
}
</style>
