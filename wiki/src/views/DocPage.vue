<template>
  <el-container class="doc-page-container">
    <!-- 阅读进度条 -->
    <div class="reading-progress" :style="{ transform: `scaleX(${progress})` }"></div>

    <!-- 桌面端侧边栏 -->
    <el-aside v-if="!isMobileView" width="260px" class="doc-sidebar-container">
      <Sidebar :sidebar-items="tree" :current-path="docPath" @navigate="onNavigate" />
    </el-aside>

    <!-- 移动端抽屉式侧边栏 -->
    <el-drawer
      v-if="isMobileView"
      v-model="drawerVisible"
      title="目录导航"
      direction="ltr"
      size="280px"
    >
      <Sidebar
        :sidebar-items="tree"
        :current-path="docPath"
        @navigate="onNavigateAndCloseDrawer"
      />
    </el-drawer>

    <el-main class="doc-main-content">
      <el-button
        v-if="isMobileView"
        @click="drawerVisible = true"
        class="mobile-menu-toggle"
        :icon="MenuIcon"
        circle
        aria-label="打开目录"
      />

      <div class="doc-inner">
        <!-- 面包屑 -->
        <nav v-if="breadcrumbs.length" class="doc-breadcrumb">
          <router-link to="/">首页</router-link>
          <template v-for="(c, i) in breadcrumbs" :key="i">
            <span class="sep">/</span>
            <router-link v-if="c.path" :to="`/docs/${c.path}`">{{ c.label }}</router-link>
            <span v-else class="crumb">{{ c.icon ? c.icon + ' ' : '' }}{{ c.label }}</span>
          </template>
        </nav>

        <div v-if="isLoading" class="loading-state">
          <el-skeleton :rows="6" animated />
        </div>

        <template v-else>
          <div :key="docPath" class="doc-loaded">
          <header class="doc-header">
            <h1 class="doc-title">{{ title }}</h1>
            <div class="doc-meta">
              <span v-if="lastUpdated" class="meta-item">🕒 更新于 {{ lastUpdated }}</span>
              <a
                v-if="!errorLoading"
                class="meta-item edit-link"
                :href="editUrl"
                target="_blank"
                rel="noopener noreferrer"
              >✏️ 编辑此页</a>
            </div>
          </header>

          <MarkdownRenderer v-if="content" :content="content" :base-path="baseDir" />
          <el-empty v-else description="这篇文档还在撰写中，欢迎来贡献内容 🙌" />

          <!-- 上一篇 / 下一篇 -->
          <nav v-if="(prev || next) && !errorLoading" class="doc-pager">
            <button v-if="prev" class="pager-card" @click="onNavigate(prev.path)">
              <span class="pager-dir">← 上一篇</span>
              <span class="pager-title">{{ prev.icon }} {{ prev.title }}</span>
            </button>
            <span v-else></span>
            <button v-if="next" class="pager-card align-right" @click="onNavigate(next.path)">
              <span class="pager-dir">下一篇 →</span>
              <span class="pager-title">{{ next.icon }} {{ next.title }}</span>
            </button>
          </nav>
          </div>
        </template>
      </div>
    </el-main>
  </el-container>
</template>

<script>
import { markRaw } from "vue";
import MarkdownRenderer from "@/components/MarkdownRenderer.vue";
import Sidebar from "@/components/WikiSidebar.vue";
import { Menu } from "@element-plus/icons-vue";
import {
  tree,
  getPage,
  getAdjacent,
  getBreadcrumbs,
  HOME_PATH,
  EDIT_BASE,
} from "@/wiki";

const DOCS_BASE_PATH = "/docs/";
const MOBILE_BREAKPOINT = 767;

export default {
  name: "DocPage",
  components: { MarkdownRenderer, Sidebar },
  props: {
    pathMatch: { type: String, default: "" },
  },
  data() {
    return {
      tree,
      content: "",
      title: "",
      isLoading: false,
      errorLoading: false,
      drawerVisible: false,
      isMobileView: false,
      MenuIcon: markRaw(Menu),
      resizeTimeout: null,
      progress: 0,
    };
  },
  computed: {
    docPath() {
      return this.pathMatch || HOME_PATH;
    },
    baseDir() {
      const p = this.docPath;
      return p.includes("/") ? p.slice(0, p.lastIndexOf("/")) : "";
    },
    pageMeta() {
      return getPage(this.docPath);
    },
    breadcrumbs() {
      return getBreadcrumbs(this.docPath);
    },
    prev() {
      return getAdjacent(this.docPath).prev;
    },
    next() {
      return getAdjacent(this.docPath).next;
    },
    lastUpdated() {
      const iso = this.pageMeta?.lastUpdated;
      if (!iso) return "";
      const d = new Date(iso);
      return Number.isNaN(d.getTime())
        ? ""
        : `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
    },
    editUrl() {
      return `${EDIT_BASE}/${this.docPath}.md`;
    },
  },
  watch: {
    docPath: {
      immediate: true,
      handler(p) {
        this.fetchMarkdown(p);
      },
    },
  },
  methods: {
    async fetchMarkdown(path) {
      this.isLoading = true;
      this.errorLoading = false;
      this.content = "";
      this.title = "";
      try {
        const response = await fetch(`${DOCS_BASE_PATH}${path}.md`);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        let raw = await response.text();

        // 去掉 YAML frontmatter（已被清单解析过）
        raw = raw.replace(/^﻿?---\r?\n[\s\S]*?\r?\n---\r?\n?/, "");

        // 若正文首行是 H1，则用它作标题并从正文移除，避免与页头重复
        const h1 = raw.match(/^\s*#\s+(.+?)\s*#*\s*$/m);
        let h1Text = "";
        if (h1 && raw.indexOf(h1[0]) < 4) {
          h1Text = h1[1].trim();
          raw = raw.replace(h1[0], "").replace(/^\s*\n/, "");
        }

        this.title = this.pageMeta?.title || h1Text || path.split("/").pop();
        this.content = raw.trim();
      } catch (e) {
        this.errorLoading = true;
        this.title = "页面未找到";
        this.content = `> 无法加载文档 \`${path}.md\`。\n\n请确认文件存在，或返回[首页](/)继续浏览。`;
      } finally {
        this.isLoading = false;
        this.scrollToTopOrHash();
      }
    },
    scrollToTopOrHash() {
      this.$nextTick(() => {
        if (this.$route.hash) {
          const el = document.getElementById(this.$route.hash.slice(1));
          if (el) {
            el.scrollIntoView({ behavior: "smooth" });
            return;
          }
        }
        window.scrollTo({ top: 0, behavior: "smooth" });
      });
    },
    onNavigate(newPage) {
      this.$router.push(`/docs/${newPage}`);
    },
    onNavigateAndCloseDrawer(newPage) {
      this.onNavigate(newPage);
      this.drawerVisible = false;
    },
    checkMobileView() {
      this.isMobileView = window.innerWidth <= MOBILE_BREAKPOINT;
    },
    handleResize() {
      clearTimeout(this.resizeTimeout);
      this.resizeTimeout = setTimeout(this.checkMobileView, 100);
    },
    handleScroll() {
      const h = document.documentElement;
      const scrollable = h.scrollHeight - h.clientHeight;
      this.progress = scrollable > 0 ? Math.min(h.scrollTop / scrollable, 1) : 0;
    },
  },
  mounted() {
    this.checkMobileView();
    window.addEventListener("resize", this.handleResize);
    window.addEventListener("scroll", this.handleScroll, { passive: true });
  },
  beforeUnmount() {
    window.removeEventListener("resize", this.handleResize);
    window.removeEventListener("scroll", this.handleScroll);
    clearTimeout(this.resizeTimeout);
  },
};
</script>

<style scoped>
.doc-page-container {
  min-height: calc(100vh - var(--header-height));
  background-color: var(--bg-page);
}

/* 阅读进度条 */
.reading-progress {
  position: fixed;
  top: var(--header-height);
  left: 0;
  right: 0;
  height: 3px;
  background: var(--brand-gradient);
  transform-origin: 0 50%;
  transform: scaleX(0);
  z-index: 999;
  transition: transform 0.1s linear;
}

.doc-loaded {
  animation: fade-up 0.5s var(--ease-out) both;
}
.doc-sidebar-container {
  background-color: var(--bg-surface);
  border-right: 1px solid var(--border);
}
.doc-main-content {
  padding: 28px 24px 60px;
  position: relative;
}
.doc-inner {
  max-width: 1400px;
  margin: 0 auto;
}

/* 面包屑 */
.doc-breadcrumb {
  font-size: 13px;
  color: var(--text-muted);
  margin: 0 auto 16px;
  max-width: 1360px;
  padding: 0 20px;
}
.doc-breadcrumb a { color: var(--text-secondary); }
.doc-breadcrumb a:hover { color: var(--brand-blue); text-decoration: none; }
.doc-breadcrumb .sep { margin: 0 8px; color: var(--border-strong); }
.doc-breadcrumb .crumb { color: var(--text-primary); font-weight: 500; }

/* 页头 */
.doc-header {
  max-width: 1360px;
  margin: 0 auto 8px;
  padding: 0 20px;
}
.doc-title {
  font-size: clamp(2rem, 4vw, 2.8rem);
  font-weight: 800;
  line-height: 1.15;
  letter-spacing: -0.03em;
  color: var(--text-primary);
  margin-bottom: 12px;
}
.doc-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  font-size: 13px;
  color: var(--text-muted);
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border);
  margin-bottom: 8px;
}
.edit-link { color: var(--text-muted); }
.edit-link:hover { color: var(--brand); text-decoration: none; }

.loading-state {
  max-width: 900px;
  margin: 0 auto;
  background: var(--bg-surface);
  padding: 30px;
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
}

/* 上一篇/下一篇 */
.doc-pager {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  max-width: 1360px;
  margin: 32px auto 0;
  padding: 0 20px;
}
.pager-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px 20px;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  cursor: pointer;
  text-align: left;
  transition: all 0.2s ease;
}
.pager-card:hover {
  border-color: var(--brand);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}
.pager-card.align-right { text-align: right; }
.pager-dir { font-size: 12px; color: var(--text-muted); }
.pager-title { font-size: 15px; font-weight: 600; color: var(--text-primary); }

.mobile-menu-toggle {
  position: fixed;
  bottom: 24px;
  left: 20px;
  z-index: 999;
  width: 48px;
  height: 48px;
  background: var(--brand-gradient);
  color: #fff;
  border: none;
  box-shadow: var(--shadow-md);
}

@media (max-width: 767px) {
  .doc-main-content { padding: 16px 8px 50px; }
  .doc-title { font-size: 1.7rem; }
  .doc-pager { grid-template-columns: 1fr; }
}
</style>
