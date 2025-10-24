<template>
  <el-menu
    :default-active="currentPath"
    :default-openeds="initiallyOpenedSubmenus"
    class="sidebar-el-menu"
    @select="handleMenuSelect"
  >
    <template v-for="item in sidebarItems" :key="item.name">
      <!-- 可折叠的分组 -->
      <el-sub-menu v-if="item.children && item.children.length > 0" :index="item.name">
        <template #title>
          <span>{{ item.name }}</span>
        </template>
        <el-menu-item
          v-for="child in item.children"
          :key="child.path"
          :index="child.path"
        >
          {{ child.name }}
        </el-menu-item>
      </el-sub-menu>
      <!-- 普通链接项 (顶级，无子项) -->
      <el-menu-item v-else :index="item.path">
        <span>{{ item.name }}</span>
      </el-menu-item>
    </template>
  </el-menu>
</template>

<script>
export default {
  name: "WikiSidebar",
  props: {
    sidebarItems: {
      type: Array,
      required: true,
    },
    currentPath: {
      type: String,
      required: true,
    },
  },
  computed: {
    initiallyOpenedSubmenus() {
      const openSubmenus = [];
      if (this.currentPath) {
        this.sidebarItems.forEach(item => {
          if (item.children && item.children.length > 0) {
            const isActiveGroup = item.children.some(child => child.path === this.currentPath);
            if (isActiveGroup) {
              openSubmenus.push(item.name); // item.name is used as el-sub-menu index
            }
          }
        });
      }
      return openSubmenus;
    }
  },
  methods: {
    handleMenuSelect(path) {
      // path 是被选中 el-menu-item 的 index
      if (path) {
        this.$emit("navigate", path);
      }
    },
  },
};
</script>

<style scoped>
.sidebar-el-menu {
  width: 240px;
  height: calc(100vh - 60px);
  overflow-y: auto;
  position: sticky;
  top: 60px;
  background-color: #ffffff;
  border-right: 1px solid #e4e7ed;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.03);
}

/* 自定义菜单项样式 */
.sidebar-el-menu :deep(.el-menu-item) {
  font-size: 14px;
  padding-left: 24px !important;
  transition: all 0.2s ease;
  border-left: 3px solid transparent;
}

.sidebar-el-menu :deep(.el-menu-item:hover) {
  background-color: #f0f7ff;
  border-left-color: #42b983;
}

.sidebar-el-menu :deep(.el-menu-item.is-active) {
  color: #0c64c1;
  background-color: #f0f7ff;
  border-left-color: #0c64c1;
  font-weight: 600;
}

.sidebar-el-menu :deep(.el-sub-menu__title) {
  font-size: 14px;
  font-weight: 600;
  transition: all 0.2s ease;
}

.sidebar-el-menu :deep(.el-sub-menu__title:hover) {
  background-color: #f8f9fa;
  color: #0c64c1;
}

/* 响应式调整：针对小屏幕设备 */
@media (max-width: 767px) {
  .sidebar-el-menu {
    position: static; /* 在堆叠布局中覆盖 sticky 定位 */
    height: auto;     /* 允许内容定义高度 */
    width: 100%;      /* 在堆叠时占据全部宽度 */
    border-right: none; /* 在小屏幕且堆叠时，通常不需要右边框 */
    /* 如果 DocPage.vue 中的 el-aside 已经处理了宽度和边框，这里的 width 和 border-right 可能可以省略 */
  }
}
</style>
