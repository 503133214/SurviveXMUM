<template>
  <!-- 分类（可折叠） -->
  <el-sub-menu v-if="node.type === 'category'" :index="node.slug">
    <template #title>
      <span class="node-icon" v-if="node.icon">{{ node.icon }}</span>
      <span class="node-label">{{ node.label }}</span>
    </template>
    <WikiSidebarNode
      v-for="child in node.children"
      :key="child.path || child.slug"
      :node="child"
      @navigate="$emit('navigate', $event)"
    />
  </el-sub-menu>

  <!-- 文档页 -->
  <el-menu-item v-else :index="node.path">
    <span class="node-icon" v-if="node.icon">{{ node.icon }}</span>
    <span class="node-label">{{ node.title }}</span>
    <span v-if="node.draft" class="draft-badge">草稿</span>
  </el-menu-item>
</template>

<script>
export default {
  name: "WikiSidebarNode",
  props: {
    node: { type: Object, required: true },
  },
  emits: ["navigate"],
};
</script>

<style scoped>
.node-icon { margin-right: 8px; font-size: 15px; }
.node-label { vertical-align: middle; }
.draft-badge {
  margin-left: 8px;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 8px;
  background: var(--bg-subtle);
  color: var(--text-muted);
}
</style>
