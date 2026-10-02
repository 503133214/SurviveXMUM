<template>
  <nav class="doc-toc" :class="{ sheet }" aria-label="本页目录">
    <p v-if="!sheet" class="toc-head">本页目录</p>
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
import { ArrowUp } from "lucide-vue-next";
import { scrollBehavior } from "@/utils/motion.js";

// 「正在读」判定线比跳转落点再低这么多：scrollIntoView 停下的位置可能带小数、
// 被浏览器取整，留一点余量，保证刚跳到的标题一定算过线
const ACTIVE_SLACK = 8;

// 「正在读」判定线（距视口顶部的像素）。不能写死常量：顶栏在桌面是两行 96px、
// 手机是一行 56px，窄屏正文上方还多一条 46px 的吸顶工具条，写死的数一换布局就错位
// （曾经的 96 配旧版 64px 顶栏刚好，顶栏改成两行后，点目录跳过去的标题反而不亮）。
// 标题被跳转（点目录、# 锚点、带 #hash 的搜索结果）后，顶边停在
//   html 的 scroll-padding-top（顶栏高度 + 16px）+ 标题自己的 scroll-margin-top
// 即桌面 112 + 8 = 120，手机 72 + 54（让开吸顶工具条）= 126。
// 判定线取这个落点再加 ACTIVE_SLACK，刚跳到的标题恰好在线上方、成为当前项，
// 以后顶栏或工具条再改高度也会自动跟上。sample 取任一正文标题即可：
// h1–h4 的 scroll-margin-top 由同一条规则给出。
function activeLine(sample) {
  const padding = parseFloat(getComputedStyle(document.documentElement).scrollPaddingTop) || 0;
  const margin = sample ? parseFloat(getComputedStyle(sample).scrollMarginTop) || 0 : 0;
  return padding + margin + ACTIVE_SLACK;
}

export default {
  name: "DocToc",
  components: { ArrowUp },
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
        let line = null;
        for (const item of this.items) {
          const el = document.getElementById(item.id);
          if (!el) continue;
          // 每帧只读一次计算样式：判定线对所有标题都一样
          if (line === null) line = activeLine(el);
          if (el.getBoundingClientRect().top <= line) current = item.id;
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
      // 系统要求减少动效时直接跳过去（JS 里写死 smooth 会绕过 CSS 的 reduce 设置）
      el.scrollIntoView({ behavior: scrollBehavior(), block: "start" });
      history.replaceState(history.state, "", `#${id}`);
      this.activeId = id;
      this.$emit("navigate", id);
    },
    toTop() {
      window.scrollTo({ top: 0, behavior: scrollBehavior() });
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
.doc-toc { font-size: var(--fs-sm); }

.toc-head {
  margin-bottom: 12px;
  color: var(--text-primary);
  font-size: var(--fs-sm);
  font-weight: 600;
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
  border-radius: var(--radius-xs);
  background: var(--accent);
  transition: transform var(--dur) var(--ease-out), height var(--dur) var(--ease-out), opacity var(--dur) ease;
}

.toc-list a {
  display: block;
  padding: 5px 0 5px 14px;
  color: var(--text-muted);
  line-height: 1.5;
  transition: color var(--dur) ease;
}
.toc-list a:hover { color: var(--text-primary); text-decoration: none; }
/* 目录项是贴着细轨排的整行，向内画焦点环，避免被目录自己的滚动容器裁掉 */
.toc-list a:focus-visible { outline-offset: -2px; }
/* 层级只靠缩进区分，各级字号保持一致，窄栏里读起来更整齐 */
.toc-list .lv-1 a { padding-left: 26px; }
.toc-list .lv-2 a { padding-left: 38px; }
.toc-list .lv-3 a { padding-left: 50px; }
.toc-list li.active > a { color: var(--accent); font-weight: 600; }

.sheet .toc-list a { padding-top: 9px; padding-bottom: 9px; font-size: var(--fs-ui); }

/* 回到顶部只是一行文字链接式的按钮，不加边框和底色 */
.toc-top {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 16px;
  padding: 4px 0;
  border: 0;
  border-radius: var(--radius-xs);
  background: none;
  color: var(--text-muted);
  font: inherit;
  font-size: var(--fs-sm);
  cursor: pointer;
  transition: color var(--dur) ease;
}
.toc-top:hover { color: var(--text-primary); }
</style>
