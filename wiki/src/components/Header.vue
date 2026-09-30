<template>
  <header class="site-header" :class="{ scrolled: isScrolled }">
    <div class="header-inner">
      <router-link to="/" class="logo" aria-label="返回首页">
        <img src="/svg/Text_logo_hor.svg" alt="XMUM Wiki" class="logo-img" />
      </router-link>

      <nav v-if="!isMobileView" class="primary-nav" aria-label="主导航">
        <router-link :to="`/docs/${HOME_PATH}`" :class="{ 'is-active': inDocs }">文档</router-link>
        <router-link to="/tags" :class="{ 'is-active': $route.path.startsWith('/tags') }">标签</router-link>
        <router-link to="/changes" :class="{ 'is-active': $route.path === '/changes' }">动态</router-link>
        <router-link to="/contributors" :class="{ 'is-active': $route.path.startsWith('/contributors') }">贡献榜</router-link>
      </nav>

      <div class="header-actions">
        <button type="button" class="search-trigger" aria-label="搜索文档" @click="openSearch">
          <el-icon :size="16"><Search /></el-icon>
          <span class="search-label">搜索文档…</span>
          <span class="search-kbd">{{ shortcutLabel }}</span>
        </button>

        <a
          v-if="!isMobileView"
          class="icon-btn"
          :href="REPO"
          target="_blank"
          rel="noopener noreferrer"
          aria-label="GitHub 仓库"
          title="GitHub"
        >
          <svg viewBox="0 0 16 16" width="17" height="17" aria-hidden="true" fill="currentColor">
            <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z"/>
          </svg>
        </a>

        <button
          type="button"
          class="icon-btn"
          :aria-label="isDark ? '切换到亮色' : '切换到暗色'"
          :title="isDark ? '切换到亮色' : '切换到暗色'"
          @click="toggleTheme($event)"
        >
          <el-icon :size="17"><Sunny v-if="isDark" /><Moon v-else /></el-icon>
        </button>

        <el-dropdown
          v-if="backendEnabled && hasToken"
          trigger="click"
          popper-class="notif-menu"
          :show-timeout="80"
          :popper-options="popperKeepInset"
          @visible-change="onBellVisible"
        >
          <button type="button" class="icon-btn" aria-label="通知">
            <el-icon :size="17"><Bell /></el-icon>
            <span v-if="unreadCount > 0" class="bell-count">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
          </button>
          <template #dropdown>
            <div class="nm-card">
              <div class="nm-head">
                <span>通知</span>
                <el-button v-if="unreadCount > 0" link size="small" @click="markAllRead">全部已读</el-button>
              </div>
              <div v-if="!notifications.length" class="nm-empty">
                <el-icon :size="22"><Bell /></el-icon>
                <span>暂无通知</span>
              </div>
              <ul v-else class="nm-list">
                <li
                  v-for="n in notifications"
                  :key="n.id"
                  :class="{ unread: !n.read }"
                  @click="openNotification(n)"
                >
                  <div class="nm-title">{{ n.title }}<span v-if="!n.read" class="nm-dot"></span></div>
                  <div class="nm-content">{{ n.content }}</div>
                  <div class="nm-time">{{ n.createTime }}</div>
                </li>
              </ul>
            </div>
          </template>
        </el-dropdown>

        <template v-if="!isMobileView && backendEnabled">
          <router-link v-if="!hasToken" to="/login" class="login-btn">登录</router-link>
          <el-dropdown
            v-else
            @command="handleUserCommand"
            trigger="click"
            popper-class="user-menu"
            :show-timeout="80"
          >
            <button type="button" class="account-btn" aria-label="账户菜单">
              <el-avatar v-if="userAvatar" :src="userAvatar" :size="28" />
              <el-avatar v-else :size="28">{{ userName.charAt(0) }}</el-avatar>
              <span class="user-name">{{ userName }}</span>
              <el-icon class="caret" :size="12"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <div class="um-card">
                <div class="um-id">
                  <el-avatar v-if="userAvatar" :src="userAvatar" :size="36" />
                  <el-avatar v-else :size="36">{{ userName.charAt(0) }}</el-avatar>
                  <div class="um-meta">
                    <span class="um-name">
                      {{ userName }}
                      <span v-if="isSuperAdmin" class="um-badge um-badge-super">超级管理员</span>
                      <span v-else-if="isAdmin" class="um-badge">管理员</span>
                    </span>
                    <span class="um-email">{{ userEmail }}</span>
                  </div>
                </div>
                <el-dropdown-menu>
                  <el-dropdown-item command="/profile">
                    <el-icon><User /></el-icon>个人中心
                  </el-dropdown-item>
                  <el-dropdown-item command="/edit">
                    <el-icon><EditPen /></el-icon>写文章
                  </el-dropdown-item>
                  <el-dropdown-item command="/favorites">
                    <el-icon><Star /></el-icon>收藏与历史
                  </el-dropdown-item>
                  <el-dropdown-item command="/feedback">
                    <el-icon><ChatDotRound /></el-icon>反馈
                  </el-dropdown-item>
                  <el-dropdown-item v-if="isAdmin" command="/admin">
                    <el-icon><Setting /></el-icon>管理后台
                  </el-dropdown-item>
                  <el-dropdown-item divided command="logout" class="um-logout">
                    <el-icon><SwitchButton /></el-icon>退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </div>
            </template>
          </el-dropdown>
        </template>

        <el-dropdown
          v-if="isMobileView"
          trigger="click"
          popper-class="nav-menu"
          :show-timeout="80"
          :popper-options="popperKeepInset"
          @command="handleMobileNavCommand"
          @visible-change="menuOpen = $event"
        >
          <button type="button" class="icon-btn" :class="{ open: menuOpen }" aria-label="导航菜单">
            <el-icon :size="18"><Close v-if="menuOpen" /><Menu v-else /></el-icon>
          </button>
          <template #dropdown>
            <div class="nv-card">
              <!-- 已登录时先亮明身份，与桌面端用户菜单同一套信息 -->
              <div v-if="backendEnabled && hasToken" class="nv-id">
                <el-avatar v-if="userAvatar" :src="userAvatar" :size="36" />
                <el-avatar v-else :size="36">{{ userName.charAt(0) }}</el-avatar>
                <div class="nv-meta">
                  <span class="nv-name">
                    {{ userName }}
                    <span v-if="isSuperAdmin" class="nv-badge nv-badge-super">超级管理员</span>
                    <span v-else-if="isAdmin" class="nv-badge">管理员</span>
                  </span>
                  <span class="nv-email">{{ userEmail }}</span>
                </div>
              </div>

              <el-dropdown-menu>
                <li class="nv-group">浏览</li>
                <el-dropdown-item :command="`/docs/${HOME_PATH}`">
                  <el-icon><Document /></el-icon>文档
                </el-dropdown-item>
                <el-dropdown-item command="/tags">
                  <el-icon><PriceTag /></el-icon>标签
                </el-dropdown-item>
                <el-dropdown-item command="/changes">
                  <el-icon><Clock /></el-icon>站点动态
                </el-dropdown-item>
                <el-dropdown-item command="/contributors">
                  <el-icon><Trophy /></el-icon>贡献榜
                </el-dropdown-item>

                <template v-if="backendEnabled && hasToken">
                  <li class="nv-group">我的</li>
                  <el-dropdown-item command="/profile">
                    <el-icon><User /></el-icon>个人中心
                  </el-dropdown-item>
                  <el-dropdown-item command="/edit">
                    <el-icon><EditPen /></el-icon>写文章
                  </el-dropdown-item>
                  <el-dropdown-item command="/favorites">
                    <el-icon><Star /></el-icon>收藏与历史
                  </el-dropdown-item>
                  <el-dropdown-item command="/feedback">
                    <el-icon><ChatDotRound /></el-icon>反馈
                  </el-dropdown-item>
                  <el-dropdown-item v-if="isAdmin" command="/admin">
                    <el-icon><Setting /></el-icon>管理后台
                  </el-dropdown-item>
                </template>

                <el-dropdown-item command="github" divided>
                  <el-icon><Link /></el-icon>GitHub
                </el-dropdown-item>
                <template v-if="backendEnabled">
                  <el-dropdown-item v-if="hasToken" command="logout" class="nv-logout">
                    <el-icon><SwitchButton /></el-icon>退出登录
                  </el-dropdown-item>
                  <el-dropdown-item v-else command="login" class="nv-login">
                    <el-icon><User /></el-icon>登录 / 注册
                  </el-dropdown-item>
                </template>
              </el-dropdown-menu>
            </div>
          </template>
        </el-dropdown>
      </div>
    </div>
  </header>
</template>

<script>
import {
  Menu, X as Close, UserRound as User, SquarePen as EditPen, Settings as Setting, LogOut as SwitchButton,
  Moon, Sun as Sunny, Github as Link, FileText as Document, ChevronDown as ArrowDown, Bell, Star,
  MessageSquareText as ChatDotRound, Trophy, Tags as PriceTag, History as Clock, Search,
} from "lucide-vue-next";
import { logout, takeAccessToken, authVersion,
  getNotifications, getUnreadCount, readNotification, readAllNotifications } from "@/net/index.js";
import { useUserStore } from "@/store/userStore.js";
import { useTheme } from "@/composables/useTheme.js";
import { openPalette } from "@/composables/usePalette.js";
import { HOME_PATH, REPO } from "@/wiki";
import { BACKEND_ENABLED } from "@/config.js";

const MOBILE_BREAKPOINT = 767;

export default {
  name: "SiteHeader",
  components: { Menu, Close, User, EditPen, Setting, SwitchButton, Moon, Sunny, Link, Document, ArrowDown, Bell, Star, ChatDotRound, Trophy, PriceTag, Clock, Search },
  setup() {
    const { isDark, toggleTheme } = useTheme();
    return { isDark, toggleTheme };
  },
  data() {
    const isMac = typeof navigator !== "undefined" && /Mac|iPhone|iPad/i.test(navigator.platform || navigator.userAgent || "");
    return {
      shortcutLabel: isMac ? "⌘K" : "Ctrl K",
      isMobileView: false,
      isScrolled: false,
      menuOpen: false,
      // 弹层默认可以贴到视口边缘；留 12px 让面板不与屏幕边框粘在一起
      popperKeepInset: {
        modifiers: [{ name: "preventOverflow", options: { padding: 12 } }],
      },
      resizeTimeout: null,
      backendEnabled: BACKEND_ENABLED,
      HOME_PATH,
      REPO,
      notifications: [],
      unreadCount: 0,
      notifyTimer: null,
    };
  },
  watch: {
    hasToken(v) {
      if (v) this.loadUnread();
      else { this.unreadCount = 0; this.notifications = []; }
    },
    // 每次路由切换刷新未读数，让通知无需手动刷新页面即可更新
    $route() {
      this.loadUnread();
    },
  },
  computed: {
    inDocs() {
      return this.$route.path.startsWith("/docs");
    },
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
    userEmail() {
      return useUserStore().userInfo?.userEmail || "";
    },
    isAdmin() {
      return useUserStore().isAdmin;
    },
    isSuperAdmin() {
      return useUserStore().isSuperAdmin;
    },
  },
  methods: {
    bumpAuthVersion() {
      authVersion.value++;
    },
    openSearch() {
      openPalette();
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
    loadUnread() {
      if (!this.hasToken) { this.unreadCount = 0; return; }
      getUnreadCount((d) => { this.unreadCount = Number(d && d.count) || 0; }, () => {});
    },
    loadNotifications() {
      getNotifications((d) => { this.notifications = d || []; }, () => {});
    },
    onBellVisible(visible) {
      if (visible) { this.loadUnread(); this.loadNotifications(); }
    },
    onVisibilityRefresh() {
      // 标签页重新可见 / 获得焦点时立即刷新未读数（回到页面即见最新通知）
      if (document.visibilityState === "visible") this.loadUnread();
    },
    openNotification(n) {
      if (!n.read) {
        readNotification(n.id, () => {}, () => {});
        n.read = true;
        this.unreadCount = Math.max(0, this.unreadCount - 1);
      }
      if (n.link) this.$router.push(n.link).catch(() => {});
    },
    markAllRead() {
      readAllNotifications(() => {
        this.unreadCount = 0;
        this.notifications.forEach((n) => { n.read = true; });
      }, () => {});
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
    this.loadUnread();
    // 近实时轮询：每 10s 拉一次未读数（仅在标签页可见时，隐藏不打扰）；
    // 回到前台 / 切路由 / 聚焦时还会即时刷新（见 watch 与 visibilitychange/focus）。
    this.notifyTimer = setInterval(() => {
      if (document.visibilityState === "visible") this.loadUnread();
    }, 10000);
    document.addEventListener("visibilitychange", this.onVisibilityRefresh);
    window.addEventListener("focus", this.onVisibilityRefresh);
  },
  beforeUnmount() {
    window.removeEventListener("resize", this.handleResize);
    window.removeEventListener("scroll", this.handleScroll);
    window.removeEventListener("storage", this.bumpAuthVersion);
    document.removeEventListener("visibilitychange", this.onVisibilityRefresh);
    window.removeEventListener("focus", this.onVisibilityRefresh);
    clearTimeout(this.resizeTimeout);
    clearInterval(this.notifyTimer);
  },
};
</script>

<style scoped>
.site-header {
  position: sticky;
  top: 0;
  z-index: 1000;
  height: var(--header-height);
  border-bottom: 1px solid transparent;
  background: var(--glass-bg);
  backdrop-filter: saturate(180%) blur(18px);
  -webkit-backdrop-filter: saturate(180%) blur(18px);
  transition: border-color 0.25s ease, background 0.25s ease;
}
.site-header.scrolled { border-bottom-color: var(--border); }

.header-inner {
  display: flex;
  align-items: center;
  gap: 28px;
  height: 100%;
  padding: 0 max(20px, env(safe-area-inset-left));
}

.logo {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  border-radius: 8px;
  transition: opacity 0.2s ease;
}
.logo:hover { opacity: 0.72; text-decoration: none; }
.logo-img { display: block; width: auto; height: 24px; }
html.dark .logo-img { filter: brightness(0) invert(1); }

/* 主导航：当前所在栏目用底色胶囊标出 */
.primary-nav { display: flex; align-items: center; gap: 2px; }
.primary-nav a {
  padding: 6px 12px;
  border-radius: 999px;
  color: var(--text-secondary);
  font-size: 14px;
  font-weight: 500;
  transition: color 0.15s ease, background 0.15s ease;
}
.primary-nav a:hover { background: var(--bg-hover); color: var(--text-primary); text-decoration: none; }
.primary-nav a.is-active { background: var(--bg-hover); color: var(--text-primary); font-weight: 600; }

.header-actions {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  min-width: 0;
}

/* 搜索入口：看起来像输入框，点开的是 ⌘K 面板 */
.search-trigger {
  display: inline-flex;
  flex: 0 1 260px;
  align-items: center;
  gap: 8px;
  min-width: 0;
  height: 36px;
  margin-right: 6px;
  padding: 0 6px 0 12px;
  border: 1px solid var(--border);
  border-radius: 10px;
  background: var(--bg-subtle);
  color: var(--text-muted);
  font: inherit;
  font-size: 13.5px;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
}
.search-trigger:hover {
  border-color: var(--border-strong);
  background: var(--bg-surface);
  color: var(--text-secondary);
  box-shadow: var(--shadow-xs);
}
.search-label { flex: 1; overflow: hidden; text-align: left; text-overflow: ellipsis; white-space: nowrap; }
.search-kbd {
  flex-shrink: 0;
  padding: 2px 6px;
  border: 1px solid var(--border);
  border-radius: 6px;
  background: var(--bg-surface);
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 600;
}

.icon-btn {
  position: relative;
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}
.icon-btn:hover,
.icon-btn.open { background: var(--bg-hover); color: var(--text-primary); text-decoration: none; }

.bell-count {
  position: absolute;
  top: 3px;
  right: 2px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border: 2px solid var(--bg-page);
  border-radius: 999px;
  background: var(--danger);
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  line-height: 12px;
  text-align: center;
  box-sizing: content-box;
}

.login-btn {
  display: inline-flex;
  align-items: center;
  height: 34px;
  margin-left: 6px;
  padding: 0 16px;
  border-radius: 999px;
  background: var(--accent);
  color: var(--accent-contrast);
  font-size: 13.5px;
  font-weight: 600;
  transition: background 0.15s ease, transform 0.15s ease;
}
.login-btn:hover { background: var(--accent-hover); color: var(--accent-contrast); text-decoration: none; }
.login-btn:active { transform: scale(0.97); }

.account-btn {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-left: 4px;
  padding: 3px 9px 3px 3px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: transparent;
  font: inherit;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}
.account-btn:hover { border-color: var(--border-strong); background: var(--bg-hover); }
.account-btn :deep(.el-avatar) {
  background: var(--accent);
  color: var(--accent-contrast);
  font-size: 12px;
  font-weight: 600;
}
.account-btn .caret { color: var(--text-muted); }
.user-name {
  max-width: 88px;
  overflow: hidden;
  color: var(--text-primary);
  font-size: 13.5px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 中等宽度：搜索入口缩成图标，给导航腾位置 */
@media (max-width: 1080px) {
  .header-inner { gap: 18px; }
  .search-trigger { flex: 0 0 36px; justify-content: center; margin-right: 0; padding: 0; border-color: transparent; background: transparent; }
  .search-trigger:hover { border-color: transparent; background: var(--bg-hover); box-shadow: none; }
  .search-label,
  .search-kbd { display: none; }
}

@media (max-width: 767px) {
  .header-inner { gap: 10px; padding: 0 max(12px, env(safe-area-inset-left)); }
  .logo-img { height: 22px; }
  .header-actions { gap: 2px; }
}
</style>

<style>
.user-menu.el-dropdown__popper {
  overflow: hidden;
  border: 1px solid var(--border) !important;
  border-radius: 14px !important;
  box-shadow: var(--shadow-lg) !important;
}
.user-menu.el-dropdown__popper .el-popper__arrow { display: none; }
.user-menu .um-card { min-width: 232px; padding: 6px; }
.user-menu .um-id {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
  padding: 10px 10px 12px;
  border-bottom: 1px solid var(--border);
}
.user-menu .um-id .el-avatar {
  flex-shrink: 0;
  background: var(--accent);
  color: var(--accent-contrast);
  font-weight: 600;
}
.user-menu .um-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 2px;
}
.user-menu .um-name {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.2;
}
.user-menu .um-badge {
  padding: 1px 6px;
  border: 1px solid var(--border);
  border-radius: 5px;
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-size: 10.5px;
  font-weight: 600;
}
.user-menu .um-badge-super {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--accent-contrast);
}
.user-menu .um-email {
  overflow: hidden;
  color: var(--text-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-menu .el-dropdown-menu {
  padding: 4px 0 0;
  border: none;
  background: transparent;
}
.user-menu .el-dropdown-menu__item {
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 1px 0;
  padding: 8px 10px;
  border-radius: 8px;
  color: var(--text-body);
  font-size: 13.5px;
  line-height: 1.3;
}
.user-menu .el-dropdown-menu__item .el-icon {
  margin: 0;
  color: var(--text-muted);
  font-size: 16px;
}
.user-menu .el-dropdown-menu__item:not(.is-disabled):hover {
  background: var(--bg-subtle);
  color: var(--text-primary);
}
.user-menu .el-dropdown-menu__item:not(.is-disabled):hover .el-icon {
  color: var(--text-primary);
}
.user-menu .el-dropdown-menu__item--divided {
  margin-top: 5px;
  padding-top: 9px;
  border-top: 1px solid var(--border);
}
.user-menu .el-dropdown-menu__item--divided::before { display: none; }
.user-menu .um-logout,
.user-menu .um-logout .el-icon { color: var(--danger); }
.user-menu .um-logout:not(.is-disabled):hover,
.user-menu .um-logout:not(.is-disabled):hover .el-icon { color: var(--danger); }
.user-menu .um-logout:not(.is-disabled):hover { background: var(--danger-soft); }

/* 移动端导航下拉：此前完全是 Element Plus 默认样式——98px 宽、贴着屏幕右边缘、
   行高只有 30 出头，和桌面端用户菜单完全不是一套东西。 */
.nav-menu.el-dropdown__popper {
  overflow: hidden;
  border: 1px solid var(--border) !important;
  border-radius: 14px !important;
  box-shadow: var(--shadow-lg) !important;
}
.nav-menu.el-dropdown__popper .el-popper__arrow { display: none; }
.nav-menu .nv-card {
  width: 232px;
  max-width: calc(100vw - 24px);
  padding: 6px;
}
.nav-menu .nv-id {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
  padding: 10px 10px 12px;
  border-bottom: 1px solid var(--border);
}
.nav-menu .nv-id .el-avatar {
  flex-shrink: 0;
  background: var(--accent);
  color: var(--accent-contrast);
  font-weight: 600;
}
.nav-menu .nv-meta { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.nav-menu .nv-name {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
  line-height: 1.2;
}
.nav-menu .nv-badge {
  padding: 1px 6px;
  border: 1px solid var(--border);
  border-radius: 5px;
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-size: 10.5px;
  font-weight: 600;
}
.nav-menu .nv-badge-super {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--accent-contrast);
}
.nav-menu .nv-email {
  overflow: hidden;
  color: var(--text-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.nav-menu .el-dropdown-menu {
  padding: 4px 0 0;
  border: none;
  background: transparent;
}
/* 分组小标题：条目变多之后（浏览 7 项 + 我的 5 项）没有分组就是一长条 */
.nav-menu .nv-group {
  padding: 7px 10px 3px;
  color: var(--text-muted);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: .08em;
  list-style: none;
}
.nav-menu .el-dropdown-menu__item {
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 40px;
  margin: 1px 0;
  padding: 8px 10px;
  border-radius: 8px;
  color: var(--text-body);
  font-size: 13.5px;
  line-height: 1.3;
}
.nav-menu .el-dropdown-menu__item .el-icon {
  margin: 0;
  color: var(--text-muted);
  font-size: 16px;
}
.nav-menu .el-dropdown-menu__item:not(.is-disabled):hover {
  background: var(--bg-subtle);
  color: var(--text-primary);
}
.nav-menu .el-dropdown-menu__item:not(.is-disabled):hover .el-icon { color: var(--text-primary); }
.nav-menu .el-dropdown-menu__item--divided {
  margin-top: 5px;
  padding-top: 9px;
  border-top: 1px solid var(--border);
}
.nav-menu .el-dropdown-menu__item--divided::before { display: none; }
.nav-menu .nv-logout,
.nav-menu .nv-logout .el-icon { color: var(--danger); }
.nav-menu .nv-logout:not(.is-disabled):hover,
.nav-menu .nv-logout:not(.is-disabled):hover .el-icon { color: var(--danger); }
.nav-menu .nv-logout:not(.is-disabled):hover { background: var(--danger-soft); }
.nav-menu .nv-login,
.nav-menu .nv-login .el-icon { color: var(--accent); font-weight: 600; }

/* 通知下拉 */
.notif-menu.el-dropdown__popper {
  overflow: hidden;
  border: 1px solid var(--border) !important;
  border-radius: 14px !important;
  box-shadow: var(--shadow-lg) !important;
}
.notif-menu.el-dropdown__popper .el-popper__arrow { display: none; }
.notif-menu .nm-card { width: 320px; max-width: 86vw; padding: 0; }
.notif-menu .nm-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  border-bottom: 1px solid var(--border);
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}
.notif-menu .nm-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px 14px;
  text-align: center;
  color: var(--text-muted);
  font-size: 13px;
}
.notif-menu .nm-list {
  list-style: none;
  margin: 0;
  padding: 4px;
  max-height: 380px;
  overflow-y: auto;
}
.notif-menu .nm-list li {
  padding: 9px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s ease;
}
.notif-menu .nm-list li:hover { background: var(--bg-subtle); }
.notif-menu .nm-list li.unread { background: var(--accent-soft); }
.notif-menu .nm-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-primary);
}
.notif-menu .nm-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--accent);
  flex-shrink: 0;
}
.notif-menu .nm-content {
  margin-top: 3px;
  font-size: 12.5px;
  color: var(--text-secondary);
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.notif-menu .nm-time {
  margin-top: 4px;
  font-size: 11.5px;
  color: var(--text-muted);
}
</style>
