<template>
  <header class="site-header" :class="{ scrolled: isScrolled }">
    <div class="logo" @click="goHome" role="button" tabindex="0" aria-label="返回首页">
      <img src="/svg/Text_logo_hor.svg" alt="XMUM Wiki" class="logo-img" />
    </div>

    <div class="search-container">
      <GlobalSearch />
    </div>

    <nav v-if="!isMobileView" class="desktop-nav">
      <router-link to="/">首页</router-link>
      <router-link :to="`/docs/${HOME_PATH}`">文档</router-link>
      <router-link v-if="hasToken" to="/edit">✍️ 写文章</router-link>
      <a :href="REPO" target="_blank" rel="noopener noreferrer">GitHub</a>

      <button class="theme-toggle" @click="toggleTheme" :aria-label="isDark ? '切换到亮色' : '切换到暗色'">
        {{ isDark ? '☀️' : '🌙' }}
      </button>

      <template v-if="backendEnabled">
        <router-link to="/login" v-if="!hasToken">
          <el-button type="primary" round>登录</el-button>
        </router-link>
        <el-dropdown v-else @command="handleUserCommand">
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
              <el-dropdown-item command="/edit">
                <el-icon><EditPen /></el-icon>写文章
              </el-dropdown-item>
              <el-dropdown-item v-if="isAdmin" command="/admin">
                <el-icon><Setting /></el-icon>管理后台
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>
    </nav>

    <div v-else class="mobile-nav">
      <button class="theme-toggle" @click="toggleTheme" aria-label="切换主题">
        {{ isDark ? '☀️' : '🌙' }}
      </button>
      <el-dropdown trigger="click" @command="handleMobileNavCommand">
        <el-button :icon="MenuIcon" text circle aria-label="导航菜单"></el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="/">首页</el-dropdown-item>
            <el-dropdown-item :command="`/docs/${HOME_PATH}`">文档</el-dropdown-item>
            <el-dropdown-item v-if="hasToken" command="/edit">写文章</el-dropdown-item>
            <el-dropdown-item command="github" divided>GitHub</el-dropdown-item>
            <template v-if="backendEnabled">
              <template v-if="hasToken">
                <el-dropdown-item command="/profile" divided>个人中心</el-dropdown-item>
                <el-dropdown-item v-if="isAdmin" command="/admin">管理后台</el-dropdown-item>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </template>
              <el-dropdown-item v-else command="login" divided>登录</el-dropdown-item>
            </template>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script>
import { markRaw } from "vue";
import { Menu, User, EditPen, Setting, SwitchButton } from "@element-plus/icons-vue";
import { logout, takeAccessToken } from "@/net/index.js";
import { useUserStore } from "@/store/userStore.js";
import { useTheme } from "@/composables/useTheme.js";
import GlobalSearch from "@/components/GlobalSearch.vue";
import { HOME_PATH, REPO } from "@/wiki";
import { BACKEND_ENABLED } from "@/config.js";

const MOBILE_BREAKPOINT = 767;

export default {
  name: "SiteHeader",
  components: { GlobalSearch, User, EditPen, Setting, SwitchButton },
  setup() {
    const { isDark, toggleTheme } = useTheme();
    return { isDark, toggleTheme };
  },
  data() {
    return {
      isMobileView: false,
      isScrolled: false,
      MenuIcon: markRaw(Menu),
      resizeTimeout: null,
      tokenCheckCounter: 0,
      tokenCheckInterval: null,
      backendEnabled: BACKEND_ENABLED,
      HOME_PATH,
      REPO,
    };
  },
  computed: {
    hasToken() {
      this.tokenCheckCounter;
      const token = takeAccessToken();
      return token && token.trim && token.trim().length > 0;
    },
    userName() {
      return useUserStore().username || "用户";
    },
    userAvatar() {
      return useUserStore().avatar || "";
    },
    isAdmin() {
      return useUserStore().userInfo?.role === "ADMIN";
    },
  },
  methods: {
    refreshTokenStatus() {
      this.tokenCheckCounter++;
    },
    goHome() {
      this.$router.push("/");
    },
    UserLogout() {
      const userStore = useUserStore();
      logout(() => {
        userStore.clearUserInfo();
        this.refreshTokenStatus();
        window.location.href = "/";
      });
    },
    handleUserCommand(command) {
      if (command === "logout") this.UserLogout();
      else this.$router.push(command);
    },
    checkMobileView() {
      this.isMobileView = window.innerWidth <= MOBILE_BREAKPOINT;
    },
    handleResize() {
      clearTimeout(this.resizeTimeout);
      this.resizeTimeout = setTimeout(this.checkMobileView, 100);
    },
    handleScroll() {
      this.isScrolled = window.scrollY > 8;
    },
    handleMobileNavCommand(command) {
      if (command === "github") {
        window.open(REPO, "_blank", "noopener,noreferrer");
      } else if (command === "login") {
        this.$router.push("/login");
      } else if (command === "logout") {
        this.UserLogout();
      } else if (command) {
        this.$router.push(command);
      }
    },
  },
  mounted() {
    this.checkMobileView();
    this.handleScroll();
    window.addEventListener("resize", this.handleResize);
    window.addEventListener("scroll", this.handleScroll, { passive: true });
    this.tokenCheckInterval = setInterval(this.refreshTokenStatus, 30000);
    window.addEventListener("storage", this.refreshTokenStatus);
  },
  beforeUnmount() {
    window.removeEventListener("resize", this.handleResize);
    window.removeEventListener("scroll", this.handleScroll);
    window.removeEventListener("storage", this.refreshTokenStatus);
    clearTimeout(this.resizeTimeout);
    if (this.tokenCheckInterval) clearInterval(this.tokenCheckInterval);
  },
};
</script>

<style scoped>
.site-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: var(--header-height);
  padding: 0 20px;
  background: var(--glass-bg);
  backdrop-filter: saturate(180%) blur(16px);
  -webkit-backdrop-filter: saturate(180%) blur(16px);
  border-bottom: 1px solid transparent;
  position: sticky;
  top: 0;
  z-index: 1000;
  transition: border-color 0.3s ease, box-shadow 0.3s ease, background 0.3s ease;
}
.site-header.scrolled {
  border-bottom-color: var(--border);
  box-shadow: var(--shadow-sm);
}

.logo {
  display: flex;
  align-items: center;
  cursor: pointer;
  user-select: none;
  flex-shrink: 0;
  transition: opacity 0.2s ease;
}
.logo:hover { opacity: 0.75; }
.logo-img {
  height: 26px;
  width: auto;
  display: block;
}
html.dark .logo-img { filter: brightness(0) invert(1); }

.search-container {
  flex: 1;
  max-width: 440px;
  padding: 0 24px;
}

.desktop-nav {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.desktop-nav > a {
  color: var(--text-secondary);
  font-weight: 500;
  font-size: 14.5px;
  padding: 7px 12px;
  border-radius: 8px;
  position: relative;
  transition: color 0.2s ease, background 0.2s ease;
}
.desktop-nav > a:hover {
  color: var(--text-primary);
  background-color: var(--bg-hover);
  text-decoration: none;
}
.desktop-nav .router-link-active {
  color: var(--text-primary);
  font-weight: 600;
}
.desktop-nav :deep(.el-button) { margin-left: 6px; }

/* 登录按钮：编辑风纯黑/纯白 */
:deep(.el-button--primary) {
  --el-button-bg-color: var(--accent);
  --el-button-border-color: var(--accent);
  --el-button-text-color: var(--accent-contrast);
  --el-button-hover-bg-color: var(--accent);
  --el-button-hover-border-color: var(--accent);
  --el-button-hover-text-color: var(--accent-contrast);
  --el-button-active-bg-color: var(--accent);
  --el-button-active-border-color: var(--accent);
  font-weight: 600;
}
:deep(.el-button--primary:hover) { opacity: 0.88; }

.theme-toggle {
  background: var(--bg-subtle);
  border: 1px solid var(--border);
  border-radius: 50%;
  width: 38px;
  height: 38px;
  font-size: 16px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.4s var(--ease-spring), border-color 0.2s ease, background 0.2s ease;
}
.theme-toggle:hover {
  border-color: var(--brand);
  background: var(--bg-hover);
  transform: rotate(40deg) scale(1.08);
}
.theme-toggle:active { transform: rotate(180deg) scale(0.95); }

.mobile-nav { display: flex; align-items: center; gap: 8px; }

.el-dropdown-link {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
}
.user-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 767px) {
  .site-header { padding: 0 12px; }
  .search-container { max-width: none; padding: 0 10px; }
  .logo-text { font-size: 17px; }
}
</style>
