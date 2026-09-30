<template>
  <footer class="site-footer" :class="{ compact }">
    <div class="sf-inner">
      <div class="sf-brand">
        <img src="/svg/Text_logo_hor.svg" alt="XMUM Wiki" class="sf-logo" />
        <p>厦门大学马来西亚分校学生自发维护的生存指南。内容由社区投稿、管理员审核后发布。</p>
        <button type="button" class="sf-search" @click="openPalette()">
          <Search :size="14" :stroke-width="2" aria-hidden="true" />
          随时按 <span class="ui-kbd">{{ shortcut }}</span> 搜索
        </button>
      </div>

      <nav class="sf-col" aria-label="浏览">
        <h2>浏览</h2>
        <router-link :to="`/docs/${HOME_PATH}`">文档首页</router-link>
        <router-link to="/tags">标签</router-link>
        <router-link to="/changes">站点动态</router-link>
        <router-link to="/contributors">贡献榜</router-link>
      </nav>

      <nav class="sf-col" aria-label="参与">
        <h2>参与</h2>
        <router-link to="/docs/贡献指南">贡献指南</router-link>
        <router-link to="/edit">写文章</router-link>
        <router-link to="/feedback">意见反馈</router-link>
        <a :href="REPO" target="_blank" rel="noopener noreferrer">GitHub ↗</a>
      </nav>

      <nav class="sf-col" aria-label="开发者">
        <h2>开发者</h2>
        <router-link to="/docs/api/api-overview">API 概览</router-link>
        <router-link to="/docs/api/endpoints">接口参考</router-link>
        <router-link to="/docs/api/development">本地开发</router-link>
      </nav>
    </div>

    <div class="sf-bottom">
      <span>© 2023–{{ year }} XMUM Wiki Team</span>
      <span>Released under the GPL-3.0 License</span>
    </div>
  </footer>
</template>

<script>
import { Search } from "lucide-vue-next";
import { HOME_PATH, REPO } from "@/wiki";
import { openPalette } from "@/composables/usePalette.js";

export default {
  name: "SiteFooter",
  components: { Search },
  props: {
    // 文档页里用：去掉上方大留白，宽度跟随正文区
    compact: { type: Boolean, default: false },
  },
  data() {
    const isMac = typeof navigator !== "undefined" && /Mac|iPhone|iPad/i.test(navigator.platform || navigator.userAgent || "");
    return { HOME_PATH, REPO, year: new Date().getFullYear(), shortcut: isMac ? "⌘K" : "Ctrl K" };
  },
  methods: { openPalette },
};
</script>

<style scoped>
.site-footer {
  margin-top: 32px;
  border-top: 1px solid var(--border);
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-size: 13.5px;
}

.sf-inner {
  display: grid;
  grid-template-columns: minmax(0, 2fr) repeat(3, minmax(0, 1fr));
  gap: 40px;
  max-width: var(--maxw);
  margin: 0 auto;
  padding: 56px 24px 40px;
}

.sf-logo { display: block; height: 22px; width: auto; margin-bottom: 14px; }
html.dark .sf-logo { filter: brightness(0) invert(1); }
.sf-brand p { max-width: 340px; margin-bottom: 16px; line-height: 1.7; }

.sf-search {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px 6px 10px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--bg-surface);
  color: var(--text-secondary);
  font: inherit;
  font-size: 12.5px;
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease;
}
.sf-search:hover { border-color: var(--border-strong); color: var(--text-primary); }

.sf-col { display: flex; flex-direction: column; gap: 10px; }
.sf-col h2 {
  margin-bottom: 4px;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 650;
}
.sf-col a { width: fit-content; color: var(--text-secondary); }
.sf-col a:hover { color: var(--text-primary); text-decoration: none; }

.sf-bottom {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px;
  max-width: var(--maxw);
  margin: 0 auto;
  padding: 18px 24px 28px;
  border-top: 1px solid var(--border);
  color: var(--text-muted);
  font-size: 12.5px;
}

.site-footer.compact { margin-top: 0; }
.site-footer.compact .sf-inner { padding-top: 40px; }

@media (max-width: 860px) {
  .sf-inner { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 28px 20px; }
  .sf-brand { grid-column: 1 / -1; }
}
@media (max-width: 520px) {
  .sf-inner { grid-template-columns: repeat(2, minmax(0, 1fr)); padding: 40px 16px 28px; }
  .sf-bottom { padding: 16px 16px calc(24px + env(safe-area-inset-bottom)); }
}
</style>
