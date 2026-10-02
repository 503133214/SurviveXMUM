<template>
  <div class="home">
    <!-- ===== Hero：纯排版居中，无背景装饰 ===== -->
    <section class="hero">
      <h1 class="hero-title">厦大马校生存指南</h1>
      <p class="hero-sub">厦门大学马来西亚分校学生共同维护的学习与生活手册</p>

      <!-- 原来大搜索框的位置留给以后的站内问答助手；搜索入口仍在顶栏和 ⌘K -->
      <AgentSlot class="hero-agent" />

      <div class="hero-actions">
        <router-link class="btn btn-primary" :to="`/docs/${HOME_PATH}`">开始阅读</router-link>
        <router-link class="btn btn-alt" to="/docs/贡献指南">如何贡献</router-link>
      </div>
    </section>

    <!-- ===== 内容导航：每个篇章一张卡、一行一篇。篇目多的先露阅读最多的三篇，
         其余收在「展开」按钮下面；篇章名只是标题，不链到「第一篇」 ===== -->
    <section class="block">
      <div class="block-head">
        <h2>内容导航</h2>
        <router-link class="block-link" :to="`/docs/${HOME_PATH}`">全部文档 →</router-link>
      </div>
      <p class="block-desc">篇章内文档较多时，先列出阅读最多的三篇，其余点「展开」查看。</p>

      <div v-if="!cats.length" class="cat-skeleton" aria-busy="true"></div>
      <div v-else class="cat-grid">
        <div v-for="row in catRows" :key="row.cat.slug" class="cat-card">
          <div class="cat-head">
            <!-- 篇数写进标题里：读屏按标题跳转时听到的是「生活篇 13 篇」 -->
            <h3 class="cat-name">
              <span class="cat-icon" aria-hidden="true">
                <WikiIcon kind="category" :icon="row.cat.icon" :category="row.cat.slug" :title="row.cat.label" :size="18" />
              </span>
              <span class="cat-label">{{ row.cat.label }}</span>
              <span class="cat-count">{{ row.total }} 篇</span>
            </h3>
            <p v-if="row.cat.description" class="cat-desc">{{ row.cat.description }}</p>
          </div>

          <ul class="cat-pages" role="list">
            <li v-for="p in row.featured" :key="p.path">
              <router-link class="cat-row" :to="`/docs/${p.path}`"><span class="cat-title">{{ p.title }}</span></router-link>
            </li>
          </ul>

          <!-- 按钮在前、收起的列表在后：展开后焦点留在按钮上，往下 Tab 正好进入新出现的行；
               按钮自己不挪位置，收起时也不用把页面滚回来 -->
          <template v-if="row.rest.length">
            <button
              type="button"
              class="cat-more"
              :aria-expanded="String(isOpen(row))"
              :aria-controls="row.restId"
              @click="toggleCat(row)"
            >
              <span>{{ isOpen(row) ? "收起" : "展开" }}其余 {{ row.rest.length }} 篇<span class="sr-only">（{{ row.cat.label }}）</span></span>
              <ChevronUp v-if="isOpen(row)" :size="16" aria-hidden="true" />
              <ChevronDown v-else :size="16" aria-hidden="true" />
            </button>
            <ul v-show="isOpen(row)" :id="row.restId" class="cat-pages cat-rest" role="list">
              <li v-for="p in row.rest" :key="p.path">
                <router-link class="cat-row" :to="`/docs/${p.path}`"><span class="cat-title">{{ p.title }}</span></router-link>
              </li>
            </ul>
          </template>
        </div>
      </div>
    </section>

    <!-- ===== 热门 + 最近更新 ===== -->
    <section v-if="popular.length || recentChanges.length" class="block duo">
      <div v-if="popular.length" class="panel">
        <header class="panel-head">
          <h2>热门文档</h2>
        </header>
        <ol class="rank">
          <li v-for="p in popular" :key="p.path">
            <router-link class="rank-row" :to="`/docs/${p.path}`">
              <span class="rank-title">{{ p.title }}</span>
              <span class="rank-cat">{{ p.category }}</span>
            </router-link>
          </li>
        </ol>
      </div>

      <div v-if="recentChanges.length" class="panel">
        <header class="panel-head">
          <h2>最近更新</h2>
          <router-link to="/changes">全部动态 →</router-link>
        </header>
        <ol class="feed">
          <li v-for="c in recentChanges" :key="c.id">
            <router-link class="feed-row" :to="`/docs/${c.path}`">
              <span class="feed-kind" :class="c.kind">{{ c.kind === 'created' ? '新建' : '更新' }}</span>
              <span class="feed-main">
                <span class="feed-title">{{ c.title }}</span>
                <span class="feed-meta">{{ c.authorName }} · {{ relativeTime(c.publishedAt) }}</span>
              </span>
            </router-link>
          </li>
        </ol>
      </div>
    </section>

    <!-- ===== 结尾一句话：手册式的说明，不再做落地页式的号召横幅 ===== -->
    <div class="block">
      <p class="home-note">
        内容有误或想补充经验？任何页面都可以直接编辑，提交后由管理员审核。<router-link to="/docs/贡献指南">贡献指南</router-link><span class="sep" aria-hidden="true"> · </span><router-link to="/contributors">贡献榜</router-link>
      </p>
    </div>

    <SiteFooter />
  </div>
</template>

<script>
import { ChevronDown, ChevronUp } from "lucide-vue-next";
import WikiIcon from "@/components/WikiIcon.vue";
import AgentSlot from "@/components/AgentSlot.vue";
import { pages, categories, HOME_PATH } from "@/wiki";
import SiteFooter from "@/components/SiteFooter.vue";
import { getSiteChanges } from "@/net/index.js";
import { relativeTime } from "@/utils/relativeTime.js";
import { splitFeatured } from "@/utils/navGroups.js";

function pagesUnder(node) {
  const out = [];
  for (const child of node.children || []) {
    if (child.type === "page") out.push(child);
    else out.push(...pagesUnder(child));
  }
  return out;
}

export default {
  name: "HomePage",
  components: { AgentSlot, ChevronDown, ChevronUp, SiteFooter, WikiIcon },
  data() {
    // recentChanges 取站点动态：只含有人实际发布的内容，并能显示是谁改的。
    // openCats 记已展开的篇章 slug：按 slug 而不是下标记，清单从缓存换成最新版时状态不丢。
    // 不写进 sessionStorage——main.js 会有意清掉它，回到首页一律是收起状态。
    return { pages, HOME_PATH, recentChanges: [], openCats: [] };
  },
  mounted() {
    // 首页的次要模块：加载失败就不显示，不弹错误提示
    getSiteChanges({ page: 1, size: 6 }, (data) => {
      this.recentChanges = (data && data.items) || [];
    }, () => {});
  },
  computed: {
    cats() {
      return categories();
    },
    catRows() {
      return this.cats.map((cat, i) => {
        const list = pagesUnder(cat);
        // 先露哪几篇由纯函数决定（utils/navGroups.js，有单测）；阅读量直接读树节点上的 viewCount
        const { featured, rest } = splitFeatured(list);
        return { cat, total: list.length, featured, rest, restId: `nav-rest-${i}` };
      });
    },
    popular() {
      return [...this.pages]
        .filter((p) => p.path !== HOME_PATH && (p.viewCount || 0) > 0)
        .sort((a, b) => (b.viewCount || 0) - (a.viewCount || 0))
        .slice(0, 6);
    },
  },
  methods: {
    relativeTime,
    isOpen(row) {
      return this.openCats.includes(row.cat.slug);
    },
    // 焦点留在按钮上（原生行为，不用写代码）；几个篇章可以同时展开
    toggleCat(row) {
      const slug = row.cat.slug;
      this.openCats = this.isOpen(row)
        ? this.openCats.filter((s) => s !== slug)
        : [...this.openCats, slug];
    },
  },
};
</script>

<style scoped>
.home {
  background: var(--bg-page);
  color: var(--text-primary);
}

/* ================= Hero ================= */
.hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  max-width: 860px;
  margin: 0 auto;
  padding: clamp(32px, 6vw, 56px) 20px 0;
  text-align: center;
}

/* 中文不加负字距；标题只比正文大一档，像手册封面而不是营销页 */
.hero-title {
  margin-bottom: 8px;
  font-size: clamp(28px, 5vw, 36px);
  font-weight: 700;
  letter-spacing: 0;
  line-height: 1.3;
}

/* balance 让窄屏换行时两行长度接近，不会只剩「手册」两个字掉到第二行 */
.hero-sub {
  margin: 0 0 24px;
  color: var(--text-secondary);
  font-size: 15px;
  line-height: 1.6;
  text-wrap: balance;
}

/* .hero 是居中的 flex 列，不给宽度的话预留框会缩成文字宽度；
   scoped 属性会落到子组件根元素上，所以这里能直接选中 AgentSlot */
.hero-agent {
  width: min(600px, 100%);
  margin-bottom: 28px;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
}

/* ===== Buttons ===== */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 36px;
  padding: 0 16px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--dur) ease, color var(--dur) ease, border-color var(--dur) ease;
}
.btn:hover { text-decoration: none; }
.btn-primary { background: var(--accent); color: var(--accent-contrast); }
.btn-primary:hover { background: var(--accent-hover); color: var(--accent-contrast); }
.btn-alt {
  border-color: var(--border-strong);
  background: var(--bg-surface);
  color: var(--text-primary);
}
.btn-alt:hover { border-color: var(--text-muted); color: var(--text-primary); }

/* ================= Section blocks ================= */
.block {
  max-width: 960px;
  margin: 0 auto;
  padding: 44px 20px 8px;
}
.block-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
}
/* 与其他页面的分节标题同档：18px / 600 */
.block-head h2 {
  font-size: var(--fs-h3);
  font-weight: 600;
  letter-spacing: 0;
  line-height: 1.3;
}
.block-link { flex-shrink: 0; color: var(--brand-blue); font-size: 14px; font-weight: 500; }
.block-desc {
  margin: 6px 0 18px;
  color: var(--text-secondary);
  font-size: 14px;
}

/* ================= 内容导航 ================= */
/* 和下面的「热门文档」「最近更新」同一套卡片：1px 边、无圆角、无阴影、44px 的行。
   align-items: start：某张卡展开后只有它自己变长，不把同一行的邻居撑出空白 */
.cat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  align-items: start;
}
.cat-card {
  min-width: 0;
  border: 1px solid var(--border);
  background: var(--bg-surface);
}

.cat-head {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
}
/* 篇章名与 .panel-head h2 同档（15px / 600），标题层级靠字号、字重和颜色区分 */
.cat-name {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0;
  line-height: var(--lh-tight);
}
/* 图标只出现在篇章标题上，颜色压低一档，不和篇章名抢 */
.cat-icon {
  display: inline-flex;
  flex-shrink: 0;
  color: var(--text-secondary);
}
.cat-label { min-width: 0; }
.cat-count {
  flex-shrink: 0;
  margin-left: auto;
  color: var(--text-muted);
  font-size: var(--fs-sm);
  font-weight: 400;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
/* 简介最多两行，并且固定占两行高：同一排三张卡的文章列表从同一条线开始 */
.cat-desc {
  display: -webkit-box;
  min-height: calc(2em * 1.55);
  margin-top: 4px;
  overflow: hidden;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  line-height: 1.55;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
}

.cat-pages { list-style: none; }
.cat-pages li + li,
.cat-rest { border-top: 1px solid var(--border); }

/* 一行一篇。标题用正文墨色而不是链接蓝：蓝色只留给「展开」和「全部文档 →」，
   不再是一整片蓝字；能点靠整行的形状、分隔线和悬停底色看出来 */
.cat-row {
  display: flex;
  align-items: center;
  min-height: 44px;
  padding: 10px 16px;
  color: var(--text-body);
  font-size: var(--fs-ui);
  line-height: 1.5;
  transition: background-color var(--dur) ease, color var(--dur) ease;
}
.cat-row:hover { background: var(--bg-subtle); color: var(--text-primary); text-decoration: none; }
/* 长标题最多折两行，不用单行省略号：手机上没有悬停提示，省掉的字就看不到了 */
.cat-title {
  display: -webkit-box;
  overflow: hidden;
  overflow-wrap: anywhere;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
}

/* 「展开 / 收起」占满一整行，整条都是点按区域；箭头换图标，不做旋转动画 */
.cat-more {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  min-height: 44px;
  padding: 10px 16px;
  border: 0;
  border-top: 1px solid var(--border);
  background: transparent;
  color: var(--brand-blue);
  font: inherit;
  font-size: var(--fs-sm);
  font-weight: 500;
  line-height: 1.5;
  text-align: left;
  cursor: pointer;
  transition: background-color var(--dur) ease, color var(--dur) ease;
}
.cat-more:hover { background: var(--bg-subtle); color: var(--accent-hover); }
.cat-more svg { flex-shrink: 0; }

/* 行与行紧挨着，焦点环向内收，免得被卡片边框裁掉或压到相邻行 */
.cat-row:focus-visible,
.cat-more:focus-visible { outline-offset: -2px; }

/* 目录还没到时只放一块静态底色占位，不做闪光动画 */
.cat-skeleton {
  height: 220px;
  border: 1px solid var(--border);
  background: var(--bg-subtle);
}

/* ================= 热门 + 最近更新 ================= */
.duo {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.panel {
  border: 1px solid var(--border);
  background: var(--bg-surface);
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
}
.panel-head h2 {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0;
}
.panel-head > span,
.panel-head > a { color: var(--text-muted); font-size: 13px; }
.panel-head > a:hover { color: var(--accent); text-decoration: none; }

.rank, .feed { list-style: none; }
.rank li + li,
.feed li + li { border-top: 1px solid var(--border); }

.rank-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  /* 列表行统一 10px 16px、至少 44px 高，手指点得准 */
  min-height: 44px;
  padding: 10px 16px;
  color: var(--text-primary);
}
.rank-row:hover { background: var(--bg-subtle); color: var(--text-primary); text-decoration: none; }
/* 行与行紧挨着，焦点环向内收，免得压到相邻行 */
.rank-row:focus-visible,
.feed-row:focus-visible { outline-offset: -2px; }
.rank-title {
  overflow: hidden;
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rank-cat { flex-shrink: 0; color: var(--text-muted); font-size: 13px; }

.feed-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 16px;
  color: var(--text-primary);
}
.feed-row:hover { background: var(--bg-subtle); color: var(--text-primary); text-decoration: none; }
.feed-kind {
  flex-shrink: 0;
  margin-top: 2px;
  padding: 0 6px;
  border-radius: var(--radius-xs);
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
}
.feed-kind.created { background: var(--success-soft); color: var(--success); }
.feed-main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 1px;
}
.feed-title {
  overflow: hidden;
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.feed-meta { color: var(--text-muted); font-size: 13px; }

/* ================= 结尾说明 ================= */
/* 不加上边线：紧接着就是页脚的分隔线，两条线夹一句话会像一条横幅 */
.home-note {
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.7;
}
.home-note a { color: var(--brand-blue); font-weight: 500; }
.home-note a:hover { color: var(--accent-hover); }
.sep { color: var(--text-muted); }

/* ================= Responsive ================= */
@media (max-width: 860px) {
  .duo { grid-template-columns: minmax(0, 1fr); }
  .cat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@media (max-width: 720px) {
  .rank-cat { display: none; }
}

@media (max-width: 640px) {
  .hero-agent { margin-bottom: 24px; }
  .hero-actions { width: 100%; }
  /* 手机上两个按钮各占一半宽，按设计规范加高到 40px 方便点按 */
  .hero-actions .btn { flex: 1; height: 40px; }
  .block { padding: 36px 16px 8px; }
  /* 手机单列：卡片之间留 12px；简介不必再占满两行去和邻居对齐 */
  .cat-grid { grid-template-columns: minmax(0, 1fr); gap: 12px; }
  .cat-desc { min-height: 0; }
}
</style>
