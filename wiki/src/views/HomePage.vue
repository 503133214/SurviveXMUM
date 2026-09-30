<template>
  <div class="home">
    <!-- ===== Hero ===== -->
    <section class="hero">
      <div class="hero-bg" aria-hidden="true">
        <span class="aurora a1"></span>
        <span class="aurora a2"></span>
        <span class="aurora a3"></span>
        <span class="hero-grid"></span>
      </div>

      <div class="hero-inner">
        <router-link v-if="latest" to="/changes" class="hero-badge">
          <span class="pulse" aria-hidden="true"></span>
          <span class="hero-badge-label">最近更新</span>
          <span class="hero-badge-title">{{ latest.title }}</span>
          <span class="hero-badge-time">{{ relativeTime(latest.publishedAt) }}</span>
          <span aria-hidden="true">→</span>
        </router-link>
        <p v-else class="hero-badge static">
          <span class="pulse" aria-hidden="true"></span>
          厦门大学马来西亚分校 · 学生共建知识库
        </p>

        <h1 class="hero-title">
          少走弯路的<br />
          <span class="gradient-text">厦马生存指南</span>
        </h1>
        <p class="hero-sub">
          入学、学习、生活、升学与求职，由学长学姐共同维护。发现过时的内容？在网站上直接改。
        </p>

        <button type="button" class="hero-search" @click="openPalette()">
          <Search class="hero-search-icon" :size="19" :stroke-width="2" aria-hidden="true" />
          <span class="hero-search-text">搜索签证、选课、GPA、宿舍…</span>
          <span class="ui-kbd hero-search-kbd">{{ shortcut }}</span>
        </button>

        <div v-if="topTags.length" class="hero-chips">
          <span class="hero-chips-label">热门标签</span>
          <router-link
            v-for="t in topTags"
            :key="t.tag"
            class="hero-chip"
            :to="`/tags/${encodeURIComponent(t.tag)}`"
          >#{{ t.tag }}</router-link>
        </div>

        <div class="hero-actions">
          <router-link class="btn btn-primary" :to="`/docs/${HOME_PATH}`">
            开始阅读
            <ArrowRight :size="16" :stroke-width="2.25" aria-hidden="true" />
          </router-link>
          <router-link class="btn btn-secondary" to="/docs/贡献指南">如何贡献</router-link>
        </div>
      </div>

      <dl class="hero-stats">
        <div class="stat">
          <dt>篇文档</dt>
          <dd><AnimatedNumber :to="docCount" /></dd>
        </div>
        <div class="stat">
          <dt>个篇章</dt>
          <dd><AnimatedNumber :to="cats.length" /></dd>
        </div>
        <div class="stat">
          <dt>次阅读</dt>
          <dd><AnimatedNumber :to="totalViews" :duration="1600" /></dd>
        </div>
        <div class="stat">
          <dt>个标签</dt>
          <dd><AnimatedNumber :to="tagCount" /></dd>
        </div>
      </dl>
    </section>

    <!-- ===== 篇章 Bento ===== -->
    <section class="block">
      <header class="block-head">
        <div>
          <span class="ui-eyebrow">Explore</span>
          <h2 v-reveal>按篇章浏览</h2>
          <p v-reveal>从出发前的准备到走进社会，每个阶段都有人替你踩过坑。</p>
        </div>
        <router-link class="block-link" :to="`/docs/${HOME_PATH}`">
          全部文档 <ArrowRight :size="15" :stroke-width="2" aria-hidden="true" />
        </router-link>
      </header>

      <div v-if="!cats.length" class="bento" aria-busy="true">
        <div v-for="n in 6" :key="n" class="bento-card skeleton" :class="n <= 2 ? 'is-lg' : 'is-md'"></div>
      </div>

      <div v-else class="bento">
        <article
          v-for="(card, i) in cards"
          :key="card.cat.slug"
          class="bento-card"
          :class="`is-${card.size}`"
          :style="{ '--c': card.color }"
          v-reveal="{ delay: Math.min(i, 5) * 50 }"
          @pointermove="spot"
        >
          <div class="bc-head">
            <span class="bc-icon" aria-hidden="true">
              <WikiIcon kind="category" :icon="card.cat.icon" :category="card.cat.slug" :title="card.cat.label" :size="22" />
            </span>
            <span class="bc-count">{{ card.count }} 篇</span>
          </div>
          <h3 class="bc-title">
            <router-link class="bc-link" :to="card.to">{{ card.cat.label }}</router-link>
          </h3>
          <p class="bc-desc">{{ card.cat.description || hint(card.cat) }}</p>
          <ul v-if="card.preview.length" class="bc-pages">
            <li v-for="p in card.preview" :key="p.path">
              <router-link :to="`/docs/${p.path}`">
                <WikiIcon class="bc-page-icon" :icon="p.icon" :title="p.title" :category="card.cat.slug" :size="15" />
                <span class="bc-page-title">{{ p.title }}</span>
              </router-link>
            </li>
          </ul>
          <span class="bc-more" aria-hidden="true">
            进入篇章 <ArrowRight :size="14" :stroke-width="2.25" />
          </span>
        </article>
      </div>
    </section>

    <!-- ===== 热门 + 最近更新 ===== -->
    <section v-if="popular.length || recentChanges.length" class="block duo">
      <div v-if="popular.length" class="panel" v-reveal>
        <header class="panel-head">
          <h2><Flame :size="18" :stroke-width="2" aria-hidden="true" />热门文档</h2>
          <span>按累计阅读</span>
        </header>
        <ol class="rank">
          <li v-for="(p, i) in popular" :key="p.path">
            <router-link class="rank-row" :to="`/docs/${p.path}`">
              <span class="rank-no" :class="{ top: i < 3 }">{{ i + 1 }}</span>
              <span class="rank-main">
                <span class="rank-title">{{ p.title }}</span>
                <span class="rank-bar" aria-hidden="true">
                  <i :style="{ width: `${Math.max(6, (p.viewCount / popular[0].viewCount) * 100)}%` }"></i>
                </span>
              </span>
              <span class="rank-cat">{{ p.category }}</span>
              <span class="rank-views">{{ p.viewCount.toLocaleString('zh-CN') }}</span>
            </router-link>
          </li>
        </ol>
      </div>

      <div v-if="recentChanges.length" class="panel" v-reveal="{ delay: 80 }">
        <header class="panel-head">
          <h2><History :size="18" :stroke-width="2" aria-hidden="true" />最近更新</h2>
          <router-link to="/changes">全部动态 →</router-link>
        </header>
        <ol class="timeline">
          <li v-for="c in recentChanges" :key="c.id">
            <router-link class="tl-row" :to="`/docs/${c.path}`">
              <span class="tl-dot" :class="c.kind" aria-hidden="true"></span>
              <span class="tl-main">
                <span class="tl-title">{{ c.title }}</span>
                <span class="tl-meta">
                  <span class="tl-kind" :class="c.kind">{{ c.kind === 'created' ? '新建' : '更新' }}</span>
                  {{ c.authorName }} · {{ relativeTime(c.publishedAt) }}
                </span>
              </span>
            </router-link>
          </li>
        </ol>
      </div>
    </section>

    <!-- ===== 结尾号召 ===== -->
    <section class="block">
      <div class="cta" v-reveal>
        <div class="cta-glow" aria-hidden="true"></div>
        <div class="cta-copy">
          <span class="cta-eyebrow">由社区共建</span>
          <h2>发现了错误，或想分享你的经验？</h2>
          <p>用校园邮箱注册后，任何页面都能直接编辑。管理员审核通过即上线，你的名字会出现在贡献者名单里。</p>
        </div>
        <div class="cta-actions">
          <router-link class="btn btn-light" to="/docs/贡献指南">查看贡献指南</router-link>
          <router-link class="btn btn-ghost-light" to="/contributors">
            <Trophy :size="16" :stroke-width="2" aria-hidden="true" />贡献榜
          </router-link>
        </div>
      </div>
    </section>

    <SiteFooter />
  </div>
</template>

<script>
import { Search, ArrowRight, Flame, History, Trophy } from "lucide-vue-next";
import WikiIcon from "@/components/WikiIcon.vue";
import { pages, categories, allTags, HOME_PATH } from "@/wiki";
import AnimatedNumber from "@/components/AnimatedNumber.vue";
import SiteFooter from "@/components/SiteFooter.vue";
import { getSiteChanges } from "@/net/index.js";
import { openPalette } from "@/composables/usePalette.js";
import { relativeTime } from "@/utils/relativeTime.js";

// 每个篇章一个色相（RGB 三元组，给 rgba() 用），只点缀在卡片的光晕和图标底上
const CARD_COLORS = [
  "99, 102, 241",
  "14, 165, 233",
  "16, 185, 129",
  "139, 92, 246",
  "244, 63, 94",
  "245, 158, 11",
  "20, 184, 166",
  "217, 70, 239",
  "249, 115, 22",
];

function pagesUnder(node) {
  const out = [];
  for (const child of node.children || []) {
    if (child.type === "page") out.push(child);
    else out.push(...pagesUnder(child));
  }
  return out;
}

// 6 列网格上的尺寸：前两张大卡各占半行，其余三张一行；最后一行不满时
// 拉宽收尾的卡片，保证网格总是方方正正。
function bentoSizes(n) {
  if (n <= 2) return Array(n).fill("lg");
  const sizes = ["lg", "lg"];
  const rest = n - 2;
  for (let i = 0; i < rest; i++) sizes.push("md");
  if (rest % 3 === 1) sizes[n - 1] = "wide";
  if (rest % 3 === 2) {
    sizes[n - 1] = "lg";
    sizes[n - 2] = "lg";
  }
  return sizes;
}

export default {
  name: "HomePage",
  components: { AnimatedNumber, SiteFooter, WikiIcon, Search, ArrowRight, Flame, History, Trophy },
  data() {
    const isMac = typeof navigator !== "undefined" && /Mac|iPhone|iPad/i.test(navigator.platform || navigator.userAgent || "");
    // recentChanges 取站点动态：只含有人实际发布的内容，并能显示是谁改的。
    return { pages, HOME_PATH, recentChanges: [], shortcut: isMac ? "⌘K" : "Ctrl K" };
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
    cards() {
      const sizes = bentoSizes(this.cats.length);
      return this.cats.map((cat, i) => {
        const list = pagesUnder(cat);
        const size = sizes[i];
        const take = size === "md" ? 3 : 4;
        return {
          cat,
          size,
          color: CARD_COLORS[i % CARD_COLORS.length],
          count: list.length,
          to: list[0] ? `/docs/${list[0].path}` : `/docs/${HOME_PATH}`,
          // 预览按阅读量挑，让卡片上露出的是这一篇章里最常被找的几篇
          preview: [...list]
            .sort((a, b) => (this.viewsOf(b) - this.viewsOf(a)))
            .slice(0, take),
        };
      });
    },
    docCount() {
      return this.pages.filter((p) => p.path !== HOME_PATH).length;
    },
    totalViews() {
      return this.pages.reduce((sum, p) => sum + (p.viewCount || 0), 0);
    },
    tagCount() {
      return allTags().length;
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
    openPalette,
    relativeTime,
    viewsOf(page) {
      const live = this.pages.find((p) => p.path === page.path);
      return (live && live.viewCount) || 0;
    },
    hint(cat) {
      const titles = pagesUnder(cat).slice(0, 3).map((c) => c.title).join("、");
      return titles ? `包含 ${titles} 等` : "敬请期待";
    },
    // 光标跟随的柔光（坐标写进 CSS 变量，由 ::before 渲染）
    spot(e) {
      const el = e.currentTarget;
      const r = el.getBoundingClientRect();
      el.style.setProperty("--mx", `${e.clientX - r.left}px`);
      el.style.setProperty("--my", `${e.clientY - r.top}px`);
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
  position: relative;
  isolation: isolate;
  overflow: hidden;
  padding: clamp(56px, 10vw, 112px) 20px 40px;
}
.hero-bg { position: absolute; inset: 0; z-index: -1; pointer-events: none; }

/* 极光：用柔边径向渐变代替 filter: blur，手机上也不掉帧 */
.aurora {
  position: absolute;
  border-radius: 50%;
  opacity: 0.55;
  animation: drift 22s ease-in-out infinite alternate;
}
.a1 {
  top: -28%;
  left: 8%;
  width: 56vw;
  height: 56vw;
  max-width: 760px;
  max-height: 760px;
  background: radial-gradient(closest-side, rgba(51, 69, 206, 0.34), transparent);
}
.a2 {
  top: -12%;
  right: 4%;
  width: 46vw;
  height: 46vw;
  max-width: 640px;
  max-height: 640px;
  background: radial-gradient(closest-side, rgba(124, 92, 255, 0.28), transparent);
  animation-duration: 26s;
  animation-delay: -6s;
}
.a3 {
  top: 26%;
  left: 34%;
  width: 40vw;
  height: 40vw;
  max-width: 560px;
  max-height: 560px;
  background: radial-gradient(closest-side, rgba(34, 184, 230, 0.2), transparent);
  animation-duration: 30s;
  animation-delay: -12s;
}
html.dark .aurora { opacity: 0.7; }

.hero-grid {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(var(--border-strong) 1px, transparent 1.2px);
  background-size: 24px 24px;
  -webkit-mask-image: radial-gradient(ellipse 70% 60% at 50% 30%, #000 20%, transparent 75%);
  mask-image: radial-gradient(ellipse 70% 60% at 50% 30%, #000 20%, transparent 75%);
  opacity: 0.7;
}

@keyframes drift {
  0% { transform: translate3d(0, 0, 0) scale(1); }
  50% { transform: translate3d(4%, 6%, 0) scale(1.08); }
  100% { transform: translate3d(-5%, 2%, 0) scale(0.96); }
}

.hero-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  max-width: 820px;
  margin: 0 auto;
  text-align: center;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
  margin-bottom: 28px;
  padding: 5px 14px 5px 10px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--glass-bg);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  color: var(--text-secondary);
  font-size: 13px;
  box-shadow: var(--shadow-xs);
  animation: rise 0.7s var(--ease-out) both;
  transition: border-color 0.2s ease, color 0.2s ease;
}
a.hero-badge:hover { border-color: var(--accent); color: var(--text-primary); text-decoration: none; }
.hero-badge-label { color: var(--accent); font-weight: 600; }
.hero-badge-title {
  overflow: hidden;
  color: var(--text-primary);
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.hero-badge-time { flex-shrink: 0; color: var(--text-muted); }

.pulse {
  position: relative;
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--success);
}
.pulse::after {
  content: "";
  position: absolute;
  inset: -4px;
  border-radius: 50%;
  background: var(--success);
  opacity: 0.35;
  animation: ping 2s var(--ease-out) infinite;
}
@keyframes ping {
  0% { transform: scale(0.5); opacity: 0.5; }
  100% { transform: scale(1.6); opacity: 0; }
}

.hero-title {
  margin-bottom: 22px;
  font-size: clamp(2.6rem, 7.4vw, 5.4rem);
  font-weight: 800;
  line-height: 1.04;
  letter-spacing: -0.04em;
  animation: rise 0.8s var(--ease-out) 0.06s both;
}
.hero-sub {
  max-width: 580px;
  margin-bottom: 34px;
  color: var(--text-secondary);
  font-size: clamp(1.02rem, 1.8vw, 1.2rem);
  line-height: 1.7;
  animation: rise 0.8s var(--ease-out) 0.12s both;
}

/* 大号搜索入口 */
.hero-search {
  display: flex;
  align-items: center;
  gap: 12px;
  width: min(600px, 100%);
  height: 60px;
  padding: 0 14px 0 20px;
  border: 1px solid var(--border-strong);
  border-radius: 16px;
  background: var(--bg-surface);
  color: var(--text-muted);
  font: inherit;
  font-size: 16px;
  box-shadow: var(--shadow-md), 0 0 0 6px var(--accent-soft);
  cursor: text;
  animation: rise 0.8s var(--ease-out) 0.18s both;
  transition: box-shadow 0.2s ease, border-color 0.2s ease, transform 0.2s var(--ease-out);
}
.hero-search:hover {
  border-color: var(--accent);
  box-shadow: var(--shadow-lg), 0 0 0 6px var(--accent-ring);
  transform: translateY(-1px);
}
.hero-search-icon { flex-shrink: 0; color: var(--accent); }
.hero-search-text { flex: 1; overflow: hidden; text-align: left; text-overflow: ellipsis; white-space: nowrap; }
.hero-search-kbd { height: 24px; padding: 0 7px; font-size: 12px; }

.hero-chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 18px;
  animation: rise 0.8s var(--ease-out) 0.24s both;
}
.hero-chips-label { color: var(--text-muted); font-size: 12.5px; }
.hero-chip {
  padding: 4px 11px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--glass-bg);
  color: var(--text-secondary);
  font-size: 12.5px;
  transition: border-color 0.15s ease, color 0.15s ease, background 0.15s ease;
}
.hero-chip:hover { border-color: var(--accent); background: var(--accent-soft); color: var(--accent); text-decoration: none; }

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 12px;
  margin-top: 32px;
  animation: rise 0.8s var(--ease-out) 0.3s both;
}

/* ===== Buttons ===== */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 44px;
  padding: 0 22px;
  border: 1px solid transparent;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: transform 0.2s var(--ease-out), background 0.15s ease, color 0.15s ease,
    border-color 0.15s ease, box-shadow 0.2s ease;
}
.btn:hover { text-decoration: none; transform: translateY(-1px); }
.btn:active { transform: translateY(0) scale(0.98); }
.btn-primary {
  background: var(--accent);
  color: var(--accent-contrast);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.18) inset, 0 6px 18px -6px var(--accent-ring);
}
.btn-primary:hover { background: var(--accent-hover); color: var(--accent-contrast); }
.btn-secondary {
  border-color: var(--border-strong);
  background: var(--bg-surface);
  color: var(--text-primary);
}
.btn-secondary:hover { border-color: var(--text-muted); color: var(--text-primary); }

/* ===== Stats ===== */
.hero-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  max-width: 820px;
  margin: 64px auto 0;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--glass-bg);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  animation: rise 0.8s var(--ease-out) 0.36s both;
}
.stat {
  display: flex;
  flex-direction: column-reverse;
  gap: 2px;
  padding: 18px 20px;
  text-align: center;
}
.stat + .stat { border-left: 1px solid var(--border); }
.stat dd {
  color: var(--text-primary);
  font-size: clamp(1.4rem, 3vw, 1.9rem);
  font-weight: 750;
  letter-spacing: -0.03em;
  line-height: 1.15;
}
.stat dt { color: var(--text-muted); font-size: 12.5px; }

/* ================= Section blocks ================= */
.block {
  max-width: var(--maxw);
  margin: 0 auto;
  padding: 56px 24px;
}
.block-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 28px;
}
.block-head h2 {
  margin: 8px 0 6px;
  font-size: clamp(1.6rem, 3vw, 2.2rem);
  font-weight: 750;
  letter-spacing: -0.03em;
}
.block-head p { color: var(--text-secondary); font-size: 15.5px; }
.block-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
}
.block-link:hover { color: var(--accent); text-decoration: none; }

/* ================= Bento ================= */
.bento {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 16px;
}
.bento-card {
  --c: 99, 102, 241;
  position: relative;
  isolation: isolate;
  display: flex;
  flex-direction: column;
  min-height: 250px;
  padding: 22px;
  overflow: hidden;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background:
    radial-gradient(120% 90% at 100% 0%, rgba(var(--c), 0.1), transparent 55%),
    var(--bg-surface);
  transition: border-color 0.25s ease, box-shadow 0.3s ease, transform 0.35s var(--ease-out);
}
html.dark .bento-card {
  background:
    radial-gradient(120% 90% at 100% 0%, rgba(var(--c), 0.16), transparent 55%),
    var(--bg-surface);
}
/* 光标柔光 */
.bento-card::before {
  content: "";
  position: absolute;
  inset: 0;
  z-index: -1;
  background: radial-gradient(360px circle at var(--mx, 50%) var(--my, -20%), rgba(var(--c), 0.13), transparent 60%);
  opacity: 0;
  transition: opacity 0.3s ease;
}
.bento-card:hover {
  border-color: rgba(var(--c), 0.45);
  box-shadow: var(--shadow-md);
  transform: translateY(-3px);
}
.bento-card:hover::before { opacity: 1; }
.bento-card:focus-within { border-color: rgba(var(--c), 0.6); }

.bento-card.is-lg { grid-column: span 3; }
.bento-card.is-md { grid-column: span 2; }
.bento-card.is-wide { grid-column: span 6; min-height: 0; }

.bc-head { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 18px; }
.bc-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border: 1px solid rgba(var(--c), 0.22);
  border-radius: 14px;
  background: rgba(var(--c), 0.1);
  color: rgb(var(--c));
  transition: transform 0.4s var(--ease-spring);
}
.bento-card:hover .bc-icon { transform: scale(1.08) rotate(-4deg); }
.bc-count {
  padding: 3px 10px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--bg-surface);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.bc-title { margin-bottom: 6px; font-size: 1.22rem; font-weight: 700; letter-spacing: -0.02em; }
/* 整张卡可点：标题链接的伪元素铺满卡片；卡内文章链接叠在它上面 */
.bc-link { color: var(--text-primary); }
.bc-link::after { content: ""; position: absolute; inset: 0; z-index: 0; border-radius: inherit; }
.bc-link:hover { color: var(--text-primary); text-decoration: none; }
.bc-link:focus-visible { outline: none; }
.bc-link:focus-visible::after { outline: 2px solid var(--accent); outline-offset: -2px; }
.bc-desc {
  display: -webkit-box;
  overflow: hidden;
  margin-bottom: 16px;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.6;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.bc-pages {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0 -8px 12px;
  list-style: none;
}
.bc-pages a {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 8px;
  border-radius: 8px;
  color: var(--text-body);
  font-size: 13.5px;
  transition: background 0.15s ease, color 0.15s ease;
}
.bc-pages a:hover { background: rgba(var(--c), 0.09); color: var(--text-primary); text-decoration: none; }
.bc-page-icon { color: var(--text-muted); transition: color 0.15s ease; }
.bc-pages a:hover .bc-page-icon { color: rgb(var(--c)); }
.bc-page-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.bc-more {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: auto;
  color: rgb(var(--c));
  font-size: 13px;
  font-weight: 600;
  opacity: 0.85;
  transition: gap 0.2s var(--ease-out), opacity 0.2s ease;
}
.bento-card:hover .bc-more { gap: 8px; opacity: 1; }

/* 收尾的宽卡：横排，文章链接平铺成两列 */
.bento-card.is-wide {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) minmax(0, 1.3fr);
  grid-template-rows: auto auto 1fr;
  column-gap: 20px;
  align-items: start;
}
.is-wide .bc-head { grid-row: 1 / 4; margin: 0; }
.is-wide .bc-count { display: none; }
.is-wide .bc-title { grid-column: 2; }
.is-wide .bc-desc { grid-column: 2; margin-bottom: 8px; }
.is-wide .bc-more { grid-column: 2; margin-top: 0; }
.is-wide .bc-pages {
  display: grid;
  grid-column: 3;
  grid-row: 1 / 4;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 0;
}

.bento-card.skeleton {
  min-height: 250px;
  background: linear-gradient(100deg, var(--bg-subtle) 30%, var(--bg-hover) 50%, var(--bg-subtle) 70%);
  background-size: 200% 100%;
  animation: shimmer 1.4s linear infinite;
}

/* ================= 热门 + 最近更新 ================= */
.duo {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  padding-top: 8px;
}
.panel {
  padding: 8px;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--bg-surface);
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 14px 12px;
}
.panel-head h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 1.05rem;
  font-weight: 700;
  letter-spacing: -0.01em;
}
.panel-head h2 svg { color: var(--accent); }
.panel-head > span,
.panel-head > a { color: var(--text-muted); font-size: 13px; }
.panel-head > a:hover { color: var(--accent); text-decoration: none; }

.rank, .timeline { list-style: none; }
.rank-row {
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
  border-radius: 12px;
  color: var(--text-primary);
  transition: background 0.15s ease;
}
.rank-row:hover { background: var(--bg-subtle); text-decoration: none; color: var(--text-primary); }
.rank-no {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: var(--bg-hover);
  color: var(--text-muted);
  font-size: 12.5px;
  font-weight: 750;
  font-variant-numeric: tabular-nums;
}
.rank-no.top { background: var(--accent-soft-strong); color: var(--accent); }
.rank-main { display: flex; min-width: 0; flex-direction: column; gap: 6px; }
.rank-title {
  overflow: hidden;
  font-size: 14.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rank-bar { display: block; height: 3px; overflow: hidden; border-radius: 3px; background: var(--bg-hover); }
.rank-bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--brand-gradient);
  transform-origin: left;
  animation: grow 1s var(--ease-out) 0.2s both;
}
@keyframes grow { from { transform: scaleX(0); } }
.rank-cat { color: var(--text-muted); font-size: 12.5px; }
.rank-views {
  min-width: 44px;
  color: var(--text-secondary);
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.timeline { position: relative; }
.timeline::before {
  content: "";
  position: absolute;
  top: 22px;
  bottom: 22px;
  left: 26px;
  width: 1px;
  background: var(--border);
}
.tl-row {
  position: relative;
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 10px 14px;
  border-radius: 12px;
  color: var(--text-primary);
  transition: background 0.15s ease;
}
.tl-row:hover { background: var(--bg-subtle); text-decoration: none; color: var(--text-primary); }
.tl-dot {
  position: relative;
  flex-shrink: 0;
  width: 9px;
  height: 9px;
  margin: 7px 0 0 8px;
  border: 2px solid var(--bg-surface);
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 1px var(--accent);
  box-sizing: content-box;
}
.tl-dot.created { background: var(--success); box-shadow: 0 0 0 1px var(--success); }
.tl-main { display: flex; min-width: 0; flex-direction: column; gap: 2px; }
.tl-title {
  overflow: hidden;
  font-size: 14.5px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tl-meta { color: var(--text-muted); font-size: 12.5px; }
.tl-kind {
  margin-right: 4px;
  padding: 0 6px;
  border-radius: 5px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 11.5px;
  font-weight: 600;
}
.tl-kind.created { background: var(--success-soft); color: var(--success); }

/* ================= CTA ================= */
.cta {
  position: relative;
  isolation: isolate;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 32px;
  overflow: hidden;
  padding: clamp(32px, 5vw, 56px);
  border-radius: var(--radius-xl);
  background: linear-gradient(135deg, #0b0e3f 0%, #00045a 45%, #1d1a7a 100%);
  color: #fff;
  box-shadow: var(--shadow-lg);
}
.cta::before {
  content: "";
  position: absolute;
  inset: 0;
  z-index: -1;
  background-image: radial-gradient(rgba(255, 255, 255, 0.14) 1px, transparent 1.2px);
  background-size: 22px 22px;
  -webkit-mask-image: linear-gradient(90deg, transparent, #000 60%);
  mask-image: linear-gradient(90deg, transparent, #000 60%);
}
.cta-glow {
  position: absolute;
  top: -60%;
  right: -10%;
  z-index: -1;
  width: 60%;
  height: 200%;
  background: radial-gradient(closest-side, rgba(124, 92, 255, 0.45), transparent);
}
.cta-copy { max-width: 600px; }
.cta-eyebrow {
  display: inline-block;
  margin-bottom: 12px;
  padding: 3px 10px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 999px;
  color: rgba(255, 255, 255, 0.8);
  font-size: 12px;
  font-weight: 600;
}
.cta h2 {
  margin-bottom: 12px;
  font-size: clamp(1.6rem, 3.4vw, 2.3rem);
  font-weight: 750;
  letter-spacing: -0.03em;
  line-height: 1.2;
}
.cta p { color: rgba(255, 255, 255, 0.72); font-size: 15.5px; line-height: 1.7; }
.cta-actions { display: flex; flex-shrink: 0; flex-wrap: wrap; gap: 10px; }
.btn-light { background: #fff; color: #00045a; }
.btn-light:hover { background: #eef0ff; color: #00045a; }
.btn-ghost-light { border-color: rgba(255, 255, 255, 0.28); color: #fff; }
.btn-ghost-light:hover { border-color: rgba(255, 255, 255, 0.6); background: rgba(255, 255, 255, 0.08); color: #fff; }

@keyframes rise {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: none; }
}

/* ================= Responsive ================= */
@media (max-width: 1024px) {
  .bento { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .bento-card.is-lg,
  .bento-card.is-md { grid-column: span 1; }
  .bento-card.is-wide { grid-column: span 2; }
  .duo { grid-template-columns: minmax(0, 1fr); }
  .cta { flex-direction: column; align-items: flex-start; }
}

@media (max-width: 640px) {
  .hero { padding: 44px 16px 24px; }
  .hero-badge { margin-bottom: 22px; font-size: 12.5px; }
  .hero-badge-time { display: none; }
  .hero-title { font-size: clamp(2.4rem, 12.5vw, 3.4rem); }
  .hero-sub { margin-bottom: 26px; font-size: 1rem; }
  .hero-search { height: 54px; padding: 0 12px 0 16px; font-size: 15px; }
  .hero-search-kbd { display: none; }
  .hero-actions { width: 100%; }
  .hero-actions .btn { flex: 1; }
  .hero-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); margin-top: 40px; }
  .stat:nth-child(3) { border-left: 0; }
  .stat:nth-child(n + 3) { border-top: 1px solid var(--border); }
  .block { padding: 40px 16px; }
  .block-head { flex-direction: column; align-items: flex-start; }
  .bento { grid-template-columns: minmax(0, 1fr); gap: 12px; }
  .bento-card.is-lg,
  .bento-card.is-md,
  .bento-card.is-wide { grid-column: span 1; min-height: 0; }
  .bento-card.is-wide { display: flex; }
  .is-wide .bc-head { margin-bottom: 18px; }
  .is-wide .bc-count { display: inline-block; }
  .is-wide .bc-pages { display: flex; margin: 0 -8px 12px; }
  .rank-cat { display: none; }
  .cta-actions { width: 100%; }
  .cta-actions .btn { flex: 1; }
}
</style>
