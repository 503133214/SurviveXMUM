<template>
  <div class="markdown-container">
    <!-- 移动端目录抽屉开关 -->
    <button
      v-if="isMobileView && tocItems.length > 0"
      class="toc-drawer-toggle"
      @click="isTocDrawerOpen = !isTocDrawerOpen"
      :aria-expanded="isTocDrawerOpen.toString()"
      aria-label="目录"
    >
      ☰
    </button>

    <div class="main-content-area">
      <div ref="bodyEl" class="markdown-body" v-html="renderedHtml"></div>
    </div>

    <!-- 移动端遮罩 -->
    <div
      v-if="isTocDrawerOpen && isMobileView"
      class="toc-overlay"
      @click="isTocDrawerOpen = false"
    ></div>

    <!-- 目录 -->
    <aside
      v-if="tocItems.length > 0"
      class="toc-sidebar-area"
      :class="{ 'is-drawer-open': isTocDrawerOpen && isMobileView }"
      role="navigation"
    >
      <div class="toc-container">
        <button
          v-if="isMobileView"
          class="toc-drawer-close-btn"
          @click="isTocDrawerOpen = false"
          aria-label="关闭目录"
        >&times;</button>
        <div class="toc-title">目录</div>
        <ul class="toc-list">
          <li
            v-for="item in tocItems"
            :key="item.id"
            :class="[`toc-level-${item.level}`, { 'is-active': item.id === activeHeading }]"
            class="toc-item"
          >
            <a :href="'#' + item.id" @click="onTocClick(item.id, $event)">{{ item.text }}</a>
          </li>
        </ul>
      </div>
    </aside>
  </div>
</template>

<script>
import MarkdownIt from "markdown-it";
import DOMPurify from "dompurify";

const MOBILE_BREAKPOINT = 992;

// 链接默认在新标签打开时补 rel，防止 tabnabbing。
DOMPurify.addHook("afterSanitizeAttributes", (node) => {
  if (node.tagName === "A" && node.getAttribute("target") === "_blank") {
    node.setAttribute("rel", "noopener noreferrer");
  }
});

function slugify(s) {
  return String(s)
    .trim()
    .toLowerCase()
    .replace(/\s+/g, "-")
    .replace(/[^\w一-龥-]/g, "")
    .replace(/-{2,}/g, "-")
    .replace(/^-+|-+$/g, "");
}

export default {
  name: "MarkdownRenderer",
  props: {
    content: { type: String, required: true },
    // 当前文档所在目录，用于把相对图片/链接解析为绝对路径
    basePath: { type: String, default: "" },
  },
  data() {
    return {
      renderedHtml: "",
      tocItems: [],
      activeHeading: "",
      isTocDrawerOpen: false,
      isMobileView: false,
      scrollSpy: null,
    };
  },
  watch: {
    content: { immediate: true, handler() { this.render(); } },
    basePath() { this.render(); },
  },
  methods: {
    render() {
      const toc = [];
      const usedIds = new Set();
      const md = new MarkdownIt({ html: true, linkify: true, typographer: true });

      // 标题：注入 id 并收集目录
      const origHeading =
        md.renderer.rules.heading_open ||
        ((t, i, o, e, s) => s.renderToken(t, i, o));
      md.renderer.rules.heading_open = (tokens, idx, options, env, self) => {
        const token = tokens[idx];
        const level = parseInt(token.tag.substring(1), 10);
        const inline = tokens[idx + 1];
        if (inline && inline.type === "inline") {
          const text = inline.content;
          let id = slugify(text) || `h-${idx}`;
          let unique = id;
          let n = 1;
          while (usedIds.has(unique)) unique = `${id}-${n++}`;
          usedIds.add(unique);
          token.attrSet("id", unique);
          if (level >= 1 && level <= 4) toc.push({ level, text, id: unique });
        }
        return origHeading(tokens, idx, options, env, self);
      };

      // 链接：相对 .md 链接 → /docs 路由
      const base = this.basePath ? this.basePath.replace(/\/$/, "") + "/" : "";
      md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
        const token = tokens[idx];
        const hi = token.attrIndex("href");
        if (hi >= 0) {
          let href = token.attrs[hi][1];
          if (href && !/^(https?:|\/|#|mailto:)/.test(href)) {
            const clean = href.replace(/^\.\//, "");
            if (/\.md$/i.test(clean)) {
              token.attrs[hi][1] = `/docs/${base}${clean.replace(/\.md$/i, "")}`;
            } else {
              token.attrs[hi][1] = `/docs/${base}${clean}`;
            }
          }
          if (/^https?:/.test(href)) {
            token.attrSet("target", "_blank");
            token.attrSet("rel", "noopener noreferrer");
          }
        }
        return self.renderToken(tokens, idx, options);
      };

      // 图片：相对路径 → /docs/<dir>/...
      md.renderer.rules.image = (tokens, idx, options, env, self) => {
        const token = tokens[idx];
        const si = token.attrIndex("src");
        if (si >= 0) {
          let src = token.attrs[si][1];
          if (src && !/^(https?:|\/|data:)/.test(src)) {
            token.attrs[si][1] = `/docs/${base}${src.replace(/^\.\//, "")}`;
          }
        }
        token.attrSet("loading", "lazy");
        return self.renderToken(tokens, idx, options);
      };

      // 内容现在来自用户投稿（不可信）：先渲染再用 DOMPurify 消毒，移除 <script>/onclick 等。
      // 保留 <details>/<summary>、链接 target 等合法用法。
      const dirty = md.render(this.content || "");
      this.renderedHtml = DOMPurify.sanitize(dirty, {
        ADD_ATTR: ["target", "id", "loading"],
      });
      this.tocItems = toc;
      this.$nextTick(() => {
        this.enhanceCodeBlocks();
        this.setupScrollSpy();
      });
    },
    enhanceCodeBlocks() {
      const root = this.$refs.bodyEl;
      if (!root) return;
      root.querySelectorAll("pre").forEach((pre) => {
        if (pre.querySelector(".code-copy-btn")) return;
        pre.style.position = "relative";
        const btn = document.createElement("button");
        btn.className = "code-copy-btn";
        btn.type = "button";
        btn.textContent = "复制";
        btn.addEventListener("click", async () => {
          const code = pre.querySelector("code");
          try {
            await navigator.clipboard.writeText(code ? code.innerText : pre.innerText);
            btn.textContent = "已复制";
            setTimeout(() => (btn.textContent = "复制"), 1500);
          } catch {
            btn.textContent = "复制失败";
          }
        });
        pre.appendChild(btn);
      });
    },
    setupScrollSpy() {
      if (this.scrollSpy) this.scrollSpy.disconnect();
      if (this.tocItems.length === 0) return;
      this.scrollSpy = new IntersectionObserver(
        (entries) => {
          for (const entry of entries) {
            if (entry.isIntersecting) this.activeHeading = entry.target.id;
          }
        },
        { rootMargin: `-${70}px 0px -70% 0px`, threshold: 0 }
      );
      this.tocItems.forEach((item) => {
        const el = document.getElementById(item.id);
        if (el) this.scrollSpy.observe(el);
      });
    },
    onTocClick(id, event) {
      event.preventDefault();
      const el = document.getElementById(id);
      if (el) {
        el.scrollIntoView({ behavior: "smooth", block: "start" });
        history.replaceState(history.state, "", `#${id}`);
        this.activeHeading = id;
      }
      if (this.isMobileView) this.isTocDrawerOpen = false;
    },
    checkMobile() {
      this.isMobileView = window.innerWidth <= MOBILE_BREAKPOINT;
      if (!this.isMobileView) this.isTocDrawerOpen = false;
    },
  },
  mounted() {
    this.checkMobile();
    window.addEventListener("resize", this.checkMobile);
  },
  beforeUnmount() {
    window.removeEventListener("resize", this.checkMobile);
    if (this.scrollSpy) this.scrollSpy.disconnect();
  },
};
</script>

<style>
.markdown-container {
  display: flex;
  position: relative;
  width: 100%;
  max-width: 1400px;
  margin: 0 auto;
  gap: 32px;
  padding: 0 20px;
  align-items: flex-start;
}

.main-content-area {
  flex: 1;
  min-width: 0;
  margin: 0;
}

/* ---- markdown 正文（自带主题，不依赖 github-markdown-css，便于暗色） ---- */
.markdown-body {
  padding: 36px 40px;
  background-color: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  color: var(--text-body);
  font-size: 16px;
  line-height: 1.75;
  word-wrap: break-word;
  transition: box-shadow 0.3s ease, background-color 0.3s ease;
}
.markdown-body:hover { box-shadow: var(--shadow-md); }

.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4 {
  color: var(--text-primary);
  font-weight: 700;
  line-height: 1.3;
  margin: 1.6em 0 0.6em;
  scroll-margin-top: 80px;
}
.markdown-body h1 { font-size: 1.9rem; margin-top: 0; }
.markdown-body h2 {
  font-size: 1.5rem;
  padding-bottom: 0.3em;
  border-bottom: 1px solid var(--border);
}
.markdown-body h3 { font-size: 1.25rem; }
.markdown-body h4 { font-size: 1.05rem; }
.markdown-body p { margin: 0.9em 0; }
.markdown-body a { color: var(--brand-blue); font-weight: 500; }
.markdown-body ul,
.markdown-body ol { padding-left: 1.6em; margin: 0.8em 0; }
.markdown-body li { margin: 0.35em 0; }
.markdown-body img {
  max-width: 100%;
  height: auto;
  border-radius: var(--radius-sm);
  box-shadow: var(--shadow-sm);
  margin: 0.8em 0;
}
.markdown-body hr { border: none; border-top: 1px solid var(--border); margin: 2em 0; }

.markdown-body blockquote {
  border-left: 4px solid var(--brand);
  padding: 8px 18px;
  margin: 1.2em 0;
  background-color: var(--bg-subtle);
  color: var(--text-secondary);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
}
.markdown-body blockquote p { margin: 0.3em 0; }

.markdown-body table {
  border-collapse: collapse;
  width: 100%;
  margin: 1.4em 0;
  overflow: hidden;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border);
}
.markdown-body th,
.markdown-body td {
  border: 1px solid var(--border);
  padding: 10px 14px;
  text-align: left;
}
.markdown-body th { background-color: var(--bg-subtle); color: var(--text-primary); font-weight: 600; }
.markdown-body tr:nth-child(2n) { background-color: var(--bg-subtle); }

.markdown-body code {
  padding: 0.15em 0.4em;
  background-color: var(--bg-subtle);
  border: 1px solid var(--border);
  border-radius: 4px;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 0.88em;
  color: var(--brand-strong);
}
.markdown-body pre {
  position: relative;
  padding: 16px 18px;
  background-color: var(--bg-subtle);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  overflow-x: auto;
  margin: 1.2em 0;
}
.markdown-body pre code {
  padding: 0;
  background: none;
  border: none;
  color: var(--text-body);
}
.markdown-body details {
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  padding: 10px 16px;
  margin: 0.8em 0;
  background: var(--bg-surface);
}
.markdown-body summary {
  cursor: pointer;
  font-weight: 600;
  color: var(--text-primary);
}

.code-copy-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  padding: 3px 10px;
  font-size: 12px;
  color: var(--text-secondary);
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: 6px;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.2s ease, color 0.2s ease;
}
.markdown-body pre:hover .code-copy-btn { opacity: 1; }
.code-copy-btn:hover { color: var(--brand); border-color: var(--brand); }

/* ---- 目录 ---- */
.toc-sidebar-area { width: 240px; flex-shrink: 0; }
.toc-container {
  position: sticky;
  top: 80px;
  max-height: calc(100vh - 100px);
  overflow-y: auto;
  padding: 20px 16px;
  background-color: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
}
.toc-title {
  font-weight: 700;
  font-size: 15px;
  color: var(--text-primary);
  padding-bottom: 10px;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--border);
}
.toc-list { list-style: none; padding: 0; margin: 0; }
.toc-item a {
  display: block;
  padding: 6px 10px;
  color: var(--text-secondary);
  font-size: 13.5px;
  line-height: 1.5;
  border-radius: 6px;
  border-left: 2px solid transparent;
  transition: all 0.18s ease;
  text-decoration: none;
}
.toc-item a:hover { background: var(--bg-hover); color: var(--brand-blue); }
.toc-item.is-active a {
  color: var(--brand-blue);
  background: var(--bg-hover);
  border-left-color: var(--brand);
  font-weight: 600;
}
.toc-level-1 a { font-weight: 600; }
.toc-level-2 a { padding-left: 18px; }
.toc-level-3 a { padding-left: 30px; font-size: 13px; }
.toc-level-4 a { padding-left: 42px; font-size: 12.5px; color: var(--text-muted); }

/* ---- 移动端目录抽屉 ---- */
.toc-drawer-toggle {
  display: none;
  position: fixed;
  top: calc(var(--header-height) + 14px);
  right: 16px;
  z-index: 1005;
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 50%;
  background: var(--brand-gradient);
  color: #fff;
  font-size: 20px;
  cursor: pointer;
  box-shadow: var(--shadow-md);
}
.toc-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 1000;
}
.toc-drawer-close-btn {
  display: none;
  position: absolute;
  top: 8px;
  right: 12px;
  background: none;
  border: none;
  font-size: 26px;
  color: var(--text-muted);
  cursor: pointer;
}

@media (max-width: 992px) {
  .markdown-container { flex-direction: column; gap: 0; padding: 0 12px; }
  .toc-drawer-toggle { display: block; }
  .toc-drawer-close-btn { display: block; }
  .toc-sidebar-area {
    position: fixed;
    top: var(--header-height);
    right: 0;
    width: 280px;
    max-width: 85vw;
    height: calc(100vh - var(--header-height));
    z-index: 1001;
    transform: translateX(100%);
    transition: transform 0.3s ease;
  }
  .toc-sidebar-area.is-drawer-open { transform: translateX(0); }
  .toc-container {
    position: static;
    height: 100%;
    max-height: none;
    border-radius: 0;
    padding-top: 44px;
  }
}

@media (max-width: 768px) {
  .markdown-body { padding: 22px 18px; border-radius: var(--radius-sm); }
}
</style>
