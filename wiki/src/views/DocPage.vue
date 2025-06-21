<!-- src/views/DocPage.vue -->
<template>
  <el-container class="doc-page-container">
    <!-- 桌面端侧边栏 -->
    <el-aside v-if="!isMobileView" width="240px" class="doc-sidebar-container desktop-sidebar">
      <Sidebar
        :sidebar-items="sidebarItems"
        :current-path="docPath"
        @navigate="onNavigate"
      />
    </el-aside>

    <!-- 移动端抽屉式侧边栏 -->
    <el-drawer
      v-if="isMobileView"
      v-model="drawerVisible"
      title="导航"
      direction="ltr"
      size="240px"
      :with-header="true"
      @closed="drawerVisible = false" 
    >
      <Sidebar
        :sidebar-items="sidebarItems"
        :current-path="docPath"
        @navigate="onNavigateAndCloseDrawer"
      />
    </el-drawer>

    <el-main class="doc-main-content">
      <el-button
        v-if="isMobileView"
        @click="drawerVisible = true"
        class="mobile-menu-toggle"
        :icon="MenuIcon"
        text
        aria-label="打开导航菜单"
      />
      <div v-if="isLoading" class="loading-state">
        <el-skeleton :rows="5" animated />
      </div>
      <div v-else-if="errorLoading" class="error-state">
        <h1 v-if="title">{{ title }}</h1>
        <MarkdownRenderer v-if="content" :content="content" />
      </div>
      <div v-else class="content-display">
        <h1 v-if="title">{{ title }}</h1>
        <MarkdownRenderer v-if="content" :content="content" />
        <el-empty v-else-if="!title && !content" description="请从侧边栏选择一篇文档。"></el-empty>
      </div>
    </el-main>
  </el-container>
</template>

<script>
import axios from "axios";
import MarkdownRenderer from "@/components/MarkdownRenderer.vue";
import Sidebar from "@/components/WikiSidebar.vue";
import { Menu } from '@element-plus/icons-vue';
import generatedSidebarItems from '@/sidebar.data.js'; // <--- 导入生成的侧边栏数据

import generatedSidebarItems from '@/sidebar.data.js'; // <--- 导入生成的侧边栏数据

// Element Plus 组件 ElContainer, ElAside, ElMain, ElSkeleton, ElEmpty, ElDrawer, ElButton 已全局注册

// 改为从静态资源获取，不再需要后端API
const DOCS_BASE_PATH = "/docs/"; // public目录下的docs文件夹会直接映射为 /docs/
const MOBILE_BREAKPOINT = 767; // px

export default {
  name: "DocPage",
  components: {
    MarkdownRenderer,
    Sidebar,
  },
  props: {
    pathMatch: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      content: "",
      title: "",
      sidebarItems: [], // <--- 初始化为空数组
      isLoading: false,
      errorLoading: false,
      drawerVisible: false,
      isMobileView: false,
      MenuIcon: Menu,
      resizeTimeout: null,
    };
  },
  computed: {
    docPath() {
      return this.pathMatch || "README";
    },
  },
  watch: {
    docPath: {
      immediate: true,
      async handler(newPath) {
        await this.fetchMarkdown(newPath);
      },
    },
  },
  methods: {
    async fetchMarkdown(path) {
      this.isLoading = true;
      this.errorLoading = false;
      this.content = "";
      this.title = "";
      
      try {
        // 使用fetch从静态资源获取markdown文件
        const url = `${DOCS_BASE_PATH}${path}.md`;
        const response = await fetch(url);
        
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        
        let rawContent = await response.text();
        const lines = rawContent.split("\n");
        const firstLine = lines.find((line) => line.startsWith("# "));
        
        if (firstLine) {
          this.title = firstLine.replace(/^#\s*/, "");
          const firstNewLineIndex = rawContent.indexOf('\n');
          this.content = firstNewLineIndex !== -1 ? rawContent.substring(firstNewLineIndex + 1).trimStart() : "";
        } else {
          this.title = path;
          this.content = rawContent;
        }
      } catch (e) {
        console.error(`Failed to fetch markdown for path "${path}":`, e);
        this.errorLoading = true;
        this.content = `# 页面未找到 (404)\n\n无法加载文档： \`${path}.md\`。\n\n请检查文件是否存在以及路径是否正确。`;
        this.title = "页面未找到";
      } finally {
        this.isLoading = false;
      }
    },
    onNavigate(newPage) {
      this.$router.push(`/docs/${newPage}`);
    },
    onNavigateAndCloseDrawer(newPage) {
      this.onNavigate(newPage);
      if (this.isMobileView) {
        this.drawerVisible = false;
      }
    },
    checkMobileView() {
      this.isMobileView = window.innerWidth <= MOBILE_BREAKPOINT;
    },
    handleResize() {
      clearTimeout(this.resizeTimeout);
      this.resizeTimeout = setTimeout(() => {
        this.checkMobileView();
      }, 100); // Debounce resize
    },
  },
  created() {
    this.sidebarItems = generatedSidebarItems; // <--- 在 created 钩子中赋值
    // this.sidebarItems = [
    //   { name: "首页", path: "README" },
    //   { name: "指南", children: [{ name: "简介", path: "guide/introduction" },{ name: "进阶", path: "guide/advanced" }] },
    //   { name: "API 文档", children: [{ name: "总览", path: "api/api-overview" },{ name: "接口列表", path: "api/endpoints" }] },
    // ];
  },
  mounted() {
    this.checkMobileView();
    window.addEventListener('resize', this.handleResize);
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.handleResize);
    clearTimeout(this.resizeTimeout);
  },
};
</script>

<style scoped>
.doc-page-container {
  height: calc(100vh - 60px); /* 减去 Header 高度 (60px) */
}

.doc-sidebar-container { /* 主要用于桌面端 el-aside */
  overflow-y: auto;
}

.doc-main-content {
  padding: 24px;
  position: relative; /* 用于潜在的内部绝对定位元素 */
}

.loading-state .el-skeleton {
  max-width: 800px;
  margin: 0 auto;
}

.mobile-menu-toggle {
  margin-bottom: 15px;
  /* 如果希望按钮固定在屏幕角落，可以使用 fixed 定位：
  position: fixed;
  bottom: 20px;
  left: 20px;
  z-index: 100; // 确保在其他内容之上
  */
}

/* 桌面端侧边栏在小屏幕上通过 v-if 移除，无需额外 CSS 隐藏 */

/* 响应式调整：小屏幕设备 */
@media (max-width: 767px) {
  .doc-page-container {
    /* 在移动端，如果抽屉打开，主内容区域可能不需要改变flex-direction */
    /* 高度仍由 calc(100vh - 60px) 控制，页面滚动由 el-main 内部处理 */
  }
  .doc-main-content {
    padding: 15px;
  }
}
</style>
