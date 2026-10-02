<template>
  <!-- 分类（可折叠） -->
  <li v-if="node.type === 'category'" class="sb-cat" :class="{ open, 'has-active': containsActive, nested: depth > 0 }">
    <button
      type="button"
      class="sb-cat-btn"
      :aria-expanded="open"
      :title="node.label"
      @click="$emit('toggle', node.slug)"
    >
      <!-- 只有顶层篇章带图标，子分类和文档行都是纯文字，侧栏读起来像目录而不是图标墙 -->
      <span v-if="depth === 0" class="sb-icon" aria-hidden="true">
        <WikiIcon kind="category" :icon="node.icon" :category="node.slug" :title="node.label" :size="16" />
      </span>
      <span class="sb-label">{{ node.label }}</span>
      <span class="sb-count">{{ pageCount }}</span>
      <ChevronRight class="sb-chevron" :size="14" :stroke-width="2" aria-hidden="true" />
    </button>
    <div class="sb-children" :inert="!open || undefined">
      <ul>
        <WikiSidebarNode
          v-for="child in node.children"
          :key="child.path || child.slug"
          :node="child"
          :depth="depth + 1"
          :current-path="currentPath"
          :open-slugs="openSlugs"
          @toggle="$emit('toggle', $event)"
          @navigate="$emit('navigate', $event)"
        />
      </ul>
    </div>
  </li>

  <!-- 文档页 -->
  <li v-else>
    <a
      :href="href"
      class="sb-link"
      :class="{ active, nested: depth > 0 }"
      :aria-current="active ? 'page' : undefined"
      :title="node.title"
      @click="onClick"
    >
      <span class="sb-label">{{ node.title }}</span>
      <span v-if="node.draft" class="sb-draft">草稿</span>
    </a>
  </li>
</template>

<script>
import { ChevronRight } from "lucide-vue-next";
import WikiIcon from "@/components/WikiIcon.vue";

function countPages(node) {
  return (node.children || []).reduce((n, c) => n + (c.type === "page" ? 1 : countPages(c)), 0);
}
function contains(node, path) {
  return (node.children || []).some((c) => c.path === path || (c.type === "category" && contains(c, path)));
}

export default {
  name: "WikiSidebarNode",
  components: { WikiIcon, ChevronRight },
  props: {
    node: { type: Object, required: true },
    depth: { type: Number, default: 0 },
    currentPath: { type: String, default: "" },
    openSlugs: { type: Array, default: () => [] },
  },
  emits: ["navigate", "toggle"],
  computed: {
    open() {
      return this.openSlugs.includes(this.node.slug);
    },
    active() {
      return this.node.path === this.currentPath;
    },
    containsActive() {
      return this.node.type === "category" && contains(this.node, this.currentPath);
    },
    pageCount() {
      return countPages(this.node);
    },
    href() {
      return this.$router.resolve(`/docs/${this.node.path}`).href;
    },
  },
  methods: {
    // 普通点击走站内路由；⌘/Ctrl/Shift/中键保留浏览器默认（新标签打开）
    onClick(e) {
      if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
      e.preventDefault();
      this.$emit("navigate", this.node.path);
    },
  },
};
</script>

<style scoped>
li { list-style: none; }

.sb-cat + .sb-cat:not(.nested),
li + .sb-cat:not(.nested) { margin-top: 2px; }

.sb-cat-btn,
.sb-link {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 34px;
  padding: 6px 8px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: var(--fs-ui);
  line-height: 1.4;
  text-align: left;
  cursor: pointer;
  transition: background-color var(--dur) ease, color var(--dur) ease;
}
.sb-cat-btn:hover,
.sb-link:hover { background: var(--bg-hover); color: var(--text-primary); text-decoration: none; }
/* 子项列表为了折叠动画设了 overflow: hidden，向外的焦点环会被裁掉一截，改为向内画 */
.sb-cat-btn:focus-visible,
.sb-link:focus-visible { outline-offset: -2px; }

.sb-cat-btn { color: var(--text-primary); font-weight: 600; }
.sb-cat.nested > .sb-cat-btn { font-weight: 500; color: var(--text-secondary); }

/* 顶层篇章图标：16px 线性图标，不加底框；当前页在这一篇里时换成强调色 */
.sb-icon {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 16px;
  color: var(--text-muted);
  transition: color var(--dur) ease;
}
.sb-cat.has-active > .sb-cat-btn .sb-icon { color: var(--accent); }

.sb-label { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sb-count {
  flex-shrink: 0;
  color: var(--text-muted);
  font-size: var(--fs-xs);
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  opacity: 0;
  transition: opacity var(--dur) ease;
}
.sb-cat-btn:hover .sb-count,
.sb-cat.open > .sb-cat-btn .sb-count { opacity: 1; }
.sb-chevron {
  flex-shrink: 0;
  color: var(--text-muted);
  transition: transform var(--dur) var(--ease-out);
}
.sb-cat.open > .sb-cat-btn .sb-chevron { transform: rotate(90deg); }

/* 折叠动画：grid 0fr → 1fr，高度自适应不用 JS 量 */
.sb-children {
  display: grid;
  grid-template-rows: 0fr;
  transition: grid-template-rows var(--dur-slow) var(--ease-out);
}
.sb-cat.open > .sb-children { grid-template-rows: 1fr; }
/* 引导线落在顶层图标的中线上（8px 内边距 + 16px 图标的一半），
   子项文字因此正好和篇章名左对齐 */
.sb-children > ul {
  min-height: 0;
  overflow: hidden;
  margin-left: 15px;
  padding-left: 10px;
  border-left: 1px solid var(--border);
}
.sb-cat.open > .sb-children > ul { padding-top: 2px; padding-bottom: 6px; }
/* 子分类没有图标，引导线对齐到它的文字起点 */
.sb-cat.nested > .sb-children > ul { margin-left: 8px; }

.sb-link.nested { position: relative; min-height: 32px; gap: 8px; }
.sb-link.active {
  background: var(--accent-soft);
  color: var(--accent);
  font-weight: 600;
}
/* 当前页在引导线上标一段高亮 */
.sb-link.nested.active::before {
  content: "";
  position: absolute;
  top: 7px;
  bottom: 7px;
  left: -11px;
  width: 2px;
  border-radius: var(--radius-xs);
  background: var(--accent);
}

.sb-draft {
  flex-shrink: 0;
  padding: 0 6px;
  border-radius: var(--radius-xs);
  background: var(--warning-soft);
  color: var(--warning);
  font-size: var(--fs-xs);
  font-weight: 500;
  line-height: 20px;
}
</style>
