<template>
  <header class="site-header" :class="{ scrolled: isScrolled }">
    <div class="logo" @click="goHome" role="button" tabindex="0" aria-label="返回首页">
      <img src="/svg/Text_logo_hor.svg" alt="XMUM Wiki" class="logo-img" />
    </div>

    <div class="search-container">
      <GlobalSearch />
    </div>

    <nav v-if="!isMobileView" class="desktop-nav">
      <router-link :to="`/docs/${HOME_PATH}`">文档</router-link>
      <a v-if="!hasToken" :href="REPO" target="_blank" rel="noopener noreferrer">GitHub</a>

      <button class="theme-toggle" @click="toggleTheme" :aria-label="isDark ? '切换到亮色' : '切换到暗色'">
        <el-icon :size="17"><Sunny v-if="isDark" /><Moon v-else /></el-icon>
      </button>

      <template v-if="backendEnabled">
        <router-link to="/login" v-if="!hasToken">
          <el-button type="primary" round>登录</el-button>
        </router-link>
        <el-dropdown v-else @command="handleUserCommand" trigger="click">
          <span class="el-dropdown-link">
            <el-avatar v-if="userAvatar" :src="userAvatar" :size="30" />
            <el-avatar v-else :size="30">{{ userName.charAt(0) }}</el-avatar>
            <span class="user-name">{{ userName }}</span>
            <el-icon class="caret" :size="13"><ArrowDown /></el-icon>
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
              <el-dropdown-item divided command="github">
                <el-icon><Link /></el-icon>GitHub
              </el-dropdown-item>
              <el-dropdown-item command="logout">
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>
    </nav>

    <div v-else class="mobile-nav">
      <button class="theme-toggle" @click="toggleTheme" aria-label="切换主题">
        <el-icon :size="17"><Sunny v-if="isDark" /><Moon v-else /></el-icon>
      </button>
      <el-dropdown trigger="click" @command="handleMobileNavCommand">
        <el-button :icon="MenuIcon" text circle aria-label="导航菜单"></el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item :command="`/docs/${HOME_PATH}`">
              <el-icon><Document /></el-icon>文档
            </el-dropdown-item>
            <template v-if="backendEnabled">
              <template v-if="hasToken">
                <el-dropdown-item command="/profile">
                  <el-icon><User /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item command="/edit">
                  <el-icon><EditPen /></el-icon>写文章
                </el-dropdown-item>
                <el-dropdown-item v-if="isAdmin" command="/admin">
                  <el-icon><Setting /></el-icon>管理后台
                </el-dropdown-item>
              </template>
            </template>
            <el-dropdown-item command="github" divided>
              <el-icon><Link /></el-icon>GitHub
            </el-dropdown-item>
            <template v-if="backendEnabled">
              <el-dropdown-item v-if="hasToken" command="logout">
                <el-icon><SwitchButton /></el-icon>退出登录
              </el-dropdown-item>
              <el-dropdown-item v-else command="login">
                <el-icon><User /></el-icon>登录
              </el-dropdown-item>
            </template>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script>
import { markRaw } from "vue";
import { Menu, User, EditPen, Setting, SwitchButton, Moon, Sunny, Link, Document, ArrowDown } from "@element-plus/icons-vue";
import { logout, takeAccessToken, authVersion } from "@/net/index.js";
import { useUserStore } from "@/store/userStore.js";
import { useTheme } from "@/composables/useTheme.js";
import GlobalSearch from "@/components/GlobalSearch.vue";
import { HOME_PATH, REPO } from "@/wiki";
import { BACKEND_ENABLED } from "@/config.js";

const MOBILE_BREAKPOINT = 767;

export default {
  name: "SiteHeader",
  components: { GlobalSearch, User, EditPen, Setting, SwitchButton, Moon, Sunny, Link, Document, ArrowDown },
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
      backendEnabled: BACKEND_ENABLED,
      HOME_PATH,
      REPO,
    };
  },
  computed: {
    hasToken() {
      // 依赖 authVersion：登录/登出会立即自增它，使本计算属性同步刷新
      // （不再依赖 30s 轮询或 storage 事件，storage 事件只在其它标签页触发）。
      authVersion.value;
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
    bumpAuthVersion() {
      authVersion.value++;
    },
    goHome() {
      this.$router.push("/");
    },
    UserLogout() {
      const userStore = useUserStore();
      logout(() => {
        userStore.clearUserInfo();
        window.location.href = "/";
      });
    },
    handleUserCommand(command) {
      if (command === "logout") this.UserLogout();
      else if (command === "github") window.open(REPO, "_blank", "noopener,noreferrer");
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
    // 其它标签页登录/登出会触发 storage 事件，这里同步刷新本标签页的登录态。
    window.addEventListener("storage", this.bumpAuthVersion);
  },
  beforeUnmount() {
    window.removeEventListener("resize", this.handleResize);
    window.removeEventListener("scroll", this.handleScroll);
    window.removeEventListener("storage", this.bumpAuthVersion);
    clearTimeout(this.resizeTimeout);
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
  background: transparent;
  border: 1px solid var(--border);
  border-radius: 50%;
  width: 36px;
  height: 36px;
  color: var(--text-secondary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.2s ease, background 0.2s ease, color 0.2s ease;
}
.theme-toggle:hover {
  border-color: var(--border-strong);
  background: var(--bg-hover);
  color: var(--text-primary);
}

.mobile-nav { display: flex; align-items: center; gap: 8px; }

.el-dropdown-link {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 3px 8px 3px 3px;
  border-radius: 999px;
  border: 1px solid transparent;
  transition: background 0.2s ease, border-color 0.2s ease;
}
.el-dropdown-link:hover {
  background: var(--bg-subtle);
  border-color: var(--border);
}
.el-dropdown-link .caret { color: var(--text-muted); }
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
