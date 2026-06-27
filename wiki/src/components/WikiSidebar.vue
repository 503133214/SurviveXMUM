<template>
  <div class="wiki-sidebar">
    <div class="sidebar-filter">
      <el-input
        v-model="filter"
        size="small"
        placeholder="筛选目录…"
        clearable
        :prefix-icon="SearchIcon"
      />
    </div>

    <el-menu
      :default-active="currentPath"
      :default-openeds="openedSubmenus"
      :unique-opened="false"
      class="sidebar-el-menu"
      @select="onSelect"
    >
      <WikiSidebarNode
        v-for="node in filteredItems"
        :key="node.path || node.slug"
        :node="node"
        @navigate="$emit('navigate', $event)"
      />
      <div v-if="filteredItems.length === 0" class="sidebar-empty">无匹配项</div>
    </el-menu>
  </div>
</template>

<script>
import { markRaw } from "vue";
import WikiSidebarNode from "@/components/WikiSidebarNode.vue";
import { Search } from "@element-plus/icons-vue";

// 递归筛选：保留标题命中的页面及其所属分类
function filterTree(nodes, q) {
  const out = [];
  for (const node of nodes) {
    if (node.type === "category") {
      const children = filterTree(node.children, q);
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
  let result = [];
  for (const node of nodes) {
    if (node.type === "category") {
      const next = [...trail, node.slug];
      if (node.children.some((c) => c.path === path)) result.push(...next);
      result.push(...slugsContaining(node.children, path, next));
    }
  }
  return result;
}

function allCategorySlugs(nodes) {
  let result = [];
  for (const node of nodes) {
    if (node.type === "category") {
      result.push(node.slug);
      result.push(...allCategorySlugs(node.children));
    }
  }
  return result;
}

export default {
  name: "WikiSidebar",
  components: { WikiSidebarNode },
  props: {
    sidebarItems: { type: Array, required: true },
    currentPath: { type: String, required: true },
  },
  emits: ["navigate"],
  data() {
    return { filter: "", SearchIcon: markRaw(Search) };
  },
  computed: {
    filteredItems() {
      const q = this.filter.trim().toLowerCase();
      if (!q) return this.sidebarItems;
      return filterTree(this.sidebarItems, q);
    },
    openedSubmenus() {
      // 筛选时全部展开，否则展开当前页所在分类
      if (this.filter.trim()) return allCategorySlugs(this.filteredItems);
      return slugsContaining(this.sidebarItems, this.currentPath);
    },
  },
  methods: {
    onSelect(index) {
      if (index) this.$emit("navigate", index);
    },
  },
};
</script>

<style scoped>
.wiki-sidebar {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-width: 0;
  height: calc(100vh - var(--header-height));
  position: sticky;
  top: var(--header-height);
  background-color: var(--bg-surface);
  border-right: 1px solid var(--border);
}

.sidebar-filter {
  padding: 12px;
  border-bottom: 1px solid var(--border);
}

.sidebar-el-menu {
  flex: 1;
  width: 100%;
  min-width: 0;
  overflow-x: hidden;
  overflow-y: auto;
  border-right: none;
  background-color: transparent;
}

.sidebar-empty {
  padding: 20px;
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
}

.sidebar-el-menu :deep(.el-menu-item) {
  min-width: 0;
  overflow: hidden;
  font-size: 14px;
  height: 42px;
  line-height: 42px;
  border-left: 3px solid transparent;
  transition: all 0.18s ease;
}
.sidebar-el-menu :deep(.el-menu-item:hover) {
  background-color: var(--bg-hover);
  border-left-color: var(--brand);
}
.sidebar-el-menu :deep(.el-menu-item.is-active) {
  color: var(--brand-blue);
  background-color: var(--bg-hover);
  border-left-color: var(--brand-blue);
  font-weight: 600;
}
.sidebar-el-menu :deep(.el-sub-menu__title) {
  min-width: 0;
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
}
.sidebar-el-menu :deep(.el-sub-menu__title:hover) {
  background-color: var(--bg-hover);
}

@media (max-width: 767px) {
  .wiki-sidebar { height: auto; position: static; border-right: none; }
}
</style>
