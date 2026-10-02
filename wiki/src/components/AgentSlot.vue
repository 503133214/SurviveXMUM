<!--
  首页 Hero 里给「站内问答助手」预留的位置。

  这个组件只是一个框：负责外框、标题和占位说明，本身没有对话逻辑、没有状态、
  不发任何请求。真正的助手以后作为单独组件放进默认插槽，上线时只需改 HomePage
  里的一行，占位文字、「筹备中」标记会自动消失，外框切换成实线（.is-live）。

  预留给真正助手的约定（现在不要实现）：
  - 用法：<AgentSlot v-slot="{ headingId }"><WikiAgent :labelledby="headingId" /></AgentSlot>
    插槽把标题 id 传出去，输入框用 aria-labelledby 指向它，区域名称前后一致。
  - 请求在 wiki/src/net/index.js 里新增函数（例如 askWiki(payload, success, failure)），
    走同源 /api；按 AGENTS.md 先上线后端接口，再部署依赖它的前端。
  - 回答是 Markdown，必须交给 MarkdownRenderer（经 DOMPurify 消毒），不要对原文 v-html。
  - 引用链接写成 /docs/<path>#<slugify(heading)>，锚点统一用 utils/slug.js，
    否则和正文标题的 id 对不上。
  - 后端的雪花 ID 一律按字符串处理。
  - 回答区域用 aria-live="polite"，加载中设置 aria-busy。
  - 出错时退回到同样的「搜索文档」入口，别让用户卡在一个坏掉的框里。
  - CommandPalette 的「/」快捷键会忽略 INPUT / TEXTAREA，所以在助手输入框里打「/」
    不会弹出搜索；⌘K 仍会打开搜索，这是有意保留的。
  - config.js 里暂时不加开关：现在没有可以开关的东西。
-->
<template>
  <section class="agent-slot" :class="{ 'is-live': $slots.default }" :aria-labelledby="headingId">
    <div class="agent-head">
      <MessageCircleQuestionMark class="agent-icon" :size="18" :stroke-width="1.75" aria-hidden="true" />
      <h2 :id="headingId" class="agent-title">{{ heading }}</h2>
      <!-- 状态写成文字而不是只靠颜色区分；插槽有内容（助手已上线）就不再显示 -->
      <span v-if="!$slots.default" class="agent-status">筹备中</span>
    </div>
    <!-- 挂载点：真正的问答助手放进默认插槽，占位内容随之消失 -->
    <slot :headingId="headingId">
      <p class="agent-text">这里预留给站内问答助手：它会根据本站文档回答你的问题。目前还在开发中，暂时不能提问。</p>
      <!-- 只放一个真按钮去打开现有的搜索面板；不做禁用的假输入框，免得看起来像坏掉的功能 -->
      <p class="agent-text">在此之前，可以先<button type="button" class="agent-search" aria-haspopup="dialog" @click="openPalette()">搜索文档</button>，或使用页面右上角的搜索<span class="agent-keys">（快捷键 <kbd class="ui-kbd">{{ shortcut }}</kbd> 或 <kbd class="ui-kbd">/</kbd>）</span>。</p>
    </slot>
  </section>
</template>

<script>
import { useId } from "vue";
import { MessageCircleQuestionMark } from "lucide-vue-next";
import { openPalette } from "@/composables/usePalette.js";
import { shortcutLabel } from "@/utils/shortcut.js";

export default {
  name: "AgentSlot",
  components: { MessageCircleQuestionMark },
  props: {
    // 可见的 h2 文字，同时也是这个区域的无障碍名称
    heading: { type: String, default: "站内问答助手" },
  },
  setup() {
    // useId 保证同一页面放多个实例时 id 也不重复；加前缀避免和正文标题锚点撞名
    return { headingId: `agent-${useId()}` };
  },
  data() {
    // 和顶栏共用同一个平台判断，Mac 显示 ⌘K，其余显示 Ctrl K
    return { shortcut: shortcutLabel() };
  },
  methods: { openPalette },
};
</script>

<style scoped>
/* 虚线框是「此处预留」的常规信号，也避免被误认成输入框 */
.agent-slot {
  box-sizing: border-box;
  padding: 16px 20px;
  border: 1px dashed var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--bg-subtle);
  text-align: left;
}

/* 真正的助手挂上来以后换成普通实线面板 */
.agent-slot.is-live {
  border-style: solid;
  border-color: var(--border);
  background: var(--bg-surface);
}

.agent-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.agent-icon {
  flex-shrink: 0;
  color: var(--text-muted);
}

.agent-title {
  margin: 0;
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0;
  line-height: 1.4;
}

/* 中性灰：这只是状态说明，不是警告，也不该抢主按钮的颜色 */
.agent-status {
  flex-shrink: 0;
  padding: 0 6px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-xs);
  background: var(--bg-surface);
  color: var(--text-secondary);
  font-size: 12px;
  font-weight: 600;
  line-height: 20px;
  white-space: nowrap;
}

/* 用 text-secondary 而不是 text-muted：后者在 bg-subtle 上对比度不到 AA */
.agent-text {
  margin-top: 8px;
  color: var(--text-secondary);
  font-size: 14px;
  line-height: 1.7;
}
.agent-text + .agent-text { margin-top: 4px; }

/* 按钮长成行内链接；焦点环沿用全局 :focus-visible，这里只给圆角 */
.agent-search {
  margin: 0 2px;
  padding: 0;
  border: 0;
  border-radius: var(--radius-xs);
  appearance: none;
  background: none;
  color: var(--brand-blue);
  font: inherit;
  font-weight: 600;
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-underline-offset: 3px;
  cursor: pointer;
  transition: color var(--dur) ease;
}
.agent-search:hover { color: var(--accent-hover); }

.agent-keys .ui-kbd {
  margin: 0 2px;
  vertical-align: 1px;
}

@media (max-width: 640px) {
  .agent-slot { padding: 14px 16px; }
}

/* 按输入方式而不是宽度隐藏快捷键：接了键盘的 iPad、窄窗口的桌面仍然看得到 */
@media (hover: none) and (pointer: coarse) {
  .agent-keys { display: none; }
}
</style>
