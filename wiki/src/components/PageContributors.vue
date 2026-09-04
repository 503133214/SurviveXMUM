<template>
  <section class="page-contributors" aria-labelledby="page-contributors-title">
    <div class="contributors-heading">
      <div>
        <p class="contributors-eyebrow">共同完成</p>
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

    <el-skeleton v-if="loading" :rows="1" animated />
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
.page-contributors {
  max-width: 1360px;
  margin: 32px auto 0;
  padding: 20px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-surface);
}

.contributors-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.contributors-eyebrow {
  margin: 0 0 4px;
  color: var(--brand);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: .12em;
}

.contributors-heading h2 {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 0;
  color: var(--text-primary);
  font-size: 1.08rem;
  line-height: 1.3;
}

.contributors-total {
  color: var(--text-muted);
  font-size: 12px;
  font-weight: 500;
}

.contributors-note {
  margin: 5px 0 0;
  color: var(--text-muted);
  font-size: 12px;
}

.contributors-toggle {
  flex-shrink: 0;
  padding: 7px 11px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--bg-page);
  color: var(--text-secondary);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  transition: border-color .2s ease, color .2s ease, background .2s ease;
}

.contributors-toggle:hover,
.contributors-toggle:focus-visible {
  border-color: var(--brand);
  color: var(--brand);
  outline: none;
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

.contributor-card.linked {
  transition: border-color .2s ease, transform .2s ease, box-shadow .2s ease;
}

.contributor-card.linked:hover,
.contributor-card.linked:focus-visible {
  border-color: var(--brand);
  box-shadow: var(--shadow-sm);
  text-decoration: none;
  transform: translateY(-1px);
  outline: none;
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
  font-size: 13.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.contributor-copy small {
  color: var(--text-muted);
  font-size: 11.5px;
}

.contributors-status {
  margin: 0;
  color: var(--text-muted);
  font-size: 13px;
}

@media (max-width: 900px) {
  .contributors-list { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 600px) {
  .page-contributors { padding: 20px 14px; }
  .contributors-heading { align-items: center; }
  .contributors-list { grid-template-columns: 1fr; }
  .contributors-note { display: none; }
}
</style>
