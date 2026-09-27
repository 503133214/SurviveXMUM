<template>
  <!-- 紧凑：榜单里只放表情，名称走 title -->
  <span v-if="variant === 'strip' && earned.length" class="cb-strip">
    <span
      v-for="b in shown"
      :key="b.id"
      class="cb-pin"
      :title="`${b.name} · ${b.description}`"
      role="img"
      :aria-label="b.name"
    >{{ b.icon }}</span>
    <span v-if="overflow > 0" class="cb-more" :title="overflowTitle">+{{ overflow }}</span>
  </span>

  <!-- 完整：个人主页，含未达成的下一档与进度 -->
  <div v-else-if="variant === 'grid'" class="cb-grid">
    <div
      v-for="b in badges"
      :key="b.id"
      class="cb-card"
      :class="{ locked: !b.earned }"
    >
      <span class="cb-icon" aria-hidden="true">{{ b.icon }}</span>
      <span class="cb-copy">
        <strong>{{ b.name }}</strong>
        <small>{{ b.description }}</small>
        <span v-if="!b.earned" class="cb-progress">
          <span class="cb-bar"><i :style="{ width: percent(b) + '%' }"></i></span>
          <em>{{ b.progress }} / {{ b.target }}</em>
        </span>
      </span>
    </div>
  </div>
</template>

<script>
export default {
  name: 'ContributorBadges',
  props: {
    badges: { type: Array, default: () => [] },
    variant: { type: String, default: 'strip' },
    max: { type: Number, default: 4 },
  },
  computed: {
    earned() {
      return this.badges.filter((b) => b.earned)
    },
    shown() {
      return this.earned.slice(0, this.max)
    },
    overflow() {
      return Math.max(0, this.earned.length - this.max)
    },
    overflowTitle() {
      return this.earned.slice(this.max).map((b) => b.name).join('、')
    },
  },
  methods: {
    percent(b) {
      if (!b.target) return 0
      return Math.min(100, Math.round((b.progress / b.target) * 100))
    },
  },
}
</script>

<style scoped>
.cb-strip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  line-height: 1;
}

.cb-pin {
  font-size: 13px;
  cursor: default;
}

.cb-more {
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 700;
}

.cb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.cb-card {
  display: flex;
  gap: 12px;
  padding: 14px 15px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
}

/* 未获得的压暗但不隐藏——看得见目标才有得追 */
.cb-card.locked {
  border-style: dashed;
  background: none;
}
.cb-card.locked .cb-icon { filter: grayscale(1); opacity: .45; }
.cb-card.locked strong { color: var(--text-muted); }

.cb-icon { flex-shrink: 0; font-size: 22px; line-height: 1.3; }

.cb-copy { display: flex; min-width: 0; flex-direction: column; gap: 3px; }
.cb-copy strong { color: var(--text-primary); font-size: 14px; }
.cb-copy small { color: var(--text-muted); font-size: 12px; line-height: 1.5; }

.cb-progress { display: flex; align-items: center; gap: 8px; margin-top: 5px; }

.cb-bar {
  overflow: hidden;
  flex: 1;
  height: 4px;
  border-radius: 999px;
  background: var(--bg-hover, var(--border));
}
.cb-bar i {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: var(--text-muted);
}

.cb-progress em {
  color: var(--text-muted);
  font-size: 11px;
  font-style: normal;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 600px) {
  .cb-grid { grid-template-columns: 1fr; }
}
</style>
