<template>
  <nav class="wiki-sidebar" aria-label="文档目录">
    <div class="sb-filter">
      <label class="sb-filter-box">
        <Search class="sb-filter-icon" :size="15" :stroke-width="2" aria-hidden="true" />
        <input
          v-model="filter"
          type="search"
          placeholder="筛选目录…"
          aria-label="筛选目录"
          @keydown.esc="filter = ''"
        />
        <button v-if="filter" type="button" class="sb-clear" aria-label="清空筛选" @click="filter = ''">
          <X :size="14" :stroke-width="2" />
        </button>
      </label>
      <p v-if="query" class="sb-hint" aria-live="polite">
        <template v-if="matchCount">找到 {{ matchCount }} 篇</template>
        <template v-else>没有匹配的文档</template>
      </p>
    </div>

    <div ref="scroll" class="sb-scroll">
      <ul class="sb-tree">
        <WikiSidebarNode
          v-for="node in filteredItems"
          :key="node.path || node.slug"
          :node="node"
          :depth="0"
          :current-path="currentPath"
          :open-slugs="query ? allOpen : openSlugs"
          @toggle="toggle"
          @navigate="$emit('navigate', $event)"
        />
      </ul>
      <div v-if="filteredItems.length === 0" class="sb-empty">无匹配项</div>
    </div>
  </nav>
</template>

<script>
import { Search, X } from "lucide-vue-next";
import WikiSidebarNode from "@/components/WikiSidebarNode.vue";

// 递归筛选：保留标题命中的页面及其所属分类
function filterTree(nodes, q) {
  const out = [];
  for (const node of nodes) {
    if (node.type === "category") {
      const children = filterTree(node.children || [], q);
      if (children.length > 0 || node.label.toLowerCase().includes(q)) {
        out.push({ ...node, children });
      }
    } else if ((node.title || "").toLowerCase().includes(q)) {
      out.push(node);
    }
  }
  return out;
}

// 收集包含目标路径的所有分类 slug，用于默认展开
function slugsContaining(nodes, path, trail = []) {
  const result = [];
  for (const node of nodes) {
    if (node.type === "category") {
      const next = [...trail, node.slug];
      if ((node.children || []).some((c) => c.path === path)) result.push(...next);
      result.push(...slugsContaining(node.children || [], path, next));
    }
  }
  return result;
}

function allCategorySlugs(nodes) {
  const result = [];
  for (const node of nodes) {
    if (node.type === "category") {
      result.push(node.slug);
      result.push(...allCategorySlugs(node.children || []));
    }
  }
  return result;
}

export default {
  name: "WikiSidebar",
  components: { WikiSidebarNode, Search, X },
  props: {
    sidebarItems: { type: Array, required: true },
    currentPath: { type: String, required: true },
  },
  emits: ["navigate"],
  data() {
    return { filter: "", openSlugs: [] };
  },
  computed: {
    query() {
      return this.filter.trim().toLowerCase();
    },
    filteredItems() {
      if (!this.query) return this.sidebarItems;
      return filterTree(this.sidebarItems, this.query);
    },
    allOpen() {
      return allCategorySlugs(this.filteredItems);
    },
    // 筛选时给个「找到 N 篇」的反馈，否则只能靠自己数
    matchCount() {
      const count = (nodes) => (nodes || []).reduce(
        (n, node) => n + (node.children ? count(node.children) : 1), 0);
      return count(this.filteredItems);
    },
  },
  watch: {
    // 换页时展开新页面所在的分类（不收起别的，保留读者自己展开的）
    currentPath: {
      immediate: true,
      handler(path) {
        this.expandTo(path);
        this.$nextTick(() => this.revealActive());
      },
    },
    // 内容清单晚到时（首次访问无缓存）补一次展开
    "sidebarItems.length"() {
      this.expandTo(this.currentPath);
      this.$nextTick(() => this.revealActive());
    },
  },
  methods: {
    expandTo(path) {
      for (const slug of slugsContaining(this.sidebarItems, path)) {
        if (!this.openSlugs.includes(slug)) this.openSlugs.push(slug);
      }
    },
    toggle(slug) {
      const i = this.openSlugs.indexOf(slug);
      if (i >= 0) this.openSlugs.splice(i, 1);
      else this.openSlugs.push(slug);
    },
    // 当前页不在可视范围内时滚到侧栏中间（只滚侧栏自己，不动整页）
    revealActive() {
      const box = this.$refs.scroll;
      const active = box && box.querySelector(".sb-link.active");
      if (!active) return;
      const b = box.getBoundingClientRect();
      const a = active.getBoundingClientRect();
      if (a.top < b.top + 8 || a.bottom > b.bottom - 8) {
        box.scrollTop += a.top - b.top - b.height / 2 + a.height / 2;
      }
    },
  },
  mounted() {
    // 展开动画结束后位置才准
    setTimeout(() => this.revealActive(), 320);
  },
};
</script>

<style scoped>
.wiki-sidebar {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-width: 0;
  height: 100%;
}

.sb-filter { padding: 16px 14px 10px; }
.sb-filter-box {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 8px 0 11px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--bg-surface);
  color: var(--text-muted);
  cursor: text;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.sb-filter-box:focus-within {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-ring);
}
.sb-filter-icon { flex-shrink: 0; }
.sb-filter-box input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text-primary);
  font: inherit;
  font-size: 13.5px;
}
.sb-filter-box input:focus-visible { outline: none; }
.sb-filter-box input::-webkit-search-cancel-button { display: none; }
.sb-filter-box input::placeholder { color: var(--text-muted); }
.sb-clear {
  display: grid;
  place-items: center;
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}
.sb-clear:hover { background: var(--bg-hover); color: var(--text-primary); }
.sb-hint { margin: 8px 4px 0; color: var(--text-muted); font-size: 12px; }

.sb-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 4px 10px 32px;
  scrollbar-gutter: stable;
}
.sb-tree { list-style: none; }
.sb-empty { padding: 24px; color: var(--text-muted); font-size: 13px; text-align: center; }
</style>
