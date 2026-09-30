<template>
  <div class="markdown-container" :class="{ 'is-embedded': embedded }">
    <div ref="bodyEl" class="markdown-body" v-html="renderedHtml" @click="onBodyClick"></div>

    <!-- 图片放大查看 -->
    <Teleport to="body">
      <transition name="lb">
        <div
          v-if="lightbox"
          class="md-lightbox"
          role="dialog"
          aria-modal="true"
          :aria-label="lightbox.alt || '图片预览'"
          @click="lightbox = null"
        >
          <img :src="lightbox.src" :alt="lightbox.alt" />
          <p v-if="lightbox.alt" class="md-lightbox-caption">{{ lightbox.alt }}</p>
          <button type="button" class="md-lightbox-close" aria-label="关闭预览">
            <X :size="20" :stroke-width="2" />
          </button>
        </div>
      </transition>
    </Teleport>
  </div>
</template>

<script>
import { h, render as renderVNode } from "vue";
import MarkdownIt from "markdown-it";
import DOMPurify from "dompurify";
import { ElMessage } from "element-plus";
import { X, Copy, Check, Info, Lightbulb, MessageSquareWarning, TriangleAlert, OctagonAlert } from "lucide-vue-next";
import { resolveDocAssetSrc, resolveDocHref } from "@/utils/docLinks.js";
import { slugify } from "@/utils/slug.js";

// 链接默认在新标签打开时补 rel，防止 tabnabbing。
DOMPurify.addHook("afterSanitizeAttributes", (node) => {
  if (node.tagName === "A" && node.getAttribute("target") === "_blank") {
    node.setAttribute("rel", "noopener noreferrer");
  }
});

// GitHub 风格提示块：> [!NOTE] / [!TIP] / [!IMPORTANT] / [!WARNING] / [!CAUTION]
const CALLOUTS = {
  NOTE: { label: "说明", icon: Info },
  TIP: { label: "提示", icon: Lightbulb },
  IMPORTANT: { label: "重要", icon: MessageSquareWarning },
  WARNING: { label: "注意", icon: TriangleAlert },
  CAUTION: { label: "警告", icon: OctagonAlert },
};
const CALLOUT_RE = /^\s*\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]\s*/i;

// 站内页面链接用路由跳转；附件、图片等静态文件仍交给浏览器
const FILE_RE = /\.(png|jpe?g|gif|webp|svg|pdf|zip|rar|7z|docx?|xlsx?|pptx?|txt|csv)(\?|#|$)/i;

export default {
  name: "MarkdownRenderer",
  components: { X },
  props: {
    content: { type: String, required: true },
    // 当前文档所在目录，用于把相对图片/链接解析为绝对路径
    basePath: { type: String, default: "" },
    // 编辑器和审核页只需要正文：不加标题锚点、不接管链接、不放大图片。
    embedded: { type: Boolean, default: false },
    // 编辑器预览里允许拖动右下角手柄缩放图片，并把新宽度写回 Markdown。
    resizable: { type: Boolean, default: false },
  },
  // toc：正文里的标题列表，由文档页渲染成右侧目录
  emits: ["resize-image", "toc"],
  data() {
    return {
      renderedHtml: "",
      lightbox: null,
      mountedIcons: [],
    };
  },
  watch: {
    content: { immediate: true, handler() { this.render(); } },
    basePath() { this.render(); },
    lightbox(open) {
      if (open) window.addEventListener("keydown", this.onLightboxKey);
      else window.removeEventListener("keydown", this.onLightboxKey);
    },
  },
  methods: {
    render() {
      const toc = [];
      const usedIds = new Set();
      let imgIndex = 0;
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
          if (level >= 1 && level <= 3) toc.push({ level, text: inline.children.map((c) => c.content).join("") || text, id: unique });
        }
        return origHeading(tokens, idx, options, env, self);
      };
      // 标题末尾的「#」锚点：悬停出现，点一下复制本节链接
      md.renderer.rules.heading_close = (tokens, idx, options, env, self) => {
        const open = tokens[idx - 2];
        const id = open && open.attrGet && open.attrGet("id");
        const anchor = !this.embedded && id
          ? `<a class="heading-anchor" href="#${md.utils.escapeHtml(id)}" aria-label="复制本节链接" title="复制本节链接">#</a>`
          : "";
        return anchor + self.renderToken(tokens, idx, options);
      };

      // 链接：规范化相对路径（含历史内容里的 ../..）并固定到 /docs 路由。
      md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
        const token = tokens[idx];
        const hi = token.attrIndex("href");
        if (hi >= 0) {
          const href = resolveDocHref(token.attrs[hi][1], this.basePath);
          token.attrs[hi][1] = href;
          if (/^https?:/i.test(href)) {
            token.attrSet("target", "_blank");
            token.attrSet("rel", "noopener noreferrer");
          }
        }
        return self.renderToken(tokens, idx, options);
      };

      // 图片：相对路径 → /docs/<dir>/...，并解析 =WxH 尺寸标记
      md.renderer.rules.image = (tokens, idx, options, env, self) => {
        const token = tokens[idx];
        const si = token.attrIndex("src");
        if (si >= 0) {
          let src = token.attrs[si][1];
          // 由 `![](url =500x)` 语法预处理而来的尺寸标记，转成 width/height 属性
          const sz = src.match(/#__sz=(\d+)(?:x(\d+))?$/);
          if (sz) {
            src = src.replace(/#__sz=\d+(?:x\d+)?$/, "");
            token.attrSet("width", sz[1]);
            if (sz[2]) token.attrSet("height", sz[2]);
          }
          src = resolveDocAssetSrc(src, this.basePath);
          token.attrs[si][1] = src;
        }
        token.attrSet("loading", "lazy");
        token.attrSet("decoding", "async");
        token.attrSet("data-img-index", String(imgIndex++));
        return self.renderToken(tokens, idx, options);
      };

      // `![](url =500x)` 是 Markdown 原生解析不了的，先把尺寸折叠进 URL 的 hash 标记，
      // 交给上面的 image 规则还原成 width/height 属性。
      const sized = (this.content || "").replace(
        /!\[([^\]]*)\]\(\s*(\S+?)\s+=(\d+)x(\d*)\s*\)/g,
        (_m, alt, url, w, h) => `![${alt}](${url}#__sz=${w}${h ? "x" + h : ""})`
      );

      // 内容现在来自用户投稿（不可信）：先渲染再用 DOMPurify 消毒，移除 <script>/onclick 等。
      // 保留 <details>/<summary>、链接 target 等合法用法。
      const dirty = md.render(sized);
      this.unmountIcons();
      this.renderedHtml = DOMPurify.sanitize(dirty, {
        ADD_ATTR: ["target", "id", "loading", "decoding", "width", "height"],
      });
      this.$nextTick(() => {
        this.enhanceCallouts();
        this.enhanceCodeBlocks();
        this.enhanceResizableImages();
        this.enhanceTables();
        this.$emit("toc", toc);
      });
    },
    // 往 v-html 生成的 DOM 里挂图标组件；重新渲染或卸载前统一清理
    mountIcon(container, icon, props = {}) {
      renderVNode(h(icon, { size: 16, strokeWidth: 2, "aria-hidden": "true", ...props }), container);
      this.mountedIcons.push(container);
    },
    unmountIcons() {
      for (const el of this.mountedIcons) renderVNode(null, el);
      this.mountedIcons = [];
    },
    enhanceCallouts() {
      const root = this.$refs.bodyEl;
      if (!root) return;
      root.querySelectorAll("blockquote").forEach((quote) => {
        const first = quote.firstElementChild;
        if (!first || first.tagName !== "P") return;
        const textNode = first.firstChild;
        if (!textNode || textNode.nodeType !== Node.TEXT_NODE) return;
        const m = textNode.textContent.match(CALLOUT_RE);
        if (!m) return;
        const type = m[1].toUpperCase();
        const meta = CALLOUTS[type];
        textNode.textContent = textNode.textContent.slice(m[0].length);
        if (first.firstChild && first.firstChild.nodeName === "BR") first.removeChild(first.firstChild);
        if (!first.textContent.trim() && !first.querySelector("img")) first.remove();

        quote.classList.add("callout", `callout-${type.toLowerCase()}`);
        const title = document.createElement("div");
        title.className = "callout-title";
        const icon = document.createElement("span");
        icon.className = "callout-icon";
        this.mountIcon(icon, meta.icon);
        title.append(icon, document.createTextNode(meta.label));
        quote.prepend(title);
      });
    },
    enhanceResizableImages() {
      if (!this.resizable) return;
      const root = this.$refs.bodyEl;
      if (!root) return;
      // 只处理 markdown 图片（带序号标记）；正文里原生 <img> 没有对应的
      // Markdown `![]()`，若也挂上手柄会把宽度写到错误的图片上。
      root.querySelectorAll("img[data-img-index]").forEach((img) => {
        if (img.parentElement && img.parentElement.classList.contains("img-resize-wrap")) return;
        const wrap = document.createElement("span");
        wrap.className = "img-resize-wrap";
        img.parentNode.insertBefore(wrap, img);
        wrap.appendChild(img);
        const handle = document.createElement("span");
        handle.className = "img-resize-handle";
        handle.title = "拖动调整图片大小";
        wrap.appendChild(handle);
        handle.addEventListener("pointerdown", (e) => this.startImageResize(e, img, handle));
      });
    },
    startImageResize(event, img, handle) {
      event.preventDefault();
      event.stopPropagation();
      const startX = event.clientX;
      const startWidth = img.getBoundingClientRect().width;
      try { handle.setPointerCapture(event.pointerId); } catch (_) { /* 忽略 */ }
      const onMove = (ev) => {
        const next = Math.max(40, Math.round(startWidth + (ev.clientX - startX)));
        img.style.width = next + "px";
        img.style.height = "auto";
      };
      const onUp = () => {
        handle.removeEventListener("pointermove", onMove);
        handle.removeEventListener("pointerup", onUp);
        const index = parseInt(img.getAttribute("data-img-index") || "0", 10);
        const width = Math.round(img.getBoundingClientRect().width);
        this.$emit("resize-image", { index, width });
      };
      handle.addEventListener("pointermove", onMove);
      handle.addEventListener("pointerup", onUp);
    },
    // 宽表在窄屏上要能横向滚动，否则右侧几列既看不见也够不着
    enhanceTables() {
      const root = this.$refs.bodyEl;
      if (!root) return;
      root.querySelectorAll("table").forEach((table) => {
        if (table.parentElement && table.parentElement.classList.contains("table-scroll")) return;
        const wrap = document.createElement("div");
        wrap.className = "table-scroll";
        table.parentNode.insertBefore(wrap, table);
        wrap.appendChild(table);
      });
    },
    enhanceCodeBlocks() {
      const root = this.$refs.bodyEl;
      if (!root) return;
      root.querySelectorAll("pre").forEach((pre) => {
        if (pre.querySelector(".code-copy-btn")) return;
        const btn = document.createElement("button");
        btn.className = "code-copy-btn";
        btn.type = "button";
        btn.setAttribute("aria-label", "复制代码");
        const icon = document.createElement("span");
        const label = document.createElement("span");
        label.textContent = "复制";
        btn.append(icon, label);
        this.mountIcon(icon, Copy, { size: 14 });
        btn.addEventListener("click", async () => {
          const code = pre.querySelector("code");
          try {
            await navigator.clipboard.writeText(code ? code.innerText : pre.innerText);
            this.mountIcon(icon, Check, { size: 14 });
            label.textContent = "已复制";
            btn.classList.add("done");
            setTimeout(() => {
              this.mountIcon(icon, Copy, { size: 14 });
              label.textContent = "复制";
              btn.classList.remove("done");
            }, 1600);
          } catch {
            label.textContent = "复制失败";
          }
        });
        pre.appendChild(btn);
      });
    },
    onBodyClick(e) {
      if (this.embedded) return;
      const anchor = e.target.closest("a");
      if (anchor && this.$refs.bodyEl.contains(anchor)) {
        this.onLinkClick(e, anchor);
        return;
      }
      const img = e.target.closest("img");
      if (img && !this.resizable) {
        this.lightbox = { src: img.currentSrc || img.src, alt: img.getAttribute("alt") || "" };
      }
    },
    onLinkClick(e, anchor) {
      const href = anchor.getAttribute("href") || "";
      const modified = e.metaKey || e.ctrlKey || e.shiftKey || e.altKey || e.button !== 0;

      if (anchor.classList.contains("heading-anchor")) {
        e.preventDefault();
        this.copySectionLink(href.slice(1));
        return;
      }
      if (href.startsWith("#") && !modified) {
        let id = href.slice(1);
        try { id = decodeURIComponent(id); } catch { /* 原样使用 */ }
        const target = document.getElementById(id);
        if (target) {
          e.preventDefault();
          target.scrollIntoView({ behavior: "smooth", block: "start" });
          history.replaceState(history.state, "", href);
        }
        return;
      }
      const internal = href.startsWith("/") && !href.startsWith("//") && !FILE_RE.test(href);
      if (!internal || modified || anchor.target || anchor.hasAttribute("download")) return;
      // 只接管前端路由认识的地址；/api/…、/wiki/…（图片存储）等仍交给浏览器
      const route = this.$router.resolve(href);
      if (route.matched.length && route.name !== "NotFound") {
        e.preventDefault();
        this.$router.push(href).catch(() => {});
      }
    },
    async copySectionLink(id) {
      const el = document.getElementById(id);
      if (el) el.scrollIntoView({ behavior: "smooth", block: "start" });
      history.replaceState(history.state, "", `#${id}`);
      try {
        await navigator.clipboard.writeText(window.location.href);
        ElMessage.success("已复制本节链接");
      } catch {
        /* 剪贴板不可用时至少地址栏已经更新 */
      }
    },
    onLightboxKey(e) {
      if (e.key === "Escape") this.lightbox = null;
    },
  },
  beforeUnmount() {
    this.unmountIcons();
    window.removeEventListener("keydown", this.onLightboxKey);
  },
};
</script>

<style>
/* ---- markdown 正文：平铺在页面上，不再包卡片；排版以阅读舒适为先 ---- */
.markdown-container { width: 100%; min-width: 0; }

.markdown-body {
  color: var(--text-body);
  font-size: 16px;
  line-height: 1.85;
  overflow-wrap: break-word;
}
.markdown-body > :first-child { margin-top: 0 !important; }
.markdown-body > :last-child { margin-bottom: 0 !important; }

.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4 {
  position: relative;
  color: var(--text-primary);
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.35;
  /* html 上已有 scroll-padding-top（顶栏高度 + 16px），两者会叠加，这里只补一点 */
  scroll-margin-top: 8px;
}
.markdown-body h1 { margin: 2.2em 0 0.7em; font-size: 1.85rem; }
.markdown-body h2 {
  margin: 2.4em 0 0.8em;
  padding-top: 1.2em;
  border-top: 1px solid var(--border);
  font-size: 1.5rem;
}
.markdown-body > h2:first-child { padding-top: 0; border-top: 0; }
.markdown-body h3 { margin: 1.9em 0 0.6em; font-size: 1.22rem; }
.markdown-body h4 { margin: 1.6em 0 0.5em; font-size: 1.05rem; }

.heading-anchor {
  margin-left: 0.4em;
  padding: 0 0.2em;
  border-radius: 4px;
  color: var(--text-muted) !important;
  font-weight: 500;
  text-decoration: none !important;
  opacity: 0;
  transition: opacity 0.15s ease, color 0.15s ease;
}
.markdown-body :is(h1, h2, h3, h4):hover .heading-anchor,
.heading-anchor:focus-visible { opacity: 1; }
.heading-anchor:hover { color: var(--accent) !important; }

.markdown-body p { margin: 1em 0; }
.markdown-body strong { color: var(--text-primary); font-weight: 650; }

.markdown-body a {
  color: var(--brand-blue);
  font-weight: 500;
  text-decoration: underline;
  text-decoration-color: var(--accent-soft-strong);
  text-decoration-thickness: 1.5px;
  text-underline-offset: 3px;
  transition: color 0.15s ease, text-decoration-color 0.15s ease;
}
.markdown-body a:hover { color: var(--accent-hover); text-decoration-color: currentColor; }
.markdown-body a[target="_blank"]:not(:has(img))::after {
  content: "↗";
  margin-left: 2px;
  font-size: 0.8em;
  text-decoration: none;
  opacity: 0.6;
}

.markdown-body ul,
.markdown-body ol { margin: 1em 0; padding-left: 1.5em; }
.markdown-body li { margin: 0.4em 0; padding-left: 0.2em; }
.markdown-body li::marker { color: var(--text-muted); }
.markdown-body ol > li::marker { font-weight: 600; font-variant-numeric: tabular-nums; }
.markdown-body li > ul,
.markdown-body li > ol { margin: 0.3em 0; }
.markdown-body input[type="checkbox"] { margin-right: 6px; accent-color: var(--accent); }

.markdown-body img {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 1.4em auto;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-subtle);
}
.markdown-container:not(.is-embedded) .markdown-body img { cursor: zoom-in; }
.markdown-body a img { cursor: pointer; }

/* 可缩放图片：用包裹层承载右下角拖动手柄，并保持居中 */
.markdown-body .img-resize-wrap {
  position: relative;
  display: block;
  width: fit-content;
  max-width: 100%;
  margin: 0.8em auto;
}
.markdown-body .img-resize-wrap img { margin: 0; }
.markdown-body .img-resize-handle {
  position: absolute;
  right: -7px;
  bottom: -7px;
  width: 16px;
  height: 16px;
  border: 2px solid var(--bg-surface, #fff);
  border-radius: 50%;
  background: var(--accent, #2563eb);
  box-shadow: var(--shadow-sm);
  cursor: nwse-resize;
  opacity: 0;
  transition: opacity 0.15s ease;
  touch-action: none;
}
.markdown-body .img-resize-wrap:hover .img-resize-handle,
.markdown-body .img-resize-handle:active { opacity: 1; }

.markdown-body hr {
  height: 1px;
  margin: 2.4em 0;
  border: none;
  background: linear-gradient(90deg, transparent, var(--border-strong), transparent);
}

/* 普通引用 */
.markdown-body blockquote {
  margin: 1.4em 0;
  padding: 2px 0 2px 18px;
  border-left: 3px solid var(--border-strong);
  color: var(--text-secondary);
}
.markdown-body blockquote p { margin: 0.5em 0; }

/* 提示块 */
.markdown-body blockquote.callout {
  --cl: var(--accent);
  --cl-bg: var(--accent-soft);
  padding: 14px 18px;
  border: 1px solid color-mix(in srgb, var(--cl) 22%, transparent);
  border-left: 3px solid var(--cl);
  border-radius: 12px;
  background: var(--cl-bg);
  color: var(--text-body);
}
.markdown-body .callout-tip { --cl: var(--success); --cl-bg: var(--success-soft); }
.markdown-body .callout-important { --cl: #7c3aed; --cl-bg: rgba(124, 58, 237, 0.07); }
html.dark .markdown-body .callout-important { --cl: #b69cff; --cl-bg: rgba(182, 156, 255, 0.1); }
.markdown-body .callout-warning { --cl: var(--warning); --cl-bg: var(--warning-soft); }
.markdown-body .callout-caution { --cl: var(--danger); --cl-bg: var(--danger-soft); }
.callout-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  color: var(--cl);
  font-size: 14px;
  font-weight: 700;
}
.callout-icon { display: inline-flex; }

/* 表格由 enhanceTables() 包进 .table-scroll。滚动容器必须是这个 div：
   给 <table> 自己加 overflow 不管用——表格的固有宽度仍会撑开父级，
   scrollWidth == clientWidth，看着有 overflow 其实滚不动。 */
.markdown-body .table-scroll {
  max-width: 100%;
  margin: 1.6em 0;
  overflow-x: auto;
  border: 1px solid var(--border);
  border-radius: 12px;
}
.markdown-body table {
  width: 100%;
  margin: 0;
  border-collapse: collapse;
  font-size: 14.5px;
  line-height: 1.6;
  font-variant-numeric: tabular-nums;
}
.markdown-body th,
.markdown-body td {
  padding: 10px 16px;
  border-bottom: 1px solid var(--border);
  text-align: left;
  vertical-align: top;
}
.markdown-body th + th,
.markdown-body td + td { border-left: 1px solid var(--border); }
.markdown-body th {
  background: var(--bg-subtle);
  color: var(--text-primary);
  font-size: 13.5px;
  font-weight: 650;
  white-space: nowrap;
}
.markdown-body tbody tr:last-child td { border-bottom: 0; }
.markdown-body tbody tr { transition: background 0.12s ease; }
.markdown-body tbody tr:hover { background: var(--bg-subtle); }

.markdown-body code {
  padding: 0.15em 0.4em;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--bg-subtle);
  color: var(--brand-strong);
  font-family: var(--font-mono);
  font-size: 0.86em;
}
.markdown-body pre {
  position: relative;
  margin: 1.4em 0;
  padding: 16px 18px;
  overflow-x: auto;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--bg-subtle);
  font-size: 13.5px;
  line-height: 1.7;
}
.markdown-body pre code {
  padding: 0;
  border: none;
  background: none;
  color: var(--text-body);
  font-size: inherit;
}
.markdown-body kbd {
  padding: 1px 6px;
  border: 1px solid var(--border);
  border-bottom-width: 2px;
  border-radius: 6px;
  background: var(--bg-surface);
  font-family: var(--font-mono);
  font-size: 0.82em;
}
.markdown-body mark { padding: 0 2px; border-radius: 3px; background: #fef08a; color: #1c1917; }

.markdown-body details {
  margin: 1em 0;
  padding: 0 16px;
  border: 1px solid var(--border);
  border-radius: 12px;
  background: var(--bg-surface);
  transition: background 0.15s ease;
}
.markdown-body details[open] { padding-bottom: 8px; background: var(--bg-subtle); }
.markdown-body summary {
  padding: 12px 0;
  color: var(--text-primary);
  font-weight: 600;
  cursor: pointer;
  list-style: none;
}
.markdown-body summary::-webkit-details-marker { display: none; }
.markdown-body summary::before {
  content: "";
  display: inline-block;
  width: 7px;
  height: 7px;
  margin: 0 12px 2px 2px;
  border-right: 2px solid var(--text-muted);
  border-bottom: 2px solid var(--text-muted);
  transform: rotate(-45deg);
  transition: transform 0.2s var(--ease-out);
}
.markdown-body details[open] > summary::before { transform: rotate(45deg); }

.code-copy-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 9px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: var(--bg-surface);
  color: var(--text-secondary);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}
.code-copy-btn span { display: inline-flex; }
.markdown-body pre:hover .code-copy-btn,
.code-copy-btn:focus-visible,
.code-copy-btn.done { opacity: 1; }
.code-copy-btn:hover { border-color: var(--border-strong); color: var(--text-primary); }
.code-copy-btn.done { border-color: var(--success); color: var(--success); }
@media (hover: none) { .code-copy-btn { opacity: 1; } }

/* ---- 图片放大 ---- */
.md-lightbox {
  position: fixed;
  inset: 0;
  z-index: 3500;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  padding: 48px 20px;
  background: rgba(8, 10, 20, 0.86);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  cursor: zoom-out;
}
.md-lightbox img {
  max-width: min(1400px, 100%);
  max-height: calc(100dvh - 120px);
  border-radius: 12px;
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.5);
  object-fit: contain;
}
.md-lightbox-caption { max-width: 720px; color: rgba(255, 255, 255, 0.8); font-size: 14px; text-align: center; }
.md-lightbox-close {
  position: absolute;
  top: max(16px, env(safe-area-inset-top));
  right: 16px;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  cursor: pointer;
}
.lb-enter-active, .lb-leave-active { transition: opacity 0.2s ease; }
.lb-enter-active img { transition: transform 0.3s var(--ease-out); }
.lb-enter-from, .lb-leave-to { opacity: 0; }
.lb-enter-from img { transform: scale(0.96); }

/* 编辑器 / 审核预览：保持原先紧凑的正文尺寸 */
.markdown-container.is-embedded .markdown-body { font-size: 15px; line-height: 1.75; }
.markdown-container.is-embedded .markdown-body h2 { padding-top: 0; border-top: 0; }

@media (max-width: 768px) {
  .markdown-body { font-size: 15.5px; line-height: 1.8; }
  .markdown-body h2 { font-size: 1.35rem; }
  .markdown-body h3 { font-size: 1.14rem; }
  .markdown-body th,
  .markdown-body td { padding: 9px 12px; }
}
</style>
