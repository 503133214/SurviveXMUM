<!-- src/components/MarkdownRenderer.vue -->
<template>
  <div class="markdown-container" :class="{ 'body-freeze-for-drawer': isTocDrawerOpen && isMobileView }">
    <!-- Toggle Button for Mobile Drawer -->
    <button
        class="toc-drawer-toggle"
        @click="toggleTocDrawer"
        v-if="showTocToggle && tocItems.length > 0"
        aria-label="Toggle Table of Contents"
        :aria-expanded="isTocDrawerOpen.toString()"
    >
      ☰
    </button>

    <div class="main-content-area">
      <div class="markdown-body" v-html="renderedHtml"></div>
    </div>

    <!-- Overlay for Mobile Drawer -->
    <div
        class="toc-overlay"
        v-if="isTocDrawerOpen && isMobileView"
        @click="closeTocDrawer"
    ></div>

    <!-- Table of Contents Sidebar / Drawer -->
    <aside
        class="toc-sidebar-area"
        :class="{ 'is-drawer-open': isTocDrawerOpen && isMobileView }"
        v-if="tocItems.length > 0"
        role="navigation"
        aria-labelledby="toc-title-id"
    >
      <div class="toc-container">
        <button
            class="toc-drawer-close-btn"
            @click="closeTocDrawer"
            v-if="isMobileView"
            aria-label="Close Table of Contents"
        >
          &times;
        </button>
        <div class="toc-title" id="toc-title-id">目录</div>
        <div class="toc-content">
          <ul class="toc-list">
            <li
                v-for="item in tocItems"
                :key="item.id"
                :class="`toc-level-${item.level}`"
                class="toc-item"
            >
              <a :href="'#' + item.id" @click="handleTocLinkClick(item.id, $event)">
                {{ item.text }}
              </a>
            </li>
          </ul>
        </div>
      </div>
    </aside>
  </div>
</template>

<script>
import MarkdownIt from "markdown-it";
import 'github-markdown-css';

export default {
  name: "MarkdownRenderer",
  props: {
    content: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      tocItems: [],
      isTocDrawerOpen: false,
      isMobileView: false,
      showTocToggle: false,
    };
  },
  computed: {
    renderedHtml() {
      if (!this.content) {
        return "";
      }
      this.tocItems = [];
      const slugify = (s) => String(s).trim().toLowerCase().replace(/\s+/g, '-').replace(/[^\w\u4e00-\u9fa5\-]/g, '').replace(/\-\-+/g, '-').replace(/^-+|-+$/g, '');
      const md = new MarkdownIt({ html: true, linkify: true, typographer: true });
      const originalHeadingOpen = md.renderer.rules.heading_open || function (tokens, idx, options, env, self) { return self.renderToken(tokens, idx, options); };
      md.renderer.rules.heading_open = (tokens, idx, options, env, self) => {
        const token = tokens[idx];
        const level = parseInt(token.tag.substring(1));
        const contentToken = tokens[idx + 1];
        if (contentToken && contentToken.type === 'inline') {
          const text = contentToken.content;
          const id = slugify(text);
          this.tocItems.push({ level, text, id });
          token.attrSet('id', id);
        }
        return originalHeadingOpen(tokens, idx, options, env, self);
      };
      const defaultRender = md.renderer.rules.link_open || function (tokens, idx, options, env, self) { return self.renderToken(tokens, idx, options); };
      md.renderer.rules.link_open = function (tokens, idx, options, env, self) {
        const token = tokens[idx];
        const hrefIndex = token.attrIndex("href");
        if (hrefIndex >= 0) {
          const hrefAttr = token.attrs[hrefIndex];
          let originalHref = hrefAttr[1];
          if (originalHref && !originalHref.startsWith("http") && !originalHref.startsWith("/") && originalHref.endsWith(".md")) {
            hrefAttr[1] = `/docs/${originalHref.substring(0, originalHref.length - 3)}`;
          } else if (originalHref && originalHref.startsWith("./") && originalHref.endsWith(".md")) {
            hrefAttr[1] = `/docs/${originalHref.substring(2, originalHref.length - 3)}`;
          }
        }
        return defaultRender(tokens, idx, options, env, self);
      };
      return md.render(this.content);
    },
  },
  methods: {
    scrollToHeading(id, event) {
      if (event) event.preventDefault();
      const element = document.getElementById(id);
      if (element) {
        element.scrollIntoView({
          behavior: 'smooth',
          block: 'start',
        });
        window.history.pushState(null, null, `#${id}`);
      }
    },
    handleTocLinkClick(id, event) {
      this.scrollToHeading(id, event);
      if (this.isMobileView && this.isTocDrawerOpen) {
        this.closeTocDrawer();
      }
    },
    openTocDrawer() {
      this.isTocDrawerOpen = true;
      // Optional: Add class to body to prevent scrolling if needed
      // document.body.classList.add('no-scroll');
    },
    closeTocDrawer() {
      this.isTocDrawerOpen = false;
      // Optional: Remove class from body
      // document.body.classList.remove('no-scroll');
    },
    toggleTocDrawer() {
      if (this.isTocDrawerOpen) {
        this.closeTocDrawer();
      } else {
        this.openTocDrawer();
      }
    },
    checkIfMobileView() {
      const mobileBreakpoint = 992; // Same as CSS media query
      this.isMobileView = window.innerWidth <= mobileBreakpoint;
      this.showTocToggle = this.isMobileView;

      // If resizing from mobile to desktop and drawer was open, ensure it's closed
      // and styles are appropriate for desktop.
      if (!this.isMobileView && this.isTocDrawerOpen) {
        this.closeTocDrawer();
      }
    },
  },
  mounted() {
    this.checkIfMobileView();
    window.addEventListener('resize', this.checkIfMobileView);
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.checkIfMobileView);
    // Clean up body class if it was added
    // document.body.classList.remove('no-scroll');
  },
};
</script>

<style>
/* 确保导入的 github-markdown-css 样式能够正确应用 */
/* github-markdown-css 的样式已经通过 import 'github-markdown-css' 引入 */

:root {
  --header-height: 60px;
}

.markdown-container {
  display: flex;
  position: relative;
  width: 100%;
  max-width: 1200px;
  margin: 0 auto;
  gap: 24px;
}

.main-content-area {
  flex: 1;
  min-width: 0;
  max-width: 800px;
  margin: 20px 0;
  box-sizing: border-box;
}

/* 修改此处：减少自定义样式，避免与 github-markdown-css 冲突 */
.markdown-body {
  /* 保持基本的布局样式 */
  padding: 25px;
  box-sizing: border-box;
  background-color: #fff;
  border-radius: 6px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05), 0 1px 2px rgba(0,0,0,0.1);
  
  /* 确保文本颜色可见 */
  color: #24292f;
  
  /* 确保字体设置 */
  font-family: -apple-system,BlinkMacSystemFont,"Segoe UI","Noto Sans",Helvetica,Arial,sans-serif,"Apple Color Emoji","Segoe UI Emoji";
  font-size: 16px;
  line-height: 1.5;
  word-wrap: break-word;
}

/* Desktop TOC styles */
.toc-sidebar-area {
  width: 250px;
  margin-top: 20px;
  box-sizing: border-box;
}

.toc-container {
  width: 100%;
  position: sticky; /* Sticky on desktop */
  top: 20px;
  max-height: calc(100vh - 40px);
  overflow-y: auto;
  padding: 20px 15px;
  font-size: 14px;
  box-sizing: border-box;
  background-color: #fff;
  border-radius: 6px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05), 0 1px 2px rgba(0,0,0,0.1);
}

.toc-title {
  font-weight: 600;
  margin-bottom: 15px;
  font-size: 17px;
  color: #2c3e50;
  text-align: left;
  padding-left: 5px;
}

.toc-list {
  list-style-type: none;
  padding: 0;
  margin: 0;
  text-align: left;
  width: 100%;
}

.toc-item a {
  display: block;
  padding: 8px 10px;
  color: #555;
  text-decoration: none;
  border-radius: 4px;
  font-size: 13.5px;
  line-height: 1.5;
  transition: background-color 0.2s ease, color 0.2s ease, border-left-color 0.2s ease;
  width: 100%;
  box-sizing: border-box;
  text-align: left;
  border-left: 3px solid transparent;
}

.toc-item a:hover {
  background-color: #f0f4f8;
  color: #2980b9;
  border-left-color: #2980b9;
}

.toc-level-1 a { font-weight: 500; color: #34495e; }
.toc-level-2 a { padding-left: 25px; }
.toc-level-3 a { padding-left: 40px; font-size: 13px; }
.toc-level-4 a { padding-left: 55px; font-size: 12.5px; color: #7f8c8d; }
.toc-level-5 a, .toc-level-6 a { padding-left: 70px; font-size: 12px; color: #95a5a6; }


/* --- Drawer Specific Styles --- */
.toc-drawer-toggle {
  display: none; /* Hidden by default, shown via media query */
  position: fixed;
  /* top: 15px; */ /* 将在媒体查询中覆盖 */
  right: 15px;
  z-index: 1005;
  background-color: #2c3e50;
  color: white;
  border: none;
  padding: 10px 15px;
  border-radius: 50%;
  cursor: pointer;
  font-size: 20px;
  line-height: 1;
  box-shadow: 0 2px 5px rgba(0,0,0,0.2);
}
.toc-drawer-toggle:hover {
  background-color: #34495e;
}

.toc-overlay {
  display: none;
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.6);
  z-index: 999;
}

.toc-drawer-close-btn {
  display: none;
  position: absolute;
  top: 10px;
  right: 15px;
  background: none;
  border: none;
  font-size: 28px;
  color: #777;
  cursor: pointer;
  padding: 5px;
  line-height: 1;
  z-index: 10;
}
.toc-drawer-close-btn:hover {
  color: #333;
}

.body-freeze-for-drawer {
  /* overflow: hidden; */
}


/* --- Responsive Design for Drawer (Mobile) --- */
@media (max-width: 992px) {
  .toc-drawer-toggle {
    display: block;
    top: calc(var(--header-height) + 15px); /* 定位到页头下方，并增加15px间距 */
    /* right: 15px; */ /* 已在全局样式中设置 */
  }

  .main-content-area {
    margin: 20px 15px;
    order: 0;
  }

  .markdown-container {
    flex-direction: column;
    gap: 0;
  }

  .toc-sidebar-area {
    position: fixed;
    top: var(--header-height); /* 抽屉从页头下方开始 */
    right: 0;
    width: 280px;
    height: calc(100vh - var(--header-height)); /* 抽屉高度为视口高度减去页头高度 */
    background-color: #ffffff;
    box-shadow: -3px 0 10px rgba(0,0,0,0.1);
    transform: translateX(100%);
    transition: transform 0.3s ease-in-out;
    z-index: 1000;
    margin-top: 0;
    display: flex;
    flex-direction: column;
  }

  .toc-sidebar-area.is-drawer-open {
    transform: translateX(0);
  }

  .toc-container {
    position: static;
    max-height: none;
    overflow-y: auto;
    box-shadow: none;
    border-radius: 0;
    flex-grow: 1;
    padding-top: 50px; /* 为关闭按钮留出空间 */
    padding-bottom: 20px;
  }

  .toc-title {
    padding-left: 15px;
    margin-top: 0;
  }

  .toc-drawer-close-btn {
    display: block;
  }
}

@media (max-width: 768px) {
  .main-content-area {
    margin: 15px 10px;
  }
  .markdown-body {
    padding: 20px;
  }
  .toc-sidebar-area {
    width: 260px;
    /* top 和 height 已在 992px 断点中通过 var(--header-height) 设置，此处无需重复 */
  }
  .toc-drawer-toggle {
    top: calc(var(--header-height) + 10px); /* 较小屏幕上，与页头间距调整为10px */
    right: 10px;
    padding: 8px 12px;
    font-size: 18px;
  }
  .toc-item a {
    font-size: 13px;
  }
  .toc-level-3 a, .toc-level-4 a, .toc-level-5 a, .toc-level-6 a {
    font-size: 12.5px;
  }
}
</style>