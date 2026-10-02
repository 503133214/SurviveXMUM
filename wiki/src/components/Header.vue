<template>
  <header class="site-header">
    <div class="header-top">
      <router-link to="/" class="logo" aria-label="返回首页">
        <img src="/svg/Text_logo_hor.svg" alt="XMUM Wiki" class="logo-img" />
      </router-link>
      <span class="site-name">厦大马校生存指南</span>

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
                <!-- 每行是一个真按钮：键盘能 Tab 到，回车 / 空格就能打开并标为已读 -->
                <li v-for="n in notifications" :key="n.id">
                  <button
                    type="button"
                    class="nm-row"
                    :class="{ unread: !n.read }"
                    @click="openNotification(n)"
                  >
                    <span class="nm-title">
                      {{ n.title }}
                      <span v-if="!n.read" class="nm-dot" aria-hidden="true"></span>
                      <span v-if="!n.read" class="sr-only">（未读）</span>
                    </span>
                    <span class="nm-content">{{ n.content }}</span>
                    <span class="nm-time">{{ n.createTime }}</span>
                  </button>
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
              <el-avatar v-if="userAvatar" :src="userAvatar" :size="26" />
              <el-avatar v-else :size="26">{{ userName.charAt(0) }}</el-avatar>
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

    <nav v-if="!isMobileView" class="header-nav" aria-label="主导航">
      <div class="header-nav-inner">
        <router-link :to="`/docs/${HOME_PATH}`" :class="{ 'is-active': inDocs }">文档</router-link>
        <router-link to="/changes" :class="{ 'is-active': $route.path === '/changes' }">动态</router-link>
        <router-link to="/contributors" :class="{ 'is-active': $route.path.startsWith('/contributors') }">贡献榜</router-link>
      </div>
    </nav>
  </header>
</template>

<script>
import {
  Menu, X as Close, UserRound as User, SquarePen as EditPen, Settings as Setting, LogOut as SwitchButton,
  Moon, Sun as Sunny, Github as Link, FileText as Document, ChevronDown as ArrowDown, Bell, Star,
  MessageSquareText as ChatDotRound, Trophy, History as Clock, Search,
} from "lucide-vue-next";
import { logout, takeAccessToken, authVersion,
  getNotifications, getUnreadCount, readNotification, readAllNotifications } from "@/net/index.js";
import { useUserStore } from "@/store/userStore.js";
import { useTheme } from "@/composables/useTheme.js";
import { openPalette } from "@/composables/usePalette.js";
import { HOME_PATH, REPO } from "@/wiki";
import { BACKEND_ENABLED } from "@/config.js";
import { shortcutLabel } from "@/utils/shortcut.js";

const MOBILE_BREAKPOINT = 767;

export default {
  name: "SiteHeader",
  components: { Menu, Close, User, EditPen, Setting, SwitchButton, Moon, Sunny, Link, Document, ArrowDown, Bell, Star, ChatDotRound, Trophy, Clock, Search },
  setup() {
    const { isDark, toggleTheme } = useTheme();
    return { isDark, toggleTheme };
  },
  data() {
    return {
      // 快捷键文案与首页助手区共用同一个判断，避免两处各写一套平台检测
      shortcutLabel: shortcutLabel(),
      isMobileView: false,
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
    window.addEventListener("resize", this.handleResize);
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
    window.removeEventListener("storage", this.bumpAuthVersion);
    document.removeEventListener("visibilitychange", this.onVisibilityRefresh);
    window.removeEventListener("focus", this.onVisibilityRefresh);
    clearTimeout(this.resizeTimeout);
    clearInterval(this.notifyTimer);
  },
};
</script>

<style scoped>
/* 顶栏：实色品牌蓝（参造 ac-wiki），暗色主题换成更沉的一档，颜色都来自 --header-* 令牌。
   第一行是品牌行（logo + 站名 + 搜索 + 操作区），第二行是主导航行。 */
.site-header {
  position: sticky;
  top: 0;
  z-index: 1000;
  background: var(--header-bg);
  color: var(--header-ink);
}
/* 实色顶栏和正文之间本来就有明显色差，滚动后也不加投影，所以不监听滚动 */

/* 全局焦点环是 --accent 蓝，落在同为蓝色的顶栏上几乎看不见，顶栏里改用白色描边 */
.site-header a:focus-visible,
.site-header button:focus-visible { outline-color: var(--header-ink); }

.header-top {
  display: flex;
  align-items: center;
  gap: 14px;
  height: 56px;
  padding: 0 max(20px, env(safe-area-inset-left));
}

.logo {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  border-radius: var(--radius-sm);
  transition: opacity var(--dur) ease;
}
.logo:hover { opacity: 0.8; text-decoration: none; }
/* 品牌蓝底上 logo 一律用白色 */
.logo-img { display: block; width: auto; height: 22px; filter: brightness(0) invert(1); }

.site-name {
  overflow: hidden;
  color: var(--header-ink);
  font-size: 15px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 主导航行：文字链接 + 当前列底部白色指示条。
   和品牌行之间的分隔线用内阴影画在这 40px 里面，不用 border-top：
   边框会让顶栏变成 97px，比 --header-height 多 1px，贴着它定位的进度条和
   sticky 元素就会被顶栏压住一条 */
.header-nav { box-shadow: inset 0 1px 0 var(--header-line); }
.header-nav-inner {
  display: flex;
  align-items: stretch;
  gap: 4px;
  height: 40px;
  padding: 0 max(20px, env(safe-area-inset-left));
}
.header-nav a {
  position: relative;
  display: inline-flex;
  align-items: center;
  padding: 0 13px;
  color: var(--header-ink-dim);
  font-size: var(--fs-ui);
  font-weight: 500;
  transition: color var(--dur) ease;
}
.header-nav a:hover { color: var(--header-ink); text-decoration: none; }
.header-nav a.is-active { color: var(--header-ink); font-weight: 600; }
/* 导航链接占满整行高度，向外的焦点环会压到上方分隔线和顶栏下沿，改为向内画 */
.header-nav a:focus-visible { outline-offset: -2px; }
.header-nav a.is-active::after {
  content: "";
  position: absolute;
  right: 13px;
  bottom: 0;
  left: 13px;
  height: 2px;
  border-radius: var(--radius-xs) var(--radius-xs) 0 0;
  background: var(--header-ink);
}

.header-actions {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
  min-width: 0;
  margin-left: auto;
}

/* 搜索入口：看起来像输入框，点开的是 ⌘K 面板 */
.search-trigger {
  display: inline-flex;
  flex: 0 1 240px;
  align-items: center;
  gap: 8px;
  min-width: 0;
  height: 34px;
  margin-right: 6px;
  padding: 0 6px 0 12px;
  border: 1px solid var(--header-field-border);
  border-radius: var(--radius-sm);
  background: var(--header-field-bg);
  color: var(--header-ink-dim);
  font: inherit;
  font-size: var(--fs-sm);
  cursor: pointer;
  transition: background-color var(--dur) ease, color var(--dur) ease, border-color var(--dur) ease;
}
.search-trigger:hover {
  background: var(--header-hover);
  color: var(--header-ink);
}
.search-label { flex: 1; overflow: hidden; text-align: left; text-overflow: ellipsis; white-space: nowrap; }
.search-kbd {
  flex-shrink: 0;
  padding: 0 6px;
  border: 1px solid var(--header-field-border);
  border-radius: var(--radius-xs);
  color: var(--header-ink-dim);
  font-size: var(--fs-xs);
  font-weight: 500;
  line-height: 20px;
}

.icon-btn {
  position: relative;
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--header-ink-dim);
  cursor: pointer;
  transition: background-color var(--dur) ease, color var(--dur) ease;
}
.icon-btn:hover,
.icon-btn.open { background: var(--header-hover); color: var(--header-ink); text-decoration: none; }

/* 未读数：描一圈顶栏底色，和铃铛图标之间留出缝隙 */
.bell-count {
  position: absolute;
  top: 1px;
  right: 0;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border: 2px solid var(--header-bg);
  border-radius: var(--radius-sm);
  background: var(--header-badge-bg);
  color: var(--header-ink);
  font-size: var(--fs-xs);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 16px;
  text-align: center;
  box-sizing: content-box;
}

/* 顶栏上唯一的实心按钮：反白，悬停时略降亮度，不做缩放 */
.login-btn {
  display: inline-flex;
  align-items: center;
  height: 32px;
  margin-left: 6px;
  padding: 0 14px;
  border-radius: var(--radius-sm);
  background: var(--header-ink);
  color: var(--header-bg);
  font-size: var(--fs-ui);
  font-weight: 500;
  transition: background-color var(--dur) ease;
}
.login-btn:hover { background: var(--header-ink-dim); color: var(--header-bg); text-decoration: none; }

.account-btn {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-left: 4px;
  padding: 3px 9px 3px 3px;
  border: 1px solid var(--header-field-border);
  border-radius: var(--radius-sm);
  background: transparent;
  font: inherit;
  cursor: pointer;
  transition: background-color var(--dur) ease;
}
.account-btn:hover { background: var(--header-hover); }
.account-btn :deep(.el-avatar) {
  background: var(--header-ink);
  color: var(--header-bg);
  font-size: var(--fs-xs);
  font-weight: 600;
}
.account-btn .caret { color: var(--header-ink-dim); }
.user-name {
  max-width: 88px;
  overflow: hidden;
  color: var(--header-ink);
  font-size: var(--fs-sm);
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 中等宽度：搜索入口缩成图标，给导航腾位置 */
@media (max-width: 1080px) {
  .header-top { gap: 10px; }
  .search-trigger { flex: 0 0 34px; justify-content: center; margin-right: 0; padding: 0; border-color: transparent; background: transparent; }
  .search-trigger:hover { border-color: transparent; background: var(--header-hover); }
  .search-label,
  .search-kbd { display: none; }
}

@media (max-width: 900px) {
  .site-name { display: none; }
}

@media (max-width: 767px) {
  .header-top { gap: 8px; height: 56px; padding: 0 max(12px, env(safe-area-inset-left)); }
  .logo-img { height: 20px; }
  .header-actions { gap: 2px; }
}
</style>

<style>
.user-menu.el-dropdown__popper {
  overflow: hidden;
  border: 1px solid var(--border) !important;
  border-radius: var(--radius) !important;
  box-shadow: var(--shadow-md) !important;
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
  padding: 0 6px;
  border: 1px solid var(--border);
  border-radius: var(--radius-xs);
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-size: var(--fs-xs);
  font-weight: 500;
  line-height: 18px;
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
  border-radius: var(--radius-sm);
  color: var(--text-body);
  font-size: var(--fs-ui);
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
  border-radius: var(--radius) !important;
  box-shadow: var(--shadow-md) !important;
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
  padding: 0 6px;
  border: 1px solid var(--border);
  border-radius: var(--radius-xs);
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-size: var(--fs-xs);
  font-weight: 500;
  line-height: 18px;
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
/* 分组小标题：登录后「浏览」「我的」加起来有八项，不分组就是一长条 */
.nav-menu .nv-group {
  padding: 7px 10px 3px;
  color: var(--text-muted);
  font-size: var(--fs-xs);
  font-weight: 600;
  list-style: none;
}
.nav-menu .el-dropdown-menu__item {
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 40px;
  margin: 1px 0;
  padding: 8px 10px;
  border-radius: var(--radius-sm);
  color: var(--text-body);
  font-size: var(--fs-ui);
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
  border-radius: var(--radius) !important;
  box-shadow: var(--shadow-md) !important;
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
  font-size: var(--fs-sm);
}
.notif-menu .nm-list {
  list-style: none;
  margin: 0;
  padding: 4px;
  max-height: 380px;
  overflow-y: auto;
}
/* 行本身是 <button>：清掉按钮默认外观，铺满整行，文字左对齐 */
.notif-menu .nm-row {
  display: block;
  width: 100%;
  padding: 9px 10px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background-color var(--dur) ease;
}
.notif-menu .nm-row:hover { background: var(--bg-subtle); }
.notif-menu .nm-row.unread { background: var(--accent-soft); }
/* 列表有滚动容器，向外的焦点环会被裁掉，改为向内画 */
.notif-menu .nm-row:focus-visible { outline-offset: -2px; }
.notif-menu .nm-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--fs-ui);
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
  overflow-wrap: anywhere;
  font-size: var(--fs-sm);
  color: var(--text-secondary);
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.notif-menu .nm-time {
  display: block;
  margin-top: 4px;
  font-size: var(--fs-xs);
  font-variant-numeric: tabular-nums;
  color: var(--text-muted);
}
</style>
