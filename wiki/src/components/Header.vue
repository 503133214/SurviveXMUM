<!-- src/components/Header.vue -->
<template>
  <header class="site-header">
    <div class="logo" @click="goHome" role="button" tabindex="0" aria-label="返回首页">XMUM Wiki</div>
    <!--搜索栏-->
    <div style="flex: 1;padding: 0 20px;margin-top: 10px;text-align: center;">
      <el-input placeholder="搜索相关内容..." style="width: 100%;max-width: 300px; height:30px;border-radius:15px">
        <template #prefix>
          <el-icon><Search/></el-icon>
        </template>
      </el-input>
    </div>
    <!-- 桌面端导航 -->
    <nav v-if="!isMobileView" class="desktop-nav">
      <router-link to="/">
        <el-button type="primary">Home</el-button>
      </router-link>
      <router-link to="/docs/README">Docs</router-link>
      <router-link to="/forums">论坛</router-link>
      <a href="https://github.com/503133214/SurviveXMUM" target="_blank" rel="noopener noreferrer">GitHub</a>
              <div>
          <a href="/login" target="_blank" rel="noopener noreferrer" v-if="!hasToken">
            <el-button>登录</el-button>
          </a>
          <el-dropdown v-if="hasToken">
            <span class="el-dropdown-link">
              <el-avatar>user</el-avatar>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item>个人信息设置</el-dropdown-item>
                <el-dropdown-item>消息列表</el-dropdown-item>
                <el-dropdown-item divided @click="UserLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

    </nav>

    <!-- 移动端导航 -->
    <div v-if="isMobileView" class="mobile-nav">
      <el-dropdown trigger="click" @command="handleMobileNavCommand">
        <el-button :icon="MenuIcon" text circle aria-label="导航菜单"></el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="/">     
              <el-button type="primary">Home</el-button>
            </el-dropdown-item>
            <el-dropdown-item command="/docs/README">Docs</el-dropdown-item>
            <el-dropdown-item command="github" divided>GitHub</el-dropdown-item>
            <el-dropdown-item command="website">官网</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script>
import {Menu, Search, User} from '@element-plus/icons-vue';
import {logout, takeAccessToken} from "@/net/index.js";
const MOBILE_BREAKPOINT = 767; // 根据需要调整断点
export default {
  name: "SiteHeader", // Renamed from "Header" to be more specific if "Header" is too generic
  components: {
    User,
     Search,
    // ElDropdown, ElDropdownMenu, ElDropdownItem, ElButton are globally registered
  },
  data() {
    return {
      isMobileView: false,
      MenuIcon: Menu,
      resizeTimeout: null,
      tokenCheckCounter: 0, // 用于强制刷新computed
    };
  },
  computed: {
    hasToken() {
      // 使用tokenCheckCounter来强制刷新这个computed属性
      this.tokenCheckCounter;
      const token = takeAccessToken();
      // 更严格的判断：不仅要存在，还要有内容
      const hasValidToken = token && token.trim && token.trim().length > 0;
      return hasValidToken;
    }
  },
  methods: {
    refreshTokenStatus() {
      this.tokenCheckCounter++;
    },
    goHome() {
      this.$router.push("/");
    },
    UserLogout(){
      logout(() => {
        // 退出登录成功后强制刷新页面
        this.refreshTokenStatus(); // 先刷新token状态
        window.location.href = '/';
      });
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
    handleMobileNavCommand(command) {
      if (command === 'github') {
        window.open('https://github.com/503133214/SurviveXMUM', '_blank', 'noopener,noreferrer');
      } else if (command === 'website') {
        window.open('https://your-site.com', '_blank', 'noopener,noreferrer');
      } else if (command) {
        this.$router.push(command);
      }
    }
  },
  mounted() {
    this.checkMobileView();
    window.addEventListener('resize', this.handleResize);
    // 定期检查token状态（每30秒检查一次）
    this.tokenCheckInterval = setInterval(() => {
      this.refreshTokenStatus();
    }, 30000);
    // 监听storage变化
    window.addEventListener('storage', this.refreshTokenStatus);
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.handleResize);
    window.removeEventListener('storage', this.refreshTokenStatus);
    clearTimeout(this.resizeTimeout);
    if (this.tokenCheckInterval) {
      clearInterval(this.tokenCheckInterval);
    }
  },
};
</script>

<style scoped>
.site-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 20px; /* 稍微调整内边距 */
  background-color: #ffffff;
  border-bottom: 1px solid #e0e0e0;
  position: sticky;
  top: 0;
  z-index: 1000;
}

.logo {
  font-size: 20px;
  font-weight: 700;
  color: #0c64c1;
  cursor: pointer;
  user-select: none; /* 防止文本选中 */
}

.desktop-nav {
  display: flex;
  align-items: center;
  gap: 20px; /* 使用gap替代margin-left，更简洁 */
}

.desktop-nav a {
  color: #333; /* 略微加深颜色以提高对比度 */
  text-decoration: none;
  font-weight: 500;
  transition: color 0.2s ease-in-out;
}

.desktop-nav a:hover,
.desktop-nav .router-link-active, /* 高亮当前路由 */
.desktop-nav .router-link-exact-active { /* 高亮精确匹配的当前路由 */
  color: #0c64c1;
}

.mobile-nav .el-button {
  /* Element Plus 按钮通常有自己的样式，这里可以微调 */
  color: #333; /* 确保图标颜色可见 */
}

/* 下拉框样式 */
.el-dropdown-link {
  cursor: pointer;
  display: flex;
  align-items: center;
}

.el-dropdown-link:hover .el-avatar {
  opacity: 0.8;
}
</style>
