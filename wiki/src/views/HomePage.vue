<!-- src/views/Home.vue -->
<template>
  <div class="new-home-page">
    <!-- Hero Section -->
    <section class="hero-section">
      <div class="hero-overlay"></div>
      <div class="hero-content">
        <h1 class="hero-title">SurviveXMUM</h1>
        <h2 class="hero-subtitle">更适合厦大马校学子的生存指南</h2>
        <p class="hero-welcome">Welcome to XMUM.</p>
        <div class="hero-cta-buttons">
          <button
            v-for="(button, idx) in ctaButtons"
            :key="idx"
            @click="navigateTo(button.path)"
            class="cta-button"
          >
            {{ button.text }}
          </button>
        </div>
      </div>
      <div class="hero-image-placeholder">
        <!-- 这里可以放一个真实的 SVG/PNG logo 或者动效 -->
        <div class="logo-graphic">
          <span class="logo-text-main">Survive</span>
          <span class="logo-text-main">XMUM</span>
          <span class="logo-text-p"></span>
        </div>
      </div>
    </section>

    <!-- Features Section -->
    <section class="features-section">
      <div
        v-for="(card, idx) in featureCards"
        :key="idx"
        class="feature-card"
        @click="navigateTo(card.path)"
      >
        <!-- 顶部图标占位 -->
        <div class="card-icon">
          <!-- 你可以把下面的 emoji 换成 SVG 或者 <img> -->
          📘
        </div>
        <h3>{{ card.title }}</h3>
        <p>{{ card.description }}</p>
      </div>
    </section>

    <!-- Footer -->
    <footer class="home-footer">
      <p>Released under the MIT License.</p>
      <p>Copyright © 2023-2025 XMUM Wiki Team</p>
    </footer>
  </div>
</template>

<script>
export default {
  name: "HomePage",
  data() {
    return {
      ctaButtons: [
        { text: "前言", path: "/docs/README" },
        { text: "征集", path: "/docs/contribute/collection" },
        { text: "贡献指南", path: "/docs/contribute/guide" },
        { text: "快问快答", path: "/docs/faq" },
      ],
      featureCards: [
        {
          title: "新生指引",
          description: "关于入学、厦大校园生活、心态调整等全方位建议。",
          path: "/docs/README",
        },
        {
          title: "保研须知",
          description: "保研全流程、竞赛科研干货，一文看懂如何无忧保研。",
          path: "/docs/postgraduate-recommendation",
        },
        {
          title: "学习路线",
          description: "学习规划、培养方案之外的进阶路线，助你脱颖而出。",
          path: "/docs/learning-path",
        },
      ],
    };
  },
  methods: {
    navigateTo(path) {
      if (path) {
        this.$router.push(path);
      }
    },
  },
};
</script>

<style scoped>
/* 整体页面基调 */
.new-home-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  font-family: "Helvetica Neue", Arial, sans-serif;
  min-height: 100vh;
  background-color: #ffffff;
  color: #2c3e50;
  position: relative;
  padding-bottom: 120px; /* 留出页脚空间 */
}

/* ===== Hero Section ===== */
.hero-section {
  position: relative;
  width: 100%;
  height: 480px; /* 可根据需要再调整 */
  background-color: #f0f4f8;
  overflow: hidden;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 5%;
  box-sizing: border-box;
}

/* 半透明覆盖层，让文字更突出 */
.hero-overlay {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(44, 62, 80, 0.15);
  z-index: 1;
}

/* Hero 内容在最上层 */
.hero-content {
  position: relative;
  z-index: 2;
  max-width: 520px;
}

/* 大标题渐变文字 */
.hero-title {
  font-size: 3.8rem;
  font-weight: 800;
  margin: 0 0 0.3em;
  background: linear-gradient(90deg, #42b983, #3498db);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  line-height: 1.1;
}

/* 副标题 */
.hero-subtitle {
  font-size: 1.8rem;
  font-weight: 500;
  margin: 0 0 0.5em;
  color: #34495e;
}

/* 简短引导语 */
.hero-welcome {
  font-size: 1.1rem;
  margin-bottom: 1.2em;
  color: #7f8c8d;
}

/* 按钮组 */
.hero-cta-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.hero-cta-buttons .cta-button {
  background-color: #3498db;
  color: #ffffff;
  border: none;
  padding: 12px 28px;
  border-radius: 25px;
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.3s, transform 0.2s;
  box-shadow: 0 4px 12px rgba(52, 73, 94, 0.12);
}
.hero-cta-buttons .cta-button:nth-child(2) {
  background-color: #42b983;
}
.hero-cta-buttons .cta-button:hover {
  transform: translateY(-2px);
}
.hero-cta-buttons .cta-button:active {
  transform: translateY(0);
  box-shadow: 0 2px 6px rgba(52, 73, 94, 0.1);
}

/* Hero 右侧的图片/Logo 占位区域 */
.hero-image-placeholder {
  position: relative;
  z-index: 2;
  width: 360px;
  height: 300px;
  background-color: #e0e6ed;
  border-radius: 12px;
  flex-shrink: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.08);
}

.logo-graphic {
  text-align: center;
  color: #2c3e50;
  font-family: "Georgia", serif;
}
.logo-text-main {
  display: block;
  font-size: 3em;
  font-weight: bold;
  line-height: 1;
}
.logo-text-p {
  display: block;
  font-size: 1.5em;
  font-style: italic;
  margin-top: 6px;
  border-top: 2px dashed #7f8c8c;
  padding-top: 6px;
}

/* ===== Features Section ===== */
.features-section {
  width: 100%;
  max-width: 1200px;
  margin: 60px 0 40px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 24px;
  padding: 0 4%;
  box-sizing: border-box;
}

.feature-card {
  background-color: #ffffff;
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  cursor: pointer;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}
.feature-card:hover {
  transform: translateY(-6px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

/* 卡片图标区域 */
.card-icon {
  font-size: 2.5rem;
  margin-bottom: 12px;
  color: #3498db;
}

/* 卡片标题 */
.feature-card h3 {
  font-size: 1.3rem;
  margin: 0 0 8px;
  color: #2c3e50;
  font-weight: 600;
}

/* 卡片描述 */
.feature-card p {
  font-size: 0.95rem;
  line-height: 1.5;
  color: #555;
  margin: 0;
}

/* ===== Footer ===== */
.home-footer {
  position: fixed;
  bottom: 0;
  left: 0;
  width: 100%;
  background-color: #2c3e50;
  color: #bdc3c7;
  text-align: center;
  padding: 14px 0;
  font-size: 0.9rem;
  z-index: 100;
}
.home-footer p {
  margin: 2px 0;
}

/* ===== 响应式调整 ===== */
@media (max-width: 1024px) {
  .hero-section {
    flex-direction: column;
    height: auto;
    padding: 60px 5%;
  }
  .hero-image-placeholder {
    margin-top: 40px;
    width: 280px;
    height: 220px;
  }
  .hero-title {
    font-size: 3rem;
  }
  .hero-subtitle {
    font-size: 1.5rem;
  }
}

@media (max-width: 768px) {
  .hero-title {
    font-size: 2.6rem;
  }
  .hero-subtitle {
    font-size: 1.3rem;
  }
  .hero-cta-buttons .cta-button {
    padding: 10px 24px;
    font-size: 0.95rem;
  }
  .hero-image-placeholder {
    width: 240px;
    height: 180px;
  }
}

@media (max-width: 480px) {
  .features-section {
    grid-template-columns: 1fr;
    gap: 20px;
    margin: 40px 0;
    padding: 0 8%;
  }
  .hero-title {
    font-size: 2.2rem;
  }
  .hero-subtitle {
    font-size: 1.2rem;
  }
  .hero-welcome {
    font-size: 1rem;
  }
  .hero-cta-buttons {
    gap: 8px;
  }
  .hero-cta-buttons .cta-button {
    padding: 8px 20px;
    font-size: 0.9rem;
  }
}
</style>
