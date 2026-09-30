<template>
  <nav class="doc-toc" :class="{ sheet }" aria-label="本页目录">
    <p v-if="!sheet" class="toc-head">
      <TextQuote :size="15" :stroke-width="2" aria-hidden="true" />
      本页目录
    </p>
    <div ref="track" class="toc-track">
      <span
        class="toc-indicator"
        :style="{ transform: `translateY(${indicator.top}px)`, height: `${indicator.height}px`, opacity: indicator.height ? 1 : 0 }"
        aria-hidden="true"
      ></span>
      <ul ref="list" class="toc-list">
        <li
          v-for="item in items"
          :key="item.id"
          :class="[`lv-${item.level - minLevel}`, { active: item.id === activeId }]"
        >
          <a
            :href="`#${item.id}`"
            :aria-current="item.id === activeId ? 'location' : undefined"
            @click.prevent="go(item.id)"
          >{{ item.text }}</a>
        </li>
      </ul>
    </div>
    <button v-if="!sheet" type="button" class="toc-top" @click="toTop">
      <ArrowUp :size="14" :stroke-width="2" aria-hidden="true" />
      回到顶部
    </button>
  </nav>
</template>

<script>
import { TextQuote, ArrowUp } from "lucide-vue-next";

// 标题进入这条线（顶栏下方一点）就算「正在读」
const ACTIVE_OFFSET = 96;

export default {
  name: "DocToc",
  components: { TextQuote, ArrowUp },
  props: {
    items: { type: Array, default: () => [] },
    // 移动端底部抽屉里用：去掉标题和回到顶部按钮
    sheet: { type: Boolean, default: false },
  },
  emits: ["navigate"],
  data() {
    return { activeId: "", indicator: { top: 0, height: 0 }, frame: 0 };
  },
  computed: {
    minLevel() {
      return this.items.reduce((m, i) => Math.min(m, i.level), 6);
    },
  },
  watch: {
    items() {
      this.$nextTick(this.spy);
    },
    activeId() {
      this.$nextTick(this.placeIndicator);
    },
  },
  methods: {
    spy() {
      cancelAnimationFrame(this.frame);
      this.frame = requestAnimationFrame(() => {
        let current = "";
        for (const item of this.items) {
          const el = document.getElementById(item.id);
          if (!el) continue;
          if (el.getBoundingClientRect().top <= ACTIVE_OFFSET) current = item.id;
          else break;
        }
        // 滚到底时最后几节可能永远到不了那条线，直接点亮最后一个
        const doc = document.documentElement;
        if (this.items.length && window.innerHeight + window.scrollY >= doc.scrollHeight - 4) {
          current = this.items[this.items.length - 1].id;
        }
        this.activeId = current || (this.items[0] && this.items[0].id) || "";
      });
    },
    placeIndicator() {
      const list = this.$refs.list;
      const li = list && list.querySelector("li.active");
      if (!li) {
        this.indicator = { top: 0, height: 0 };
        return;
      }
      this.indicator = { top: li.offsetTop, height: li.offsetHeight };
      // 目录很长时让当前项保持在可视范围。只滚目录自己的容器：
      // 用 scrollIntoView 会顺带打断页面正在进行的平滑滚动（点目录、搜索跳转时）
      const track = this.$refs.track;
      if (track && track.scrollHeight > track.clientHeight) {
        const top = li.offsetTop;
        const bottom = top + li.offsetHeight;
        if (top < track.scrollTop) track.scrollTop = top - 8;
        else if (bottom > track.scrollTop + track.clientHeight) track.scrollTop = bottom - track.clientHeight + 8;
      }
    },
    go(id) {
      const el = document.getElementById(id);
      if (!el) return;
      el.scrollIntoView({ behavior: "smooth", block: "start" });
      history.replaceState(history.state, "", `#${id}`);
      this.activeId = id;
      this.$emit("navigate", id);
    },
    toTop() {
      window.scrollTo({ top: 0, behavior: "smooth" });
    },
  },
  mounted() {
    window.addEventListener("scroll", this.spy, { passive: true });
    window.addEventListener("resize", this.spy, { passive: true });
    this.spy();
  },
  beforeUnmount() {
    window.removeEventListener("scroll", this.spy);
    window.removeEventListener("resize", this.spy);
    cancelAnimationFrame(this.frame);
  },
};
</script>

<style scoped>
.doc-toc { font-size: 13px; }

.toc-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: var(--text-primary);
  font-size: 13px;
  font-weight: 650;
}

.toc-track {
  position: relative;
  max-height: calc(100dvh - var(--header-height) - 160px);
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: none;
}
.toc-track::-webkit-scrollbar { display: none; }
.sheet .toc-track { max-height: 60dvh; }

/* 左侧细轨 + 跟随当前小节滑动的高亮段 */
.toc-list { position: relative; list-style: none; border-left: 1px solid var(--border); }
.toc-indicator {
  position: absolute;
  top: 0;
  left: 0;
  z-index: 1;
  width: 2px;
  margin-left: -0.5px;
  border-radius: 2px;
  background: var(--accent);
  transition: transform 0.3s var(--ease-out), height 0.3s var(--ease-out), opacity 0.2s ease;
}

.toc-list a {
  display: block;
  padding: 5px 0 5px 14px;
  color: var(--text-muted);
  line-height: 1.5;
  transition: color 0.15s ease;
}
.toc-list a:hover { color: var(--text-primary); text-decoration: none; }
.toc-list .lv-1 a { padding-left: 26px; }
.toc-list .lv-2 a { padding-left: 38px; font-size: 12.5px; }
.toc-list .lv-3 a { padding-left: 50px; font-size: 12.5px; }
.toc-list li.active > a { color: var(--accent); font-weight: 600; }

.sheet .toc-list a { padding-top: 9px; padding-bottom: 9px; font-size: 14.5px; }

.toc-top {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 16px;
  padding: 6px 10px;
  border: 1px solid var(--border);
  border-radius: 999px;
  background: var(--bg-surface);
  color: var(--text-secondary);
  font: inherit;
  font-size: 12.5px;
  cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease;
}
.toc-top:hover { border-color: var(--border-strong); color: var(--text-primary); }
</style>
