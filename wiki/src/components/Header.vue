<template>
  <header class="site-header">
    <div class="logo" @click="goHome" role="button" tabindex="0" aria-label="返回首页">
      <span class="logo-text">XMUM Wiki</span>
    </div>

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

    <nav v-if="!isMobileView" class="desktop-nav">
      <router-link to="/">
        <el-button type="primary">Home</el-button>
      </router-link>
      <router-link to="/docs/README">Docs</router-link>
      <router-link to="/forums">论坛</router-link>
      <router-link to="/feedback">反馈</router-link>
      <a href="https://github.com/503133214/SurviveXMUM" target="_blank" rel="noopener noreferrer">GitHub</a>
      <div>
        <router-link to="/login" v-if="!hasToken">
          <el-button>登录</el-button>
        </router-link>
        <el-dropdown v-if="hasToken" @command="handleUserCommand">
          <span class="el-dropdown-link">
            <el-avatar v-if="userAvatar" :src="userAvatar" :size="32" />
            <el-avatar v-else :size="32">{{ userName.charAt(0) }}</el-avatar>
            <span class="user-name">{{ userName }}</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="/profile">
                <el-icon><User /></el-icon>个人中心
              </el-dropdown-item>
              <el-dropdown-item command="/favorites">
                <el-icon><Star /></el-icon>我的收藏
              </el-dropdown-item>
              <el-dropdown-item command="/feedback">
                <el-icon><ChatDotRound /></el-icon>系统反馈
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </nav>

    <div v-if="isMobileView" class="mobile-nav">
      <el-dropdown trigger="click" @command="handleMobileNavCommand">
        <el-button :icon="MenuIcon" text circle aria-label="导航菜单"></el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="/">
              <el-button type="primary">Home</el-button>
            </el-dropdown-item>
            <el-dropdown-item command="/docs/README">Docs</el-dropdown-item>
            <el-dropdown-item command="/forums">论坛</el-dropdown-item>
            <el-dropdown-item command="/feedback">反馈</el-dropdown-item>
            <el-dropdown-item command="github" divided>GitHub</el-dropdown-item>
            <el-dropdown-item command="website">官网</el-dropdown-item>
            <template v-if="hasToken">
              <el-dropdown-item command="/profile" divided>个人中心</el-dropdown-item>
              <el-dropdown-item command="/favorites">我的收藏</el-dropdown-item>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </template>
            <el-dropdown-item v-else command="login" divided>登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script>
import { Menu, Search, User, Star, ChatDotRound, SwitchButton } from '@element-plus/icons-vue';
import { logout, takeAccessToken } from "@/net/index.js";
import { useUserStore } from '@/store/userStore.js';

const MOBILE_BREAKPOINT = 767;

export default {
  name: "SiteHeader",
  components: {
    User,
    Search,
    Star,
    ChatDotRound,
    SwitchButton
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
      this.tokenCheckCounter;
      const token = takeAccessToken();
      return token && token.trim && token.trim().length > 0;
    },
    userName() {
      const store = useUserStore();
      return store.username || '用户';
    },
    userAvatar() {
      const store = useUserStore();
      return store.avatar || '';
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
      }
    },
    UserLogout(){
      const userStore = useUserStore();
      logout(() => {
        userStore.clearUserInfo();
        this.refreshTokenStatus();
        window.location.href = '/';
      });
    },
    handleUserCommand(command) {
      if (command === 'logout') {
        this.UserLogout();
      } else {
        this.$router.push(command);
      }
    },
    checkMobileView() {
      this.isMobileView = window.innerWidth <= MOBILE_BREAKPOINT;
    },
    handleResize() {
      clearTimeout(this.resizeTimeout);
      this.resizeTimeout = setTimeout(() => {
        this.checkMobileView();
      }, 100);
    },
    handleMobileNavCommand(command) {
      if (command === 'github') {
        window.open('https://github.com/503133214/SurviveXMUM', '_blank', 'noopener,noreferrer');
      } else if (command === 'website') {
        window.open('https://your-site.com', '_blank', 'noopener,noreferrer');
      } else if (command === 'logout') {
        this.UserLogout();
      } else if (command) {
        this.$router.push(command);
      }
    }
  },
  mounted() {
    this.checkMobileView();
    window.addEventListener('resize', this.handleResize);
    this.tokenCheckInterval = setInterval(() => {
      this.refreshTokenStatus();
    }, 30000);
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
  gap: 8px;
  transition: opacity 0.2s ease;
}

.el-dropdown-link:hover {
  opacity: 0.8;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Responsive adjustments */
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
