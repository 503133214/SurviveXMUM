<template>
  <div class="not-found-container">
    <div class="content-wrapper">
      <span class="nf-icon" aria-hidden="true"><MapPinOff :size="30" :stroke-width="1.75" /></span>
      <p class="nf-code">404</p>
      <h1 class="title">页面未找到</h1>
      <p class="subtitle">
        你访问的页面不存在，可能链接写错了，或者页面已经移动。<br />
        Sorry, the page you’re looking for doesn’t exist.
      </p>
      <div class="nf-actions">
        <button type="button" class="nf-btn primary" @click="openPalette()">
          <Search :size="16" :stroke-width="2" aria-hidden="true" />搜索文档
        </button>
        <router-link to="/" class="nf-btn">返回首页</router-link>
      </div>
    </div>
  </div>
</template>

<script>
import { MapPinOff, Search } from "lucide-vue-next";
import { openPalette } from "@/composables/usePalette.js";

export default {
  name: "NotFoundPage", // 多词命名以满足 ESLint 规则
  components: { MapPinOff, Search },
  methods: { openPalette },
};
</script>

<style scoped>
.not-found-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: calc(100dvh - var(--header-height));
  padding: 0 16px;
  background:
    radial-gradient(circle at 50% 38%, var(--accent-soft), transparent 42%),
    var(--bg-page);
  text-align: center;
}

.content-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  max-width: 460px;
}

.nf-icon {
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  margin-bottom: 20px;
  border: 1px solid var(--accent-soft-strong);
  border-radius: 18px;
  background: var(--bg-surface);
  color: var(--accent);
  box-shadow: var(--shadow-md);
}

.nf-code {
  margin-bottom: 6px;
  color: var(--accent);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.2em;
}

.title {
  margin-bottom: 12px;
  color: var(--text-primary);
  font-size: 2.2rem;
  font-weight: 800;
  letter-spacing: -0.03em;
}

.subtitle {
  margin-bottom: 28px;
  color: var(--text-secondary);
  font-size: 1rem;
  line-height: 1.7;
}

.nf-actions { display: flex; flex-wrap: wrap; justify-content: center; gap: 10px; }
.nf-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 42px;
  padding: 0 20px;
  border: 1px solid var(--border-strong);
  border-radius: 12px;
  background: var(--bg-surface);
  color: var(--text-primary);
  font: inherit;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.15s ease;
}
.nf-btn:hover { border-color: var(--text-muted); color: var(--text-primary); text-decoration: none; }
.nf-btn:active { transform: scale(0.98); }
.nf-btn.primary { border-color: var(--accent); background: var(--accent); color: var(--accent-contrast); }
.nf-btn.primary:hover { border-color: var(--accent-hover); background: var(--accent-hover); }

@media (max-width: 480px) {
  .title { font-size: 1.8rem; }
}
</style>
