<template>
  <Teleport to="body">
    <transition name="cp">
      <div v-if="palette.open" class="cp-overlay" @mousedown.self="close">
        <div
          class="cp-panel"
          role="dialog"
          aria-modal="true"
          aria-label="搜索文档"
          @keydown="onKeydown"
        >
          <div class="cp-input-row">
            <Search class="cp-input-icon" :size="18" :stroke-width="2" aria-hidden="true" />
            <input
              ref="input"
              v-model="palette.query"
              class="cp-input"
              type="search"
              placeholder="搜索文档、小标题或标签…"
              autocomplete="off"
              spellcheck="false"
              role="combobox"
              aria-expanded="true"
              aria-controls="cp-listbox"
              :aria-activedescendant="activeId"
            />
            <button type="button" class="cp-esc" @click="close">
              <span class="ui-kbd">esc</span>
            </button>
          </div>

          <div ref="list" id="cp-listbox" class="cp-body" role="listbox" aria-label="搜索结果">
            <div v-if="matchedTags.length" class="cp-tags">
              <span class="cp-group-label">标签</span>
              <button
                v-for="t in matchedTags"
                :key="t.tag"
                type="button"
                class="cp-tag"
                @click="goTag(t.tag)"
              >#{{ t.tag }}<span>{{ t.count }}</span></button>
            </div>

            <template v-for="group in groups" :key="group.label">
              <div class="cp-group-label">{{ group.label }}</div>
              <div
                v-for="item in group.items"
                :id="`cp-opt-${item.index}`"
                :key="item.key"
                class="cp-item"
                :class="{ active: item.index === activeIndex }"
                role="option"
                :aria-selected="item.index === activeIndex"
                @mousemove="activeIndex = item.index"
                @click="run(item)"
              >
                <span class="cp-item-icon" aria-hidden="true">
                  <WikiIcon v-if="item.wiki" v-bind="item.wiki" :size="16" />
                  <component :is="item.icon" v-else :size="16" :stroke-width="1.75" />
                </span>
                <span class="cp-item-body">
                  <span class="cp-item-title" v-html="item.titleHtml"></span>
                  <span v-if="item.sub" class="cp-item-sub">{{ item.sub }}</span>
                </span>
                <span class="cp-item-enter" aria-hidden="true">↵</span>
              </div>
            </template>

            <div v-if="query && !flatItems.length && !matchedTags.length" class="cp-empty">
              <p class="cp-empty-title">没有找到 “{{ query }}”</p>
              <p>试试更短的关键词，或者去 <a href="/tags" @click.prevent="go('/tags')">按标签浏览</a>。</p>
            </div>
          </div>

          <div class="cp-foot">
            <span><span class="ui-kbd">↑</span><span class="ui-kbd">↓</span> 选择</span>
            <span><span class="ui-kbd">↵</span> 打开</span>
            <span class="cp-foot-hide-sm"><span class="ui-kbd">esc</span> 关闭</span>
            <span class="cp-foot-count">共 {{ pages.length }} 篇文档</span>
          </div>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<script>
import { markRaw } from "vue";
import { Search, Tags, History, Trophy, SquarePen, SunMoon } from "lucide-vue-next";
import WikiIcon from "@/components/WikiIcon.vue";
import { pages, categories, searchPages, searchTags, getPage, HOME_PATH } from "@/wiki";
import { palette, closePalette, openPalette } from "@/composables/usePalette.js";
import { readRecent } from "@/utils/recentPages.js";
import { slugify } from "@/utils/slug.js";
import { useTheme } from "@/composables/useTheme.js";
import { useUserStore } from "@/store/userStore.js";

function escapeHtml(s) {
  return String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
}
function escapeRe(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function firstPagePath(node) {
  for (const child of node.children || []) {
    if (child.type === "page") return child.path;
    const deep = firstPagePath(child);
    if (deep) return deep;
  }
  return null;
}

export default {
  name: "CommandPalette",
  components: { Search, WikiIcon },
  setup() {
    const { toggleTheme } = useTheme();
    return { toggleTheme };
  },
  data() {
    return { palette, pages, activeIndex: 0, recent: [], lastFocus: null };
  },
  computed: {
    query() {
      return (this.palette.query || "").trim();
    },
    matchedTags() {
      return this.query ? searchTags(this.query, 6) : [];
    },
    groups() {
      const groups = this.query ? this.resultGroups() : this.idleGroups();
      // 给每一项一个全局序号，键盘上下键在所有分组之间连续移动
      let index = 0;
      return groups
        .filter((g) => g.items.length)
        .map((g) => ({ ...g, items: g.items.map((item) => ({ ...item, index: index++ })) }));
    },
    flatItems() {
      return this.groups.flatMap((g) => g.items);
    },
    activeId() {
      return this.flatItems.length ? `cp-opt-${this.activeIndex}` : undefined;
    },
  },
  watch: {
    "palette.open"(open) {
      if (open) {
        this.lastFocus = document.activeElement;
        this.recent = readRecent();
        this.activeIndex = 0;
        document.documentElement.style.overflow = "hidden";
        this.$nextTick(() => this.$refs.input?.focus());
      } else {
        document.documentElement.style.overflow = "";
        if (this.lastFocus && this.lastFocus.focus) this.lastFocus.focus({ preventScroll: true });
      }
    },
    query() {
      this.activeIndex = 0;
      if (this.$refs.list) this.$refs.list.scrollTop = 0;
    },
    activeIndex() {
      this.$nextTick(() => {
        const el = document.getElementById(`cp-opt-${this.activeIndex}`);
        if (el) el.scrollIntoView({ block: "nearest" });
      });
    },
  },
  methods: {
    idleGroups() {
      const recent = this.recent
        .map((r) => {
          // 标题/图标以最新的内容清单为准；清单里已经没有的页面就不再推荐
          const live = getPage(r.path);
          if (pages.length && !live && r.path !== HOME_PATH) return null;
          return live ? { ...r, title: live.title, icon: live.icon || r.icon, category: live.category } : r;
        })
        .filter(Boolean)
        .slice(0, 5)
        .map((p) => ({
          key: `recent:${p.path}`,
          wiki: { icon: p.icon, title: p.title, category: p.category },
          titleHtml: escapeHtml(p.title),
          sub: p.category || "",
          to: `/docs/${p.path}`,
        }));

      const cats = categories()
        .map((c) => ({ cat: c, path: firstPagePath(c) }))
        .filter((c) => c.path)
        .map(({ cat, path }) => ({
          key: `cat:${cat.slug}`,
          wiki: { icon: cat.icon, category: cat.slug, title: cat.label, kind: "category" },
          titleHtml: escapeHtml(cat.label),
          sub: cat.description || "",
          to: `/docs/${path}`,
        }));

      const actions = [
        { key: "go:tags", icon: markRaw(Tags), titleHtml: "按标签浏览", to: "/tags" },
        { key: "go:changes", icon: markRaw(History), titleHtml: "站点动态", sub: "最近通过审核的修改", to: "/changes" },
        { key: "go:contributors", icon: markRaw(Trophy), titleHtml: "贡献榜", to: "/contributors" },
        useUserStore().isLoggedIn
          ? { key: "go:edit", icon: markRaw(SquarePen), titleHtml: "写一篇新文章", to: "/edit" }
          : { key: "go:guide", icon: markRaw(SquarePen), titleHtml: "如何贡献内容", to: "/docs/贡献指南" },
        { key: "act:theme", icon: markRaw(SunMoon), titleHtml: "切换亮 / 暗主题", action: "theme" },
      ];

      return [
        { label: "最近浏览", items: recent },
        { label: "分类", items: cats },
        { label: "快捷操作", items: actions },
      ];
    },
    resultGroups() {
      const results = searchPages(this.query, 12).map(({ page, matchedHeadings }) => {
        const heading = matchedHeadings[0];
        const titleHit = this.hits(page.title);
        // 标题没命中、只命中了小标题时，直接跳到那一节
        const hash = heading && !titleHit ? `#${slugify(heading)}` : "";
        return {
          key: `page:${page.path}`,
          wiki: { icon: page.icon, title: page.title, category: page.category },
          titleHtml: this.highlight(page.title),
          sub: [page.category || "首页", heading ? `§ ${heading}` : page.description]
            .filter(Boolean)
            .join(" · "),
          to: `/docs/${page.path}${hash}`,
        };
      });
      return [{ label: "文档", items: results }];
    },
    hits(text) {
      const t = String(text || "").toLowerCase();
      return this.query.toLowerCase().split(/\s+/).filter(Boolean).some((q) => t.includes(q));
    },
    highlight(text) {
      const terms = this.query.split(/\s+/).filter(Boolean).map(escapeRe);
      if (!terms.length) return escapeHtml(text);
      const re = new RegExp(`(${terms.join("|")})`, "gi");
      // 先在原文上分段匹配、再逐段转义：若先转义再匹配，搜索 "amp" 之类
      // 会命中 &amp; 等实体内部，插出残缺的 HTML。
      return String(text)
        .split(re)
        .map((part, i) => (i % 2 === 1 ? `<mark>${escapeHtml(part)}</mark>` : escapeHtml(part)))
        .join("");
    },
    onKeydown(e) {
      const n = this.flatItems.length;
      if (e.key === "Escape") {
        e.preventDefault();
        this.close();
      } else if (e.key === "ArrowDown" && n) {
        e.preventDefault();
        this.activeIndex = (this.activeIndex + 1) % n;
      } else if (e.key === "ArrowUp" && n) {
        e.preventDefault();
        this.activeIndex = (this.activeIndex - 1 + n) % n;
      } else if (e.key === "Enter" && !e.isComposing) {
        // 输入法选词时的回车不算「打开」
        const item = this.flatItems[this.activeIndex];
        if (item) {
          e.preventDefault();
          this.run(item);
        }
      } else if (e.key === "Tab") {
        // 焦点留在面板里
        e.preventDefault();
        this.$refs.input?.focus();
      }
    },
    run(item) {
      if (item.action === "theme") {
        this.close();
        this.toggleTheme();
        return;
      }
      if (item.to) this.go(item.to);
    },
    go(to) {
      this.close();
      this.palette.query = "";
      const [path, hash] = to.split("#");
      let current = this.$route.path;
      try { current = decodeURI(current); } catch { /* 保留原样 */ }
      const sameRoute = current === path;
      this.$router.push(to).catch(() => {});
      // 同一页面内只换锚点时路由不会重新加载正文，自己滚过去
      if (sameRoute && hash) {
        this.$nextTick(() => document.getElementById(hash)?.scrollIntoView({ behavior: "smooth", block: "start" }));
      }
    },
    goTag(tag) {
      this.go(`/tags/${encodeURIComponent(tag)}`);
    },
    close() {
      closePalette();
    },
    onGlobalKeydown(e) {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        if (this.palette.open) this.close();
        else openPalette();
        return;
      }
      // 「/」唤起搜索，但在输入框、编辑器里打字时不抢
      if (e.key === "/" && !this.palette.open && !e.metaKey && !e.ctrlKey && !e.altKey) {
        const t = e.target;
        const typing = t && (t.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName));
        if (!typing) {
          e.preventDefault();
          openPalette();
        }
      }
    },
  },
  mounted() {
    window.addEventListener("keydown", this.onGlobalKeydown);
  },
  beforeUnmount() {
    window.removeEventListener("keydown", this.onGlobalKeydown);
    document.documentElement.style.overflow = "";
  },
};
</script>

<style scoped>
.cp-overlay {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: max(12vh, 24px) 12px 24px;
  background: rgba(10, 12, 24, 0.38);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}
html.dark .cp-overlay { background: rgba(0, 0, 0, 0.6); }

.cp-panel {
  display: flex;
  flex-direction: column;
  width: min(660px, 100%);
  max-height: min(620px, calc(100dvh - 48px));
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: 18px;
  background: var(--bg-surface);
  box-shadow: var(--shadow-lg), 0 0 0 1px var(--glass-border);
}

.cp-input-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 14px 0 18px;
  border-bottom: 1px solid var(--border);
}
.cp-input-icon { flex-shrink: 0; color: var(--text-muted); }
.cp-input {
  flex: 1;
  min-width: 0;
  height: 58px;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text-primary);
  font: inherit;
  font-size: 16px; /* ≥16px，iOS 聚焦时不会自动放大 */
}
.cp-input::placeholder { color: var(--text-muted); }
.cp-input::-webkit-search-cancel-button { display: none; }
.cp-input:focus-visible { outline: none; }
.cp-esc {
  flex-shrink: 0;
  padding: 4px;
  border: 0;
  background: none;
  cursor: pointer;
}

.cp-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 6px 8px 10px;
}

.cp-group-label {
  padding: 12px 10px 6px;
  color: var(--text-muted);
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.04em;
}

.cp-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 4px 2px 8px;
  border-bottom: 1px dashed var(--border);
}
.cp-tags .cp-group-label { padding: 6px 8px 6px 8px; }
.cp-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--bg-subtle);
  color: var(--text-body);
  font: inherit;
  font-size: 12.5px;
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease, background 0.15s ease;
}
.cp-tag span { color: var(--text-muted); font-size: 11px; }
.cp-tag:hover { border-color: var(--accent); color: var(--accent); background: var(--accent-soft); }

.cp-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 48px;
  padding: 7px 10px;
  border-radius: 10px;
  cursor: pointer;
  scroll-margin: 8px;
}
.cp-item.active { background: var(--accent-soft); }
.cp-item-icon {
  display: inline-grid;
  flex-shrink: 0;
  place-items: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--border);
  border-radius: 9px;
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-size: 16px;
}
.cp-item.active .cp-item-icon {
  border-color: var(--accent-soft-strong);
  background: var(--bg-surface);
  color: var(--accent);
}
.cp-item-body { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 1px; }
.cp-item-title {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cp-item-title :deep(mark) {
  border-radius: 3px;
  background: var(--accent-soft-strong);
  color: var(--accent);
  box-shadow: 0 0 0 1px var(--accent-soft-strong);
}
.cp-item-sub {
  overflow: hidden;
  color: var(--text-muted);
  font-size: 12.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cp-item-enter {
  flex-shrink: 0;
  color: var(--accent);
  font-size: 14px;
  opacity: 0;
  transform: translateX(-4px);
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.cp-item.active .cp-item-enter { opacity: 1; transform: none; }

.cp-empty {
  padding: 40px 20px 32px;
  color: var(--text-secondary);
  font-size: 13.5px;
  text-align: center;
}
.cp-empty-title { margin-bottom: 6px; color: var(--text-primary); font-size: 15px; font-weight: 600; }

.cp-foot {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 16px;
  border-top: 1px solid var(--border);
  background: var(--bg-subtle);
  color: var(--text-muted);
  font-size: 12px;
}
.cp-foot > span { display: inline-flex; align-items: center; gap: 4px; }
.cp-foot-count { margin-left: auto; }

/* 进出场 */
.cp-enter-active, .cp-leave-active { transition: opacity 0.18s ease; }
.cp-enter-active .cp-panel { transition: transform 0.28s var(--ease-out), opacity 0.2s ease; }
.cp-leave-active .cp-panel { transition: transform 0.14s ease, opacity 0.14s ease; }
.cp-enter-from, .cp-leave-to { opacity: 0; }
.cp-enter-from .cp-panel { opacity: 0; transform: translateY(-10px) scale(0.98); }
.cp-leave-to .cp-panel { opacity: 0; transform: scale(0.98); }

@media (max-width: 640px) {
  .cp-overlay { padding: max(8px, env(safe-area-inset-top)) 8px 8px; }
  .cp-panel { max-height: calc(100dvh - 16px); border-radius: 16px; }
  .cp-foot-hide-sm { display: none; }
  .cp-foot > span:first-child,
  .cp-foot > span:nth-child(2) { display: none; }
}
</style>
