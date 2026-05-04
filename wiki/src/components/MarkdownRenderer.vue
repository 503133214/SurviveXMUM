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
  max-width: 1400px;
  margin: 0 auto;
  gap: 32px;
  padding: 0 20px;
}

.main-content-area {
  flex: 1;
  min-width: 0;
  max-width: 900px;
  margin: 20px 0;
  box-sizing: border-box;
}

.markdown-body {
  padding: 32px;
  box-sizing: border-box;
  background-color: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
  color: #24292f;
  font-family: -apple-system,BlinkMacSystemFont,"Segoe UI","Noto Sans",Helvetica,Arial,"PingFang SC","Microsoft YaHei",sans-serif,"Apple Color Emoji","Segoe UI Emoji";
  font-size: 16px;
  line-height: 1.7;
  word-wrap: break-word;
  transition: box-shadow 0.3s ease;
}

.markdown-body:hover {
  box-shadow: 0 4px 16px rgba(0,0,0,0.1);
}

/* Desktop TOC styles */
.toc-sidebar-area {
  width: 280px;
  margin-top: 20px;
  box-sizing: border-box;
}

.toc-container {
  width: 100%;
  position: sticky;
  top: 80px;
  max-height: calc(100vh - 100px);
  overflow-y: auto;
  padding: 24px 18px;
  font-size: 14px;
  box-sizing: border-box;
  background-color: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
  transition: box-shadow 0.3s ease;
}

.toc-container:hover {
  box-shadow: 0 4px 16px rgba(0,0,0,0.1);
}

.toc-title {
  font-weight: 700;
  margin-bottom: 18px;
  font-size: 18px;
  color: #2c3e50;
  text-align: left;
  padding-left: 5px;
  padding-bottom: 12px;
  border-bottom: 2px solid #e4e7ed;
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
  padding: 10px 12px;
  color: #555;
  text-decoration: none;
  border-radius: 6px;
  font-size: 14px;
  line-height: 1.6;
  transition: all 0.2s ease;
  width: 100%;
  box-sizing: border-box;
  text-align: left;
  border-left: 3px solid transparent;
  position: relative;
}

.toc-item a:hover {
  background-color: #f0f7ff;
  color: #0c64c1;
  border-left-color: #42b983;
  padding-left: 16px;
}

.toc-item a:active {
  color: #42b983;
  background-color: #e6f7ff;
  border-left-color: #0c64c1;
}

.toc-level-1 a { font-weight: 600; color: #2c3e50; font-size: 14.5px; }
.toc-level-2 a { padding-left: 28px; }
.toc-level-3 a { padding-left: 44px; font-size: 13.5px; }
.toc-level-4 a { padding-left: 60px; font-size: 13px; color: #7f8c8d; }
.toc-level-5 a, .toc-level-6 a { padding-left: 76px; font-size: 12.5px; color: #95a5a6; }

.toc-level-1 a:hover { padding-left: 16px; }
.toc-level-2 a:hover { padding-left: 32px; }
.toc-level-3 a:hover { padding-left: 48px; }
.toc-level-4 a:hover { padding-left: 64px; }
.toc-level-5 a:hover, .toc-level-6 a:hover { padding-left: 80px; }


/* --- Drawer Specific Styles --- */
.toc-drawer-toggle {
  display: none;
  position: fixed;
  right: 20px;
  z-index: 1005;
  background: linear-gradient(135deg, #42b983 0%, #3498db 100%);
  color: white;
  border: none;
  padding: 12px 16px;
  border-radius: 50%;
  cursor: pointer;
  font-size: 22px;
  line-height: 1;
  box-shadow: 0 4px 16px rgba(66, 185, 131, 0.4);
  transition: all 0.3s ease;
}

.toc-drawer-toggle:hover {
  transform: scale(1.1);
  box-shadow: 0 6px 20px rgba(66, 185, 131, 0.5);
}

.toc-drawer-toggle:active {
  transform: scale(0.95);
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
  .markdown-container {
    padding: 0 12px;
  }
  
  .main-content-area {
    margin: 12px 0;
  }
  
  .markdown-body {
    padding: 20px 16px;
    border-radius: 8px;
  }
  
  .toc-sidebar-area {
    width: 280px;
  }
  
  .toc-drawer-toggle {
    top: calc(var(--header-height) + 12px);
    right: 12px;
    padding: 10px 14px;
    font-size: 20px;
  }
  
  .toc-item a {
    font-size: 13px;
    padding: 8px 10px;
  }
  
  .toc-level-3 a, .toc-level-4 a, .toc-level-5 a, .toc-level-6 a {
    font-size: 12.5px;
  }
}
</style>