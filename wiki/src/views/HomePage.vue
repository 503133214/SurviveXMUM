<template>
  <div class="home">
    <!-- ===== Hero：纯排版居中，无背景装饰 ===== -->
    <section class="hero">
      <router-link v-if="latest" to="/changes" class="hero-badge">
        <span class="hb-label">最近更新</span>
        <span class="hb-title">{{ latest.title }}</span>
        <span class="hb-time">{{ relativeTime(latest.publishedAt) }}</span>
        <span aria-hidden="true">→</span>
      </router-link>
      <p v-else class="hero-badge">厦门大学马来西亚分校 · 学生共建知识库</p>

      <h1 class="hero-title">厦大马校生存指南</h1>

      <div class="hero-actions">
        <router-link class="btn btn-primary" :to="`/docs/${HOME_PATH}`">开始阅读</router-link>
        <router-link class="btn btn-alt" to="/docs/贡献指南">如何贡献</router-link>
      </div>

      <p v-if="topTags.length" class="hero-tags">
        <router-link
          v-for="t in topTags"
          :key="t.tag"
          :to="`/tags/${encodeURIComponent(t.tag)}`"
        >#{{ t.tag }}</router-link>
      </p>
    </section>

    <!-- ===== 内容导航：篇章 → 文章链接的两列表格（参造 ac-wiki） ===== -->
    <section class="block">
      <div class="block-head">
        <h2>📚 内容导航</h2>
        <router-link class="block-link" :to="`/docs/${HOME_PATH}`">全部文档 →</router-link>
      </div>
      <p class="block-desc">从出发前的准备到走进社会，每个阶段都有人替你踩过坑。</p>

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
          <h2>🔥 热门文档</h2>
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
          <h2>🕘 最近更新</h2>
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

    <!-- ===== 结尾号召：扁平 callout ===== -->
    <section class="block">
      <div class="cta">
        <div class="cta-copy">
          <h2>发现了错误，或想分享你的经验？</h2>
          <p>用校园邮箱注册后，任何页面都能直接编辑。管理员审核通过即上线，你的名字会出现在贡献者名单里。</p>
        </div>
        <div class="cta-actions">
          <router-link class="btn btn-primary" to="/docs/贡献指南">查看贡献指南</router-link>
          <router-link class="btn btn-alt" to="/contributors">贡献榜</router-link>
        </div>
      </div>
    </section>

    <SiteFooter />
  </div>
</template>

<script>
import WikiIcon from "@/components/WikiIcon.vue";
import { pages, categories, allTags, HOME_PATH } from "@/wiki";
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
  components: { SiteFooter, WikiIcon },
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
    topTags() {
      return allTags().slice(0, 6);
    },
    latest() {
      return this.recentChanges[0] || null;
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
  padding: clamp(48px, 8vw, 84px) 20px 0;
  text-align: center;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
  margin-bottom: 26px;
  padding: 4px 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-subtle);
  color: var(--text-secondary);
  font-size: 13px;
}
a.hero-badge:hover { border-color: var(--accent); color: var(--text-secondary); text-decoration: none; }
.hb-label { flex-shrink: 0; color: var(--accent); font-weight: 600; }
.hb-title {
  overflow: hidden;
  color: var(--text-primary);
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hb-time { flex-shrink: 0; color: var(--text-muted); }

.hero-title {
  margin-bottom: 30px;
  font-size: clamp(2.1rem, 6vw, 3.6rem);
  font-weight: 750;
  letter-spacing: -0.02em;
  line-height: 1.16;
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
  height: 42px;
  padding: 0 22px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
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

.hero-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 4px 10px;
  margin-top: 10px;
  font-size: 13px;
}

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
.block-head h2 {
  font-size: 1.45rem;
  font-weight: 700;
  letter-spacing: -0.01em;
}
.block-link { flex-shrink: 0; color: var(--brand-blue); font-size: 14px; font-weight: 500; }
.block-desc {
  margin: 6px 0 18px;
  color: var(--text-secondary);
  font-size: 14.5px;
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
  font-size: 14.5px;
  font-weight: 650;
}
.cat-link:hover { color: var(--accent); text-decoration: none; }
.cat-desc {
  margin-top: 6px;
  color: var(--text-muted);
  font-size: 12.5px;
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

.nav-table.skeleton {
  height: 220px;
  border: 1px solid var(--border);
  background: linear-gradient(100deg, var(--bg-subtle) 30%, var(--bg-hover) 50%, var(--bg-subtle) 70%);
  background-size: 200% 100%;
  animation: shimmer 1.4s linear infinite;
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
  font-size: 1rem;
  font-weight: 700;
  letter-spacing: -0.01em;
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
  padding: 9px 16px;
  color: var(--text-primary);
}
.rank-row:hover { background: var(--bg-subtle); color: var(--text-primary); text-decoration: none; }
.rank-title {
  overflow: hidden;
  font-size: 14px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rank-cat { flex-shrink: 0; color: var(--text-muted); font-size: 12.5px; }

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
  border-radius: 4px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 11.5px;
  font-weight: 600;
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
.feed-meta { color: var(--text-muted); font-size: 12.5px; }

/* ================= CTA ================= */
.cta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  padding: 28px 30px;
  border: 1px solid var(--border);
  border-left: 3px solid var(--accent);
  background: var(--bg-subtle);
}
.cta-copy h2 {
  margin-bottom: 8px;
  font-size: 1.3rem;
  font-weight: 700;
  letter-spacing: -0.01em;
}
.cta-copy p {
  color: var(--text-secondary);
  font-size: 14.5px;
  line-height: 1.7;
}
.cta-actions {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 10px;
}

/* ================= Responsive ================= */
@media (max-width: 860px) {
  .duo { grid-template-columns: minmax(0, 1fr); }
  .cta { flex-direction: column; align-items: flex-start; }
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
  .hero { padding-top: 36px; }
  .hero-badge .hb-time { display: none; }
  .hero-actions { width: 100%; }
  .hero-actions .btn { flex: 1; }
  .block { padding: 36px 16px 8px; }
  .cta { padding: 22px 18px; }
  .cta-actions { width: 100%; }
  .cta-actions .btn { flex: 1; }
}
</style>
