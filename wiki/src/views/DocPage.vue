<template>
  <div class="doc-page">
    <!-- 阅读进度条 -->
    <div class="reading-progress" :style="{ transform: `scaleX(${progress})` }" aria-hidden="true"></div>

    <!-- 桌面端侧边栏 -->
    <aside v-if="!isMobileView" class="doc-nav">
      <Sidebar :sidebar-items="tree" :current-path="docPath" @navigate="onNavigate" />
    </aside>

    <!-- 移动端：侧栏从左侧滑出 -->
    <el-drawer
      v-if="isMobileView"
      v-model="drawerVisible"
      class="doc-nav-drawer"
      direction="ltr"
      size="86%"
      title="文档目录"
    >
      <Sidebar
        :sidebar-items="tree"
        :current-path="docPath"
        @navigate="onNavigateAndCloseDrawer"
      />
    </el-drawer>

    <div class="doc-main">
      <!-- 移动端吸顶工具条：目录 / 当前标题 / 本页目录 -->
      <div v-if="isMobileView" class="doc-mobilebar">
        <button type="button" class="mb-btn" aria-label="打开文档目录" @click="drawerVisible = true">
          <PanelLeft :size="17" :stroke-width="2" aria-hidden="true" />
          <span>目录</span>
        </button>
        <span class="mb-title" :class="{ shown: progress > 0.02 }">{{ title }}</span>
        <button
          v-if="tocItems.length"
          type="button"
          class="mb-btn"
          aria-label="本页目录"
          @click="tocSheetVisible = true"
        >
          <span>本页</span>
          <TextQuote :size="17" :stroke-width="2" aria-hidden="true" />
        </button>
      </div>

      <div class="doc-grid" :class="{ 'has-toc': showTocRail }">
        <article class="doc-article">
          <!-- 面包屑 -->
          <nav v-if="breadcrumbs.length" class="doc-breadcrumb" aria-label="面包屑">
            <router-link to="/" class="crumb-home" aria-label="首页">
              <House :size="14" :stroke-width="2" aria-hidden="true" />
            </router-link>
            <template v-for="(c, i) in breadcrumbs" :key="i">
              <ChevronRight class="sep" :size="13" :stroke-width="2" aria-hidden="true" />
              <router-link v-if="c.path" :to="`/docs/${c.path}`" aria-current="page">{{ c.label }}</router-link>
              <span v-else class="crumb">{{ c.label }}</span>
            </template>
          </nav>

          <div v-if="isLoading" class="loading-state" aria-busy="true">
            <div class="sk sk-title"></div>
            <div class="sk sk-meta"></div>
            <div class="sk sk-line" v-for="n in 7" :key="n" :style="{ width: `${92 - (n % 3) * 14}%` }"></div>
          </div>

          <div v-else-if="errorLoading" class="doc-state">
            <span class="doc-state-icon"><FileQuestion :size="28" :stroke-width="1.75" /></span>
            <h1>找不到这篇文档</h1>
            <p>地址 <code>{{ docPath }}</code> 可能写错了，或者这篇文档已被移动、删除。</p>
            <div class="doc-state-actions">
              <button type="button" class="act act-primary" @click="openSearch">
                <Search :size="15" :stroke-width="2" />搜索文档
              </button>
              <router-link class="act" to="/">返回首页</router-link>
            </div>
          </div>

          <template v-else>
            <div :key="docPath" class="doc-loaded">
              <header class="doc-header">
                <h1 class="doc-title">{{ title }}</h1>
                <div class="doc-meta">
                  <span v-if="lastUpdated" class="meta-item">
                    <CalendarClock :size="14" :stroke-width="2" aria-hidden="true" />更新于 {{ lastUpdated }}
                  </span>
                  <span v-if="viewCount > 0" class="meta-item">
                    <Eye :size="14" :stroke-width="2" aria-hidden="true" />{{ viewCount.toLocaleString('zh-CN') }} 次浏览
                  </span>
                  <span v-if="readingMinutes" class="meta-item">
                    <Timer :size="14" :stroke-width="2" aria-hidden="true" />约 {{ readingMinutes }} 分钟读完
                  </span>
                </div>

                <nav v-if="pageTags.length" class="doc-tags" aria-label="本页标签">
                  <router-link
                    v-for="t in pageTags"
                    :key="t"
                    class="doc-tag"
                    :to="`/tags/${encodeURIComponent(t)}`"
                  ><Hash :size="12" :stroke-width="2.25" aria-hidden="true" />{{ t }}</router-link>
                </nav>

                <div class="doc-actions" role="toolbar" aria-label="页面操作">
                  <router-link
                    v-if="userStore.isLoggedIn"
                    class="act act-primary"
                    :to="editUrl"
                  ><SquarePen :size="15" :stroke-width="2" aria-hidden="true" />编辑此页</router-link>
                  <router-link
                    v-else
                    class="act act-primary"
                    :to="{ path: '/login', query: { redirect: $route.fullPath } }"
                  ><SquarePen :size="15" :stroke-width="2" aria-hidden="true" />登录后编辑</router-link>

                  <button
                    v-if="userStore.isLoggedIn"
                    type="button"
                    class="act"
                    :class="{ on: favorited }"
                    :aria-pressed="favorited"
                    :disabled="favLoading || notifyLoading"
                    @click="toggleFavorite"
                  >
                    <Star :size="15" :stroke-width="2" :fill="favorited ? 'currentColor' : 'none'" aria-hidden="true" />
                    {{ favorited ? '已收藏' : '收藏' }}
                  </button>
                  <button
                    v-if="userStore.isLoggedIn"
                    type="button"
                    class="act"
                    :class="{ on: notifyUpdates }"
                    :aria-pressed="notifyUpdates"
                    :disabled="favLoading || notifyLoading"
                    title="关注后会同时收藏此页；有新版本发布或有人新开讨论时会收到站内通知"
                    @click="toggleUpdateNotification(!notifyUpdates)"
                  >
                    <BellRing v-if="notifyUpdates" :size="15" :stroke-width="2" aria-hidden="true" />
                    <Bell v-else :size="15" :stroke-width="2" aria-hidden="true" />
                    {{ notifyUpdates ? '已关注' : '关注更新' }}
                  </button>

                  <span class="act-sep" aria-hidden="true"></span>

                  <button type="button" class="act act-quiet" @click="openRevisionHistory">
                    <History :size="15" :stroke-width="2" aria-hidden="true" />版本历史
                  </button>
                  <button type="button" class="act act-quiet" @click="scrollToComments">
                    <MessageSquareText :size="15" :stroke-width="2" aria-hidden="true" />
                    {{ commentCount ? `${commentCount} 条讨论` : '参与讨论' }}
                  </button>
                  <button type="button" class="act act-quiet" @click="copyPageLink">
                    <Link2 :size="15" :stroke-width="2" aria-hidden="true" />复制链接
                  </button>
                  <button
                    v-if="!isMobileView && !showTocRail && tocItems.length"
                    type="button"
                    class="act act-quiet"
                    @click="tocSheetVisible = true"
                  >
                    <TextQuote :size="15" :stroke-width="2" aria-hidden="true" />本页目录
                  </button>
                </div>
              </header>

              <MarkdownRenderer
                v-if="content"
                :content="content"
                :base-path="baseDir"
                @toc="tocItems = $event"
              />
              <div v-else class="doc-state compact">
                <span class="doc-state-icon"><PenLine :size="26" :stroke-width="1.75" /></span>
                <h2>这篇文档还在撰写中</h2>
                <p>知道这方面的经验？欢迎补充内容。</p>
                <router-link class="act act-primary" :to="userStore.isLoggedIn ? editUrl : '/login'">参与撰写</router-link>
              </div>

              <PageContributors
                :contributors="contributors"
                :loading="contributorsLoading"
                :error="contributorsError"
              />

              <!-- 上一篇 / 下一篇 -->
              <nav v-if="prev || next" class="doc-pager" aria-label="翻页">
                <a
                  v-if="prev"
                  class="pager-card"
                  :href="$router.resolve(`/docs/${prev.path}`).href"
                  @click.prevent="onNavigate(prev.path)"
                >
                  <span class="pager-dir"><ArrowLeft :size="14" :stroke-width="2" aria-hidden="true" />上一篇</span>
                  <span class="pager-title">{{ prev.title }}</span>
                  <span v-if="prev.category" class="pager-cat">{{ prev.category }}</span>
                </a>
                <span v-else></span>
                <a
                  v-if="next"
                  class="pager-card align-right"
                  :href="$router.resolve(`/docs/${next.path}`).href"
                  @click.prevent="onNavigate(next.path)"
                >
                  <span class="pager-dir">下一篇<ArrowRight :size="14" :stroke-width="2" aria-hidden="true" /></span>
                  <span class="pager-title">{{ next.title }}</span>
                  <span v-if="next.category" class="pager-cat">{{ next.category }}</span>
                </a>
              </nav>

              <PageComments
                ref="comments"
                :doc-path="docPath"
                @count="commentCount = $event"
              />
            </div>
          </template>
        </article>

        <aside v-if="showTocRail" class="doc-aside">
          <div class="doc-aside-inner">
            <DocToc v-if="tocItems.length && !isLoading && !errorLoading" :items="tocItems" />
          </div>
        </aside>
      </div>

      <SiteFooter compact />
    </div>

    <!-- 本页目录：手机上是底部抽屉，平板宽度从右侧滑出 -->
    <el-drawer
      v-model="tocSheetVisible"
      class="doc-toc-drawer"
      :direction="isMobileView ? 'btt' : 'rtl'"
      :size="isMobileView ? 'auto' : '320px'"
      title="本页目录"
      append-to-body
    >
      <DocToc :items="tocItems" sheet @navigate="tocSheetVisible = false" />
    </el-drawer>

    <PageRevisionHistory
      ref="revisionHistory"
      :doc-path="docPath"
      :page-title="title"
    />
  </div>
</template>

<script>
import MarkdownRenderer from "@/components/MarkdownRenderer.vue";
import DocToc from "@/components/DocToc.vue";
import SiteFooter from "@/components/SiteFooter.vue";
import PageComments from "@/components/PageComments.vue";
import PageContributors from "@/components/PageContributors.vue";
import PageRevisionHistory from "@/components/PageRevisionHistory.vue";
import Sidebar from "@/components/WikiSidebar.vue";
import { ElMessage } from "element-plus";
import {
  ArrowLeft, ArrowRight, Bell, BellRing, CalendarClock, ChevronRight, Eye, FileQuestion, Hash, History,
  House, Link2, MessageSquareText, PanelLeft, PenLine, Search, SquarePen, Star, TextQuote, Timer,
} from "lucide-vue-next";
import {
  tree,
  getPage,
  getAdjacent,
  getBreadcrumbs,
  fetchPageContent,
  HOME_PATH,
} from "@/wiki";
import {
  docFavoriteCheck,
  docFavoriteAdd,
  docFavoriteRemove,
  docFavoriteUpdateNotification,
  getPageContributors,
  recordHistory,
} from "@/net/index.js";
import { useUserStore } from "@/store/userStore.js";
import { openPalette } from "@/composables/usePalette.js";
import { pushRecent } from "@/utils/recentPages.js";

// ≤ 此宽度：侧栏收进抽屉；≥ TOC_RAIL_MIN：右侧常驻本页目录
const MOBILE_BREAKPOINT = 1023;
const TOC_RAIL_MIN = 1200;

export default {
  name: "DocPage",
  components: {
    MarkdownRenderer,
    DocToc,
    SiteFooter,
    PageComments,
    PageContributors,
    PageRevisionHistory,
    Sidebar,
    ArrowLeft, ArrowRight, Bell, BellRing, CalendarClock, ChevronRight, Eye, FileQuestion, Hash, History,
    House, Link2, MessageSquareText, PanelLeft, PenLine, Search, SquarePen, Star, TextQuote, Timer,
  },
  props: {
    pathMatch: { type: String, default: "" },
  },
  data() {
    return {
      tree,
      userStore: useUserStore(),
      content: "",
      title: "",
      pageLastUpdated: "",
      isLoading: false,
      errorLoading: false,
      drawerVisible: false,
      tocSheetVisible: false,
      viewportWidth: typeof window !== "undefined" ? window.innerWidth : 1280,
      resizeTimeout: null,
      tocItems: [],
      progress: 0,
      favorited: false,
      favoriteId: null,
      favLoading: false,
      notifyUpdates: false,
      notifyLoading: false,
      favoriteStateToken: 0,
      viewCount: 0,
      contributors: [],
      contributorsLoading: false,
      contributorsError: "",
      contributorsRequestToken: 0,
      pageRequestToken: 0,
      pageTags: [],
      commentCount: 0,
    };
  },
  computed: {
    isMobileView() {
      return this.viewportWidth <= MOBILE_BREAKPOINT;
    },
    showTocRail() {
      return this.viewportWidth >= TOC_RAIL_MIN;
    },
    // 中文按每分钟约 400 字、英文按约 220 词估算
    readingMinutes() {
      const text = (this.content || "")
        .replace(/```[\s\S]*?```/g, " ")
        .replace(/!\[[^\]]*\]\([^)]*\)/g, " ")
        .replace(/\[([^\]]*)\]\([^)]*\)/g, "$1")
        .replace(/<[^>]+>/g, " ");
      const cjk = (text.match(/[\u4e00-\u9fff]/g) || []).length;
      const words = (text.replace(/[\u4e00-\u9fff]/g, " ").match(/[A-Za-z0-9]+/g) || []).length;
      const minutes = cjk / 400 + words / 220;
      return minutes < 0.5 ? 0 : Math.max(1, Math.round(minutes));
    },
    docPath() {
      return this.pathMatch || HOME_PATH;
    },
    baseDir() {
      const p = this.docPath;
      return p.includes("/") ? p.slice(0, p.lastIndexOf("/")) : "";
    },
    pageMeta() {
      return getPage(this.docPath);
    },
    breadcrumbs() {
      return getBreadcrumbs(this.docPath);
    },
    prev() {
      return getAdjacent(this.docPath).prev;
    },
    next() {
      return getAdjacent(this.docPath).next;
    },
    lastUpdated() {
      const iso = this.pageLastUpdated || this.pageMeta?.lastUpdated;
      if (!iso) return "";
      const d = new Date(iso);
      return Number.isNaN(d.getTime())
        ? ""
        : `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
    },
    editUrl() {
      // 站内编辑（登录后投稿，需审核）
      return `/edit/${this.docPath}`;
    },
  },
  watch: {
    docPath: {
      immediate: true,
      handler(p) {
        this.fetchMarkdown(p);
      },
    },
  },
  methods: {
    async fetchMarkdown(path) {
      const pageRequestToken = ++this.pageRequestToken;
      // 使上一页面尚未返回的收藏/关注请求失效，避免切页后覆盖新页面状态。
      this.favoriteStateToken++;
      this.isLoading = true;
      this.errorLoading = false;
      this.content = "";
      this.title = "";
      this.pageLastUpdated = "";
      this.pageTags = [];
      this.tocItems = [];
      this.tocSheetVisible = false;
      this.commentCount = 0;
      this.favorited = false;
      this.favoriteId = null;
      this.favLoading = false;
      this.notifyUpdates = false;
      this.notifyLoading = false;
      this.viewCount = 0;
      this.contributors = [];
      this.contributorsLoading = false;
      this.contributorsError = "";
      const contributorsRequestToken = ++this.contributorsRequestToken;
      try {
        const detail = await fetchPageContent(path, true);
        if (this.docPath !== path || pageRequestToken !== this.pageRequestToken) return;
        let raw = detail.content || "";

        // 去掉可能残留的 YAML frontmatter
        raw = raw.replace(/^﻿?---\r?\n[\s\S]*?\r?\n---\r?\n?/, "");

        // 若正文首行是 H1，则用它作标题并从正文移除，避免与页头重复
        const h1 = raw.match(/^\s*#\s+(.+?)\s*#*\s*$/m);
        let h1Text = "";
        if (h1 && raw.indexOf(h1[0]) < 4) {
          h1Text = h1[1].trim();
          raw = raw.replace(h1[0], "").replace(/^\s*\n/, "");
        }

        this.title = detail.title || h1Text || path.split("/").pop();
        this.pageLastUpdated = detail.lastUpdated || "";
        this.viewCount = detail.viewCount || 0;
        this.pageTags = (detail.tags || []).map((t) => (t || "").trim()).filter(Boolean);
        this.content = raw.trim();
        pushRecent({ path, title: this.title, icon: this.pageMeta?.icon || detail.icon || "" });
        this.loadContributors(path, contributorsRequestToken);
        this.afterLoad(path);
        this.openRevisionFromQuery(path);
      } catch (e) {
        if (this.docPath !== path || pageRequestToken !== this.pageRequestToken) return;
        this.errorLoading = true;
        this.title = "页面未找到";
        this.content = "";
      } finally {
        if (this.docPath === path && pageRequestToken === this.pageRequestToken) {
          this.isLoading = false;
          this.scrollToTopOrHash();
        }
      }
    },
    scrollToTopOrHash() {
      this.$nextTick(() => {
        if (this.$route.hash) {
          const raw = this.$route.hash.slice(1);
          let decoded = raw;
          try { decoded = decodeURIComponent(raw); } catch { /* 原样使用 */ }
          const el = document.getElementById(decoded) || document.getElementById(raw);
          if (el) {
            el.scrollIntoView({ behavior: "smooth" });
            return;
          }
        }
        window.scrollTo({ top: 0, behavior: "smooth" });
      });
    },
    openSearch() {
      openPalette();
    },
    async copyPageLink() {
      const url = window.location.origin + this.$router.resolve(`/docs/${this.docPath}`).href;
      try {
        await navigator.clipboard.writeText(url);
        ElMessage.success("已复制本页链接");
      } catch {
        ElMessage.info(url);
      }
    },
    loadContributors(path, requestToken) {
      this.contributorsLoading = true;
      getPageContributors(path, (data) => {
        if (this.docPath !== path || requestToken !== this.contributorsRequestToken) return;
        this.contributors = Array.isArray(data) ? data : [];
        this.contributorsLoading = false;
      }, (message) => {
        if (this.docPath !== path || requestToken !== this.contributorsRequestToken) return;
        this.contributorsError = message || "贡献者信息加载失败";
        this.contributorsLoading = false;
      });
    },
    // 从站点动态点「查看改动」过来会带 ?rev=<版本 id>：正文加载完直接打开那一次改动的差异。
    // 用完即从地址栏移除，免得关掉抽屉后刷新又弹出来。afterLoad 对未登录用户会提前返回，所以单独放。
    openRevisionFromQuery(path) {
      const rev = this.$route.query.rev;
      if (!rev || this.docPath !== path) return;
      this.$nextTick(() => this.$refs.revisionHistory?.open(String(rev)));
      const query = { ...this.$route.query };
      delete query.rev;
      this.$router.replace({ query, hash: this.$route.hash });
    },
    afterLoad(path) {
      // 仅登录用户：记录浏览历史 + 查询收藏状态
      if (!this.userStore.isLoggedIn) return;
      recordHistory(path);
      const requestToken = ++this.favoriteStateToken;
      docFavoriteCheck(path, (d) => {
        if (this.docPath !== path || requestToken !== this.favoriteStateToken) return;
        this.favorited = !!(d && d.favorited);
        this.favoriteId = d && d.id ? d.id : null;
        this.notifyUpdates = !!(d && d.notifyUpdates);
      }, () => {});
    },
    scrollToComments() {
      const el = this.$refs.comments && this.$refs.comments.$el;
      if (!el) return;
      el.scrollIntoView({ behavior: "smooth", block: "start" });
    },
    toggleFavorite() {
      if (!this.userStore.isLoggedIn) { this.$router.push("/login"); return; }
      if (this.favLoading || this.notifyLoading) return;
      const targetPath = this.docPath;
      const requestToken = ++this.favoriteStateToken;
      this.favLoading = true;
      if (this.favorited && this.favoriteId) {
        docFavoriteRemove(this.favoriteId, () => {
          if (this.docPath !== targetPath || requestToken !== this.favoriteStateToken) return;
          this.favorited = false;
          this.favoriteId = null;
          this.notifyUpdates = false;
          this.favLoading = false;
          ElMessage.success("已取消收藏");
        }, (m) => {
          if (this.docPath !== targetPath || requestToken !== this.favoriteStateToken) return;
          this.favLoading = false;
          ElMessage.error(m || "操作失败");
        });
      } else {
        docFavoriteAdd(targetPath, false, (d) => {
          if (this.docPath !== targetPath || requestToken !== this.favoriteStateToken) return;
          this.favorited = true;
          this.favoriteId = d && d.id ? d.id : null;
          this.notifyUpdates = !!(d && d.notifyUpdates);
          this.favLoading = false;
          ElMessage.success("已收藏");
        }, (m) => {
          if (this.docPath !== targetPath || requestToken !== this.favoriteStateToken) return;
          this.favLoading = false;
          ElMessage.error(m || "操作失败");
        });
      }
    },
    toggleUpdateNotification(enabled) {
      if (!this.userStore.isLoggedIn) { this.$router.push("/login"); return; }
      if (this.notifyLoading || this.favLoading) return;
      const nextValue = !!enabled;
      const previousValue = this.notifyUpdates;
      const targetPath = this.docPath;
      const requestToken = ++this.favoriteStateToken;
      this.notifyUpdates = nextValue;
      this.notifyLoading = true;

      const success = (d) => {
        if (this.docPath !== targetPath || requestToken !== this.favoriteStateToken) return;
        this.notifyUpdates = d && typeof d.notifyUpdates === "boolean"
          ? d.notifyUpdates
          : nextValue;
        if (d && d.id) this.favoriteId = d.id;
        if (nextValue) this.favorited = true;
        this.notifyLoading = false;
        ElMessage.success(nextValue
          ? "已关注，页面更新后会通知你"
          : "已取消更新通知");
      };
      const failure = (message) => {
        if (this.docPath !== targetPath || requestToken !== this.favoriteStateToken) return;
        this.notifyUpdates = previousValue;
        this.notifyLoading = false;
        ElMessage.error(message || "操作失败");
      };

      if (!this.favorited || !this.favoriteId) {
        if (!nextValue) {
          this.notifyUpdates = false;
          this.notifyLoading = false;
          return;
        }
        docFavoriteAdd(targetPath, true, success, failure);
        return;
      }
      docFavoriteUpdateNotification(this.favoriteId, nextValue, success, failure);
    },
    openRevisionHistory() {
      this.$refs.revisionHistory?.open();
    },
    onNavigate(newPage) {
      this.$router.push(`/docs/${newPage}`);
    },
    onNavigateAndCloseDrawer(newPage) {
      this.onNavigate(newPage);
      this.drawerVisible = false;
    },
    checkMobileView() {
      this.viewportWidth = window.innerWidth;
    },
    handleResize() {
      clearTimeout(this.resizeTimeout);
      this.resizeTimeout = setTimeout(this.checkMobileView, 100);
    },
    handleScroll() {
      const h = document.documentElement;
      const scrollable = h.scrollHeight - h.clientHeight;
      this.progress = scrollable > 0 ? Math.min(h.scrollTop / scrollable, 1) : 0;
    },
  },
  mounted() {
    this.checkMobileView();
    window.addEventListener("resize", this.handleResize);
    window.addEventListener("scroll", this.handleScroll, { passive: true });
  },
  beforeUnmount() {
    window.removeEventListener("resize", this.handleResize);
    window.removeEventListener("scroll", this.handleScroll);
    clearTimeout(this.resizeTimeout);
  },
};
</script>

<style scoped>
.doc-page {
  display: grid;
  grid-template-columns: 284px minmax(0, 1fr);
  min-height: calc(100vh - var(--header-height));
  background: var(--bg-page);
}

/* 阅读进度条 */
.reading-progress {
  position: fixed;
  top: var(--header-height);
  left: 0;
  right: 0;
  z-index: 999;
  height: 2px;
  background: var(--accent);
  transform: scaleX(0);
  transform-origin: 0 50%;
  transition: transform 0.1s linear;
  pointer-events: none;
}

/* ---- 左侧文档目录 ---- */
.doc-nav {
  position: sticky;
  top: var(--header-height);
  align-self: start;
  height: calc(100vh - var(--header-height));
  height: calc(100dvh - var(--header-height));
  border-right: 1px solid var(--border);
  background: var(--bg-subtle);
}

.doc-main { display: flex; min-width: 0; flex-direction: column; }

/* ---- 正文 + 右侧本页目录 ---- */
.doc-grid {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(0, var(--measure));
  justify-content: center;
  gap: 64px;
  padding: 36px clamp(24px, 4vw, 56px) 72px;
}
.doc-grid.has-toc { grid-template-columns: minmax(0, var(--measure)) 220px; }

.doc-article { min-width: 0; }
.doc-loaded { animation: fade-up 0.45s var(--ease-out) both; }

.doc-aside { min-width: 0; }
.doc-aside-inner {
  position: sticky;
  top: calc(var(--header-height) + 36px);
}

/* 面包屑 */
.doc-breadcrumb {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-bottom: 18px;
  color: var(--text-muted);
  font-size: 13px;
}
.doc-breadcrumb a { color: var(--text-secondary); }
.doc-breadcrumb a:hover { color: var(--accent); text-decoration: none; }
.doc-breadcrumb .crumb-home { display: inline-flex; padding: 2px; border-radius: 4px; }
.doc-breadcrumb .sep { color: var(--border-strong); }
.doc-breadcrumb .crumb { color: var(--text-secondary); }
.doc-breadcrumb [aria-current="page"] { color: var(--text-primary); font-weight: 500; }

/* 页头 */
.doc-header {
  margin-bottom: 36px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--border);
}
.doc-title {
  margin-bottom: 14px;
  color: var(--text-primary);
  font-size: clamp(2rem, 3.6vw, 2.6rem);
  font-weight: 800;
  line-height: 1.18;
  letter-spacing: -0.035em;
}
.doc-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 18px;
  color: var(--text-muted);
  font-size: 13px;
}
.meta-item { display: inline-flex; align-items: center; gap: 6px; }

.doc-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 14px;
}
.doc-tag {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px 10px 3px 8px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-size: 12.5px;
  transition: border-color 0.15s ease, color 0.15s ease, background 0.15s ease;
}
.doc-tag svg { color: var(--text-muted); }
.doc-tag:hover { border-color: var(--accent); background: var(--accent-soft); color: var(--accent); text-decoration: none; }
.doc-tag:hover svg { color: var(--accent); }

/* 操作条 */
.doc-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 20px;
}
.act {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--border);
  border-radius: 9px;
  background: var(--bg-surface);
  color: var(--text-secondary);
  font: inherit;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease, transform 0.15s ease;
}
.act:hover { border-color: var(--border-strong); color: var(--text-primary); text-decoration: none; }
.act:active { transform: scale(0.97); }
.act:disabled { opacity: 0.55; cursor: default; }
.act-primary {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--accent-contrast);
  font-weight: 600;
}
.act-primary:hover { border-color: var(--accent-hover); background: var(--accent-hover); color: var(--accent-contrast); }
.act.on { border-color: var(--accent-soft-strong); background: var(--accent-soft); color: var(--accent); }
.act-quiet { border-color: transparent; background: transparent; }
.act-quiet:hover { border-color: transparent; background: var(--bg-hover); }
.act-sep { width: 1px; height: 18px; margin: 0 4px; background: var(--border); }

/* 加载骨架 */
.loading-state { display: flex; flex-direction: column; gap: 14px; padding-top: 4px; }
.sk {
  height: 14px;
  border-radius: 7px;
  background: linear-gradient(100deg, var(--bg-subtle) 30%, var(--bg-hover) 50%, var(--bg-subtle) 70%);
  background-size: 200% 100%;
  animation: shimmer 1.4s linear infinite;
}
.sk-title { width: 58%; height: 38px; border-radius: 10px; }
.sk-meta { width: 36%; height: 12px; margin-bottom: 26px; }

/* 空状态 / 出错 */
.doc-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 64px 20px;
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius-lg);
  text-align: center;
}
.doc-state.compact { padding: 44px 20px; }
.doc-state-icon {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  margin-bottom: 16px;
  border: 1px solid var(--accent-soft-strong);
  border-radius: 16px;
  background: var(--accent-soft);
  color: var(--accent);
}
.doc-state h1,
.doc-state h2 { margin-bottom: 8px; color: var(--text-primary); font-size: 1.3rem; font-weight: 700; }
.doc-state p { max-width: 420px; margin-bottom: 20px; color: var(--text-secondary); font-size: 14.5px; }
.doc-state code { padding: 1px 6px; border-radius: 6px; background: var(--bg-hover); font-family: var(--font-mono); font-size: 0.9em; }
.doc-state-actions { display: flex; gap: 8px; }

/* 上一篇/下一篇 */
.doc-pager {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 28px;
}
.pager-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 16px 18px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-surface);
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.25s var(--ease-out);
}
.pager-card:hover {
  border-color: var(--accent);
  box-shadow: var(--shadow-md);
  text-decoration: none;
  transform: translateY(-2px);
}
.pager-card.align-right { align-items: flex-end; text-align: right; }
.pager-dir { display: inline-flex; align-items: center; gap: 4px; color: var(--text-muted); font-size: 12.5px; }
.pager-title { color: var(--text-primary); font-size: 15px; font-weight: 650; line-height: 1.4; }
.pager-card:hover .pager-title { color: var(--accent); }
.pager-cat { color: var(--text-muted); font-size: 12px; }

/* 评论、贡献者卡片跟正文同宽 */
.doc-article :deep(.page-contributors),
.doc-article :deep(.page-comments) { max-width: none; border-radius: var(--radius-lg); }

/* ---- 移动端吸顶工具条 ---- */
.doc-mobilebar {
  position: sticky;
  top: var(--header-height);
  z-index: 900;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 46px;
  padding: 0 8px;
  border-bottom: 1px solid var(--border);
  background: var(--glass-bg);
  backdrop-filter: saturate(180%) blur(18px);
  -webkit-backdrop-filter: saturate(180%) blur(18px);
}
.mb-btn {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 10px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
}
.mb-btn:active,
.mb-btn:hover { background: var(--bg-hover); color: var(--text-primary); }
.mb-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--text-primary);
  font-size: 13.5px;
  font-weight: 600;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
  opacity: 0;
  transform: translateY(4px);
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.mb-title.shown { opacity: 1; transform: none; }

@media (max-width: 1280px) {
  .doc-page { grid-template-columns: 260px minmax(0, 1fr); }
  .doc-grid { gap: 48px; }
}

@media (max-width: 1023px) {
  .doc-page { display: block; }
  .doc-grid { padding: 24px 20px 56px; }
  .reading-progress { top: calc(var(--header-height) + 46px); }
  /* 标题跳转时别被吸顶工具条挡住 */
  .doc-article :deep(:is(h1, h2, h3, h4)) { scroll-margin-top: 54px; }
}

@media (max-width: 640px) {
  .doc-grid { padding: 20px 16px 48px; }
  .doc-title { font-size: 1.8rem; }
  .doc-header { margin-bottom: 28px; padding-bottom: 20px; }
  .doc-actions { gap: 6px; }
  .act-sep { display: none; }
  .act { height: 34px; }
  .doc-pager { grid-template-columns: 1fr; }
  .doc-tag { padding: 4px 11px 4px 9px; }
}
</style>

<style>
/* 抽屉里的侧栏：去掉默认内边距，让筛选框和列表自己控制 */
.doc-nav-drawer .el-drawer__body { padding: 0; }
.doc-nav-drawer .el-drawer__header { padding: 14px 18px; }
.doc-toc-drawer .el-drawer__body { padding: 8px 20px calc(20px + env(safe-area-inset-bottom)); }
.doc-toc-drawer.btt { max-height: 75dvh; }
</style>
