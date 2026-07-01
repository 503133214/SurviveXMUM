<template>
  <div class="home">
    <!-- ===== Hero ===== -->
    <section class="hero">
      <img src="/svg/Simple_Logo.svg" alt="XMUM Wiki" class="hero-logo" />
      <p class="hero-kicker">厦门大学马来西亚分校 · 学生知识库</p>
      <h1 class="hero-title">少走弯路的<br />厦马生存指南</h1>
      <p class="hero-sub">
        入学、学习、生活、人生 —— 由学长学姐共同维护，写一个 Markdown 即可贡献。
      </p>
      <div class="hero-actions">
        <button class="btn btn-solid" @click="go(`/docs/${HOME_PATH}`)">开始阅读</button>
        <router-link class="btn btn-outline" to="/docs/贡献指南">如何贡献 →</router-link>
      </div>
      <p class="hero-meta">
        <AnimatedNumber :to="pages.length" /> 篇文档 ·
        <AnimatedNumber :to="cats.length" /> 个分类 · 持续更新
      </p>
    </section>

    <!-- ===== 分类卡片网格 ===== -->
    <section class="block">
      <div class="block-head">
        <h2 v-reveal>浏览分类</h2>
        <p v-reveal>挑一个篇章开始探索。</p>
      </div>

      <div class="grid">
        <article
          v-for="(cat, i) in cats"
          :key="cat.slug"
          class="card"
          v-reveal="{ delay: i * 60 }"
          @click="openCategory(cat)"
        >
          <div class="card-media" :style="{ background: gradient(i) }">
            <span class="card-emoji">{{ cat.icon }}</span>
            <span class="card-count">{{ countPages(cat) }} 篇</span>
          </div>
          <div class="card-text">
            <h3 class="card-title">{{ cat.label }}<span class="card-arrow">→</span></h3>
            <p class="card-desc">{{ cat.description || hint(cat) }}</p>
          </div>
        </article>
      </div>
    </section>

    <!-- ===== 热门文档 ===== -->
    <section v-if="popular.length" class="block">
      <div class="block-head">
        <h2 v-reveal>🔥 热门文档</h2>
        <span class="block-sub" v-reveal>大家都在看</span>
      </div>
      <div class="pop-list">
        <a
          v-for="(p, i) in popular"
          :key="p.path"
          class="pop-row"
          v-reveal="{ delay: i * 40 }"
          @click="go(`/docs/${p.path}`)"
        >
          <span class="pop-rank" :class="{ top: i < 3 }">{{ i + 1 }}</span>
          <span class="pop-title">{{ p.title }}</span>
          <span class="pop-cat">{{ p.category }}</span>
          <span class="pop-views">{{ p.viewCount }} 次浏览</span>
          <span class="news-go">→</span>
        </a>
      </div>
    </section>

    <!-- ===== 最近更新（编辑式列表） ===== -->
    <section v-if="recent.length" class="block">
      <div class="block-head">
        <h2 v-reveal>最近更新</h2>
        <router-link class="block-link" :to="`/docs/${HOME_PATH}`" v-reveal>
          全部文档 →
        </router-link>
      </div>

      <div class="news">
        <a
          v-for="(p, i) in recent"
          :key="p.path"
          class="news-row"
          v-reveal="{ delay: i * 40 }"
          @click="go(`/docs/${p.path}`)"
        >
          <span class="news-date">{{ fmtDate(p.lastUpdated) }}</span>
          <span class="news-title">{{ p.title }}</span>
          <span class="news-cat">{{ p.category }}</span>
          <span class="news-go">→</span>
        </a>
      </div>
    </section>

    <!-- ===== 结尾号召 ===== -->
    <section class="cta" v-reveal>
      <h2>发现了错误，或想分享经验？</h2>
      <p>这份指南由社区共建。你只需要写一个 Markdown 文件。</p>
      <div class="hero-actions">
        <router-link class="btn btn-solid" to="/docs/贡献指南">查看贡献指南</router-link>
        <router-link class="btn btn-outline" to="/contributors">🏆 贡献榜</router-link>
        <a class="btn btn-outline" :href="REPO" target="_blank" rel="noopener noreferrer">
          前往 GitHub →
        </a>
      </div>
    </section>

    <footer class="foot">
      <span>SurviveXMUM</span>
      <span>Released under the MIT License · © 2023–2025 XMUM Wiki Team</span>
    </footer>
  </div>
</template>

<script>
import { pages, categories, HOME_PATH, REPO } from "@/wiki";
import AnimatedNumber from "@/components/AnimatedNumber.vue";

// 仅在卡片"图片区"使用颜色，其余界面保持黑白编辑风
const GRADIENTS = [
  "linear-gradient(135deg, #6EE7B7 0%, #3B82F6 100%)",
  "linear-gradient(135deg, #FBCFE8 0%, #8B5CF6 100%)",
  "linear-gradient(135deg, #FDE68A 0%, #F97316 100%)",
  "linear-gradient(135deg, #A5F3FC 0%, #6366F1 100%)",
  "linear-gradient(135deg, #BBF7D0 0%, #059669 100%)",
  "linear-gradient(135deg, #FECACA 0%, #DB2777 100%)",
];

export default {
  name: "HomePage",
  components: { AnimatedNumber },
  data() {
    return { pages, HOME_PATH, REPO };
  },
  computed: {
    cats() {
      return categories();
    },
    recent() {
      return [...this.pages]
        .filter((p) => p.lastUpdated && p.path !== HOME_PATH)
        .sort((a, b) => new Date(b.lastUpdated) - new Date(a.lastUpdated))
        .slice(0, 5);
    },
    popular() {
      return [...this.pages]
        .filter((p) => p.path !== HOME_PATH && (p.viewCount || 0) > 0)
        .sort((a, b) => (b.viewCount || 0) - (a.viewCount || 0))
        .slice(0, 6);
    },
  },
  methods: {
    go(path) {
      if (path) this.$router.push(path);
    },
    gradient(i) {
      return GRADIENTS[i % GRADIENTS.length];
    },
    firstChildPath(node) {
      for (const child of node.children || []) {
        if (child.type === "page") return child.path;
        const deep = this.firstChildPath(child);
        if (deep) return deep;
      }
      return null;
    },
    openCategory(cat) {
      const p = this.firstChildPath(cat);
      if (p) this.go(`/docs/${p}`);
    },
    countPages(node) {
      let n = 0;
      for (const child of node.children || []) {
        if (child.type === "page") n++;
        else n += this.countPages(child);
      }
      return n;
    },
    hint(cat) {
      const titles = (cat.children || [])
        .filter((c) => c.type === "page")
        .slice(0, 3)
        .map((c) => c.title)
        .join("、");
      return titles ? `包含 ${titles} 等` : "敬请期待";
    },
    fmtDate(iso) {
      const d = new Date(iso);
      if (Number.isNaN(d.getTime())) return "";
      return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")}`;
    },
  },
};
</script>

<style scoped>
.home {
  background: var(--bg-page);
  color: var(--text-primary);
}

/* ===== Hero ===== */
.hero {
  max-width: var(--maxw);
  margin: 0 auto;
  padding: 96px 24px 72px;
  text-align: center;
}
.hero-logo {
  width: 88px;
  height: auto;
  margin-bottom: 28px;
  animation: rise 0.7s var(--ease-out) both;
}
html.dark .hero-logo { filter: brightness(0) invert(1); }
.hero-kicker {
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--text-secondary);
  margin-bottom: 18px;
  animation: rise 0.7s var(--ease-out) 0.05s both;
}
.hero-title {
  font-size: clamp(2.8rem, 7vw, 5.2rem);
  font-weight: 800;
  line-height: 1.02;
  letter-spacing: -0.035em;
  margin-bottom: 26px;
  animation: rise 0.8s var(--ease-out) 0.12s both;
}
.hero-sub {
  font-size: clamp(1.05rem, 2vw, 1.3rem);
  color: var(--text-secondary);
  max-width: 600px;
  margin: 0 auto 36px;
  line-height: 1.6;
  animation: rise 0.8s var(--ease-out) 0.2s both;
}
.hero-actions {
  display: flex;
  gap: 14px;
  justify-content: center;
  flex-wrap: wrap;
  animation: rise 0.8s var(--ease-out) 0.28s both;
}
.hero-meta {
  margin-top: 28px;
  font-size: 0.9rem;
  color: var(--text-muted);
  animation: rise 0.8s var(--ease-out) 0.36s both;
}

/* ===== Buttons (editorial pills) ===== */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 12px 26px;
  border-radius: 999px;
  font-size: 0.98rem;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  text-decoration: none;
  transition: transform 0.25s var(--ease-out), background 0.2s ease, color 0.2s ease,
    border-color 0.2s ease, opacity 0.2s ease;
}
.btn-solid {
  background: var(--accent);
  color: var(--accent-contrast);
}
.btn-solid:hover { transform: translateY(-2px); opacity: 0.88; text-decoration: none; }
.btn-outline {
  background: transparent;
  color: var(--text-primary);
  border-color: var(--border-strong);
}
.btn-outline:hover {
  border-color: var(--text-primary);
  text-decoration: none;
  transform: translateY(-2px);
}

/* ===== Section blocks ===== */
.block {
  max-width: var(--maxw);
  margin: 0 auto;
  padding: 40px 24px 64px;
}
.block-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 32px;
  border-bottom: 1px solid var(--border);
  padding-bottom: 20px;
}
.block-head h2 {
  font-size: clamp(1.6rem, 3vw, 2.2rem);
  font-weight: 700;
  letter-spacing: -0.02em;
}
.block-head p { color: var(--text-secondary); font-size: 1rem; }
.block-link { font-size: 0.95rem; font-weight: 600; color: var(--text-primary); white-space: nowrap; }
.block-link:hover { text-decoration: none; opacity: 0.6; }
.block-sub { color: var(--text-muted); font-size: 0.95rem; white-space: nowrap; }

/* ===== 热门文档列表 ===== */
.pop-list { display: flex; flex-direction: column; }
.pop-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 15px 6px;
  border-bottom: 1px solid var(--border);
  cursor: pointer;
  transition: background 0.15s ease, padding-left 0.2s ease;
}
.pop-row:hover { background: var(--bg-subtle); padding-left: 12px; text-decoration: none; }
.pop-rank {
  flex-shrink: 0;
  display: inline-grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: var(--bg-hover);
  color: var(--text-muted);
  font-size: 13px;
  font-weight: 800;
}
.pop-rank.top { background: var(--accent); color: var(--accent-contrast); }
.pop-title {
  flex: 1;
  min-width: 0;
  font-size: 1.02rem;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pop-cat { flex-shrink: 0; color: var(--text-muted); font-size: 0.85rem; }
.pop-views { flex-shrink: 0; color: var(--text-secondary); font-size: 0.85rem; white-space: nowrap; }
.pop-row .news-go { flex-shrink: 0; color: var(--text-muted); opacity: 0; transition: opacity 0.2s ease; }
.pop-row:hover .news-go { opacity: 1; }
@media (max-width: 640px) {
  .pop-cat { display: none; }
}

/* ===== Card grid (OpenAI-style: image tile + text below) ===== */
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 28px 24px;
}
.card { cursor: pointer; }
.card-media {
  position: relative;
  aspect-ratio: 16 / 10;
  border-radius: var(--radius);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
}
.card-media::after {
  content: "";
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0);
  transition: background 0.3s ease;
}
.card:hover .card-media::after { background: rgba(0, 0, 0, 0.06); }
.card-emoji {
  font-size: 3.4rem;
  filter: drop-shadow(0 6px 14px rgba(0, 0, 0, 0.25));
  transition: transform 0.45s var(--ease-out);
  z-index: 1;
}
.card:hover .card-emoji { transform: scale(1.12); }
.card-count {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 1;
  font-size: 0.78rem;
  font-weight: 600;
  color: #fff;
  background: rgba(0, 0, 0, 0.28);
  backdrop-filter: blur(6px);
  padding: 4px 10px;
  border-radius: 999px;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 1.2rem;
  font-weight: 700;
  letter-spacing: -0.01em;
  margin-bottom: 6px;
}
.card-arrow {
  font-size: 1rem;
  opacity: 0;
  transform: translateX(-6px);
  transition: all 0.3s var(--ease-out);
}
.card:hover .card-arrow { opacity: 1; transform: translateX(0); }
.card-desc {
  color: var(--text-secondary);
  font-size: 0.92rem;
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

/* ===== Latest list (editorial rows) ===== */
.news { border-top: 1px solid var(--border); }
.news-row {
  display: grid;
  grid-template-columns: 120px 1fr auto 24px;
  align-items: center;
  gap: 20px;
  padding: 20px 12px;
  border-bottom: 1px solid var(--border);
  cursor: pointer;
  text-decoration: none;
  color: var(--text-primary);
  transition: background 0.2s ease, padding-left 0.25s var(--ease-out);
}
.news-row:hover { background: var(--bg-subtle); padding-left: 20px; text-decoration: none; }
.news-date { color: var(--text-muted); font-size: 0.85rem; font-variant-numeric: tabular-nums; }
.news-title { font-size: 1.1rem; font-weight: 600; letter-spacing: -0.01em; }
.news-cat { color: var(--text-secondary); font-size: 0.85rem; }
.news-go { color: var(--text-muted); opacity: 0; transition: opacity 0.2s ease; text-align: right; }
.news-row:hover .news-go { opacity: 1; }

/* ===== CTA ===== */
.cta {
  max-width: var(--maxw);
  margin: 0 auto;
  padding: 80px 24px 96px;
  text-align: center;
}
.cta h2 { font-size: clamp(1.8rem, 4vw, 2.8rem); font-weight: 800; letter-spacing: -0.025em; margin-bottom: 14px; }
.cta p { color: var(--text-secondary); font-size: 1.1rem; margin-bottom: 30px; }

/* ===== Footer ===== */
.foot {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
  max-width: var(--maxw);
  margin: 0 auto;
  padding: 32px 24px;
  border-top: 1px solid var(--border);
  color: var(--text-muted);
  font-size: 0.85rem;
}
.foot span:first-child { font-weight: 700; color: var(--text-secondary); letter-spacing: -0.01em; }

@keyframes rise {
  from { opacity: 0; transform: translateY(18px); }
  to { opacity: 1; transform: none; }
}

@media (max-width: 640px) {
  .hero { padding: 64px 20px 48px; }
  .news-row { grid-template-columns: 1fr auto; gap: 4px 12px; }
  .news-date { grid-column: 1 / -1; order: 3; }
  .news-go { display: none; }
}
</style>
