<template>
  <header class="site-header">
    <div class="logo" @click="goHome" role="button" tabindex="0" aria-label="返回首页">
      <span class="logo-text">XMUM Wiki</span>
    </div>
    
    <!-- 搜索栏 -->
    <div class="search-container">
      <el-input 
        v-model="searchQuery"
        placeholder="搜索相关内容..." 
        class="search-input"
        clearable
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
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
            <el-dropdown-item command="login">登录</el-dropdown-item>
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
      tokenCheckCounter: 0,
      searchQuery: '',
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
    handleSearch() {
      if (this.searchQuery.trim()) {
        console.log('搜索:', this.searchQuery);
        // TODO: 实现搜索功能
      }
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
  padding: 0 20px;
  background-color: #ffffff;
  border-bottom: 1px solid #e4e7ed;
  position: sticky;
  top: 0;
  z-index: 1000;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  transition: box-shadow 0.3s ease;
}

.site-header:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.logo {
  display: flex;
  align-items: center;
  cursor: pointer;
  user-select: none;
  transition: transform 0.2s ease;
}

.logo:hover {
  transform: scale(1.05);
}

.logo-text {
  font-size: 22px;
  font-weight: 700;
  background: linear-gradient(135deg, #0c64c1 0%, #42b983 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.search-container {
  flex: 1;
  max-width: 400px;
  padding: 0 20px;
}

.search-input {
  width: 100%;
}

.search-input :deep(.el-input__wrapper) {
  border-radius: 20px;
  transition: all 0.3s ease;
}

.search-input :deep(.el-input__wrapper):hover,
.search-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #0c64c1 inset;
}

.desktop-nav {
  display: flex;
  align-items: center;
  gap: 24px;
}

.desktop-nav a {
  color: #333;
  text-decoration: none;
  font-weight: 500;
  font-size: 15px;
  padding: 8px 12px;
  border-radius: 6px;
  transition: all 0.2s ease;
  position: relative;
}

.desktop-nav a::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 0;
  height: 2px;
  background: linear-gradient(90deg, #0c64c1, #42b983);
  transition: width 0.3s ease;
}

.desktop-nav a:hover {
  color: #0c64c1;
  background-color: #f0f7ff;
}

.desktop-nav a:hover::after {
  width: 80%;
}

.desktop-nav .router-link-active,
.desktop-nav .router-link-exact-active {
  color: #0c64c1;
  background-color: #f0f7ff;
}

.desktop-nav .router-link-active::after,
.desktop-nav .router-link-exact-active::after {
  width: 80%;
}

.mobile-nav .el-button {
  color: #333;
  font-size: 20px;
}

.el-dropdown-link {
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: opacity 0.2s ease;
}

.el-dropdown-link:hover {
  opacity: 0.8;
}

/* 响应式调整 */
@media (max-width: 767px) {
  .site-header {
    padding: 0 12px;
  }
  
  .search-container {
    max-width: 200px;
    padding: 0 12px;
  }
  
  .logo-text {
    font-size: 18px;
  }
}
</style>
