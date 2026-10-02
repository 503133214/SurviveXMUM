<template>
  <section class="page-contributors" aria-labelledby="page-contributors-title">
    <div class="contributors-heading">
      <div>
        <h2 id="page-contributors-title">
          本页贡献者
          <span v-if="contributors.length" class="contributors-total">{{ contributors.length }} 位</span>
        </h2>
        <p class="contributors-note">按已发布的编写与修改次数排序</p>
      </div>
      <button
        v-if="contributors.length > collapsedLimit"
        class="contributors-toggle"
        type="button"
        :aria-expanded="expanded"
        aria-controls="page-contributor-list"
        @click="expanded = !expanded"
      >
        {{ expanded ? '收起' : `查看全部 ${contributors.length} 位` }}
      </button>
    </div>

    <el-skeleton v-if="loading" :rows="1" />
    <p v-else-if="error" class="contributors-status">贡献者信息暂时无法加载</p>
    <p v-else-if="!contributors.length" class="contributors-status">暂无可确认的贡献者记录</p>
    <div v-else id="page-contributor-list" class="contributors-list">
      <component
        :is="contributor.userId ? 'router-link' : 'div'"
        v-for="(contributor, index) in visibleContributors"
        :key="`${contributor.userId || 'removed'}-${contributor.displayName}-${index}`"
        class="contributor-card"
        :class="{ linked: contributor.userId }"
        :to="contributor.userId ? `/contributors/${contributor.userId}` : undefined"
      >
        <el-avatar :size="38" :src="contributor.avatar || undefined">
          {{ initial(contributor.displayName) }}
        </el-avatar>
        <span class="contributor-copy">
          <strong>{{ contributor.displayName }}</strong>
          <small>{{ contributor.count }} 次贡献</small>
        </span>
      </component>
    </div>
  </section>
</template>

<script>
export default {
  name: 'PageContributors',
  props: {
    contributors: { type: Array, default: () => [] },
    loading: { type: Boolean, default: false },
    error: { type: String, default: '' },
  },
  data() {
    return {
      expanded: false,
      collapsedLimit: 3,
    }
  },
  computed: {
    visibleContributors() {
      return this.expanded
        ? this.contributors
        : this.contributors.slice(0, this.collapsedLimit)
    },
  },
  watch: {
    contributors() {
      this.expanded = false
    },
  },
  methods: {
    initial(name) {
      return (name || '?').trim().charAt(0).toUpperCase()
    },
  },
}
</script>

<style scoped>
/* 文档页的附属区块不再装进卡片：只用一条分隔线和正文隔开，读起来像手册的章末附录 */
.page-contributors {
  margin-top: 40px;
  padding-top: 24px;
  border-top: 1px solid var(--border);
}

.contributors-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.contributors-heading h2 {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 0;
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 600;
  line-height: var(--lh-tight);
  letter-spacing: 0;
}

.contributors-total {
  color: var(--text-muted);
  font-size: var(--fs-xs);
  font-weight: 500;
  font-variant-numeric: tabular-nums;
}

.contributors-note {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: var(--fs-sm);
}

/* 不覆盖 :focus-visible 的 outline：键盘用户要靠全局焦点环看到当前位置 */
.contributors-toggle {
  flex-shrink: 0;
  height: 30px;
  padding: 0 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  color: var(--text-secondary);
  font: inherit;
  font-size: var(--fs-sm);
  font-weight: 500;
  cursor: pointer;
  transition: border-color var(--dur) ease, color var(--dur) ease;
}

.contributors-toggle:hover {
  border-color: var(--border-strong);
  color: var(--text-primary);
}

.contributors-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.contributor-card {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 11px;
  padding: 11px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-page);
  color: inherit;
  text-decoration: none;
}

/* 可点的卡片悬停只换边框色，不浮起、不加阴影 */
.contributor-card.linked {
  transition: border-color var(--dur) ease;
}

.contributor-card.linked:hover {
  border-color: var(--accent);
  text-decoration: none;
}

.contributor-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.contributor-copy strong {
  overflow: hidden;
  color: var(--text-primary);
  font-size: var(--fs-ui);
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.contributor-copy small {
  color: var(--text-muted);
  font-size: var(--fs-xs);
  font-variant-numeric: tabular-nums;
}

.contributors-status {
  margin: 0;
  color: var(--text-muted);
  font-size: var(--fs-sm);
}

@media (max-width: 900px) {
  .contributors-list { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 600px) {
  .contributors-heading { align-items: center; }
  .contributors-list { grid-template-columns: 1fr; }
  .contributors-note { display: none; }
}
</style>
