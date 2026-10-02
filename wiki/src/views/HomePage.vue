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

    <!-- ===== 内容导航：篇章 → 文章链接的两列表格（参造 ac-wiki） ===== -->
    <section class="block">
      <div class="block-head">
        <h2>内容导航</h2>
        <router-link class="block-link" :to="`/docs/${HOME_PATH}`">全部文档 →</router-link>
      </div>
      <p class="block-desc">按篇章列出站内全部文档；点篇章名会打开该篇章的第一篇。</p>

      <div v-if="!cats.length" class="nav-table skeleton" aria-busy="true"></div>
      <table v-else class="nav-table">
        <tbody>
          <tr v-for="row in catRows" :key="row.cat.slug">
            <th scope="row">
              <router-link class="cat-link" :to="row.to">
                <WikiIcon kind="category" :icon="row.cat.icon" :category="row.cat.slug" :title="row.cat.label" :size="18" />
                <span>{{ row.cat.label }}</span>
              </router-link>
              <p v-if="row.cat.description" class="cat-desc">{{ row.cat.description }}</p>
            </th>
            <td>
              <span v-for="(p, i) in row.links" :key="p.path">
                <router-link :to="`/docs/${p.path}`">{{ p.title }}</router-link><span v-if="i < row.links.length - 1" class="sep" aria-hidden="true"> · </span>
              </span>
            </td>
          </tr>
        </tbody>
      </table>
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
import WikiIcon from "@/components/WikiIcon.vue";
import AgentSlot from "@/components/AgentSlot.vue";
import { pages, categories, HOME_PATH } from "@/wiki";
import SiteFooter from "@/components/SiteFooter.vue";
import { getSiteChanges } from "@/net/index.js";
import { relativeTime } from "@/utils/relativeTime.js";

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
  components: { AgentSlot, SiteFooter, WikiIcon },
  data() {
    // recentChanges 取站点动态：只含有人实际发布的内容，并能显示是谁改的。
    return { pages, HOME_PATH, recentChanges: [] };
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
      return this.cats.map((cat) => {
        const list = pagesUnder(cat);
        return {
          cat,
          to: list[0] ? `/docs/${list[0].path}` : `/docs/${HOME_PATH}`,
          links: list,
        };
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

/* ================= 内容导航表 ================= */
.nav-table {
  width: 100%;
  border: 1px solid var(--border);
  border-collapse: collapse;
}
.nav-table th,
.nav-table td {
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
  vertical-align: top;
  text-align: left;
}
.nav-table tr:last-child th,
.nav-table tr:last-child td { border-bottom: 0; }
.nav-table th { width: 220px; background: var(--bg-subtle); }

.cat-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
}
.cat-link:hover { color: var(--accent); text-decoration: none; }
.cat-desc {
  margin-top: 6px;
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 400;
  line-height: 1.55;
}
.nav-table td a {
  color: var(--brand-blue);
  font-size: 14px;
  line-height: 2.1;
}
.nav-table td a:hover { color: var(--accent-hover); }
.sep { color: var(--text-muted); }

/* 目录还没到时只放一块静态底色占位，不做闪光动画 */
.nav-table.skeleton {
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

/* ================= Responsive ================= */
@media (max-width: 860px) {
  .duo { grid-template-columns: minmax(0, 1fr); }
}

@media (max-width: 720px) {
  .nav-table th,
  .nav-table td { display: block; width: auto; border-bottom: 0; }
  .nav-table tr { display: block; border-bottom: 1px solid var(--border); }
  .nav-table tr:last-child { border-bottom: 0; }
  .nav-table th { padding-bottom: 4px; }
  .nav-table td { padding-top: 2px; }
  .rank-cat { display: none; }
}

@media (max-width: 640px) {
  .hero-agent { margin-bottom: 24px; }
  .hero-actions { width: 100%; }
  /* 手机上两个按钮各占一半宽，按设计规范加高到 40px 方便点按 */
  .hero-actions .btn { flex: 1; height: 40px; }
  .block { padding: 36px 16px 8px; }
}
</style>
