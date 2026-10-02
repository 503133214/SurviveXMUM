<template>
  <div class="not-found-container">
    <div class="content-wrapper">
      <p class="nf-code">404</p>
      <h1 class="title">页面未找到</h1>
      <p class="subtitle">你访问的页面不存在，可能链接写错了，或者页面已经移动。</p>
      <div class="nf-actions">
        <button type="button" class="nf-btn primary" @click="openPalette()">
          <Search :size="16" :stroke-width="1.75" aria-hidden="true" />搜索文档
        </button>
        <router-link to="/" class="nf-btn">返回首页</router-link>
      </div>
    </div>
  </div>
</template>

<script>
import { Search } from "lucide-vue-next";
import { openPalette } from "@/composables/usePalette.js";

export default {
  name: "NotFoundPage", // 多词命名以满足 ESLint 规则
  components: { Search },
  methods: { openPalette },
};
</script>

<style scoped>
/* 404 只保留文字和两个按钮：不放光晕背景、图标方块，也不加英文补充句 */
.not-found-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100dvh - var(--header-height));
  padding: 0 16px;
  background: var(--bg-page);
  text-align: center;
}

.content-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  max-width: 460px;
}

.nf-code {
  margin: 0 0 6px;
  color: var(--text-muted);
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0;
  font-variant-numeric: tabular-nums;
}

.title {
  margin: 0 0 10px;
  color: var(--text-primary);
  font-size: 28px;
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: 0;
}

.subtitle {
  margin: 0 0 24px;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.nf-actions { display: flex; flex-wrap: wrap; justify-content: center; gap: 10px; }
.nf-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 36px;
  padding: 0 16px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  color: var(--text-primary);
  font: inherit;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background var(--dur), border-color var(--dur), color var(--dur);
}
.nf-btn:hover { border-color: var(--text-muted); color: var(--text-primary); text-decoration: none; }
.nf-btn.primary { border-color: var(--accent); background: var(--accent); color: var(--accent-contrast); }
.nf-btn.primary:hover { border-color: var(--accent-hover); background: var(--accent-hover); }

@media (max-width: 480px) {
  .title { font-size: 24px; }
}
</style>
