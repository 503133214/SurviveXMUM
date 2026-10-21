<!--
  站内问答助手（内测）。HomePage 只在当前用户是超级管理员时把它挂进 AgentSlot；后端同样只放行
  AGENT_ACCESS_ROLE 允许的角色，前端的判断只决定显示什么，不是权限边界。

  一轮问答：创建 run（requestId 幂等，没连上时可原样重试）→ 用 fetch 订阅事件流，只拿来显示进度，
  断线按游标续传 → 流结束后读快照渲染结果（回答与来源、澄清问题或失败原因）。快照是唯一依据，
  刷新页面后也按快照恢复：本轮对话（conversationId 与各轮 runId）按用户记在 sessionStorage，关掉标签页即丢弃。
  接口与事件约定见 AGENTS.md「联调站内问答 Agent」；解析与文案规则在 utils/agentStream.js、utils/agentAnswer.js（有单测）。
-->
<template>
  <div class="wa">
    <p class="wa-note">内测中，目前只对超级管理员开放。回答只依据站内文档整理，可能不完整，请以引用的原文为准。</p>

    <ol v-if="turns.length" class="wa-turns" aria-live="polite" :aria-busy="busy ? 'true' : 'false'">
      <li v-for="turn in turns" :key="turn.key" class="wa-turn">
        <p class="wa-question">{{ turn.question }}</p>
        <p v-if="turn.reply" class="wa-question">补充：{{ turn.reply }}</p>

        <p v-if="turn.progress" class="wa-progress">{{ turn.progress }}</p>

        <template v-if="turn.result && turn.result.type === 'answer'">
          <div class="wa-answer">
            <MarkdownRenderer :content="answerMarkdown(turn)" />
          </div>
          <p v-if="turn.result.reduced" class="wa-meta">受预算限制，这次用了精简方式回答，内容可能不完整。</p>
          <div v-if="unresolved(turn).length" class="wa-unresolved">
            <p class="wa-meta">站内资料没有覆盖：</p>
            <ul>
              <li v-for="(item, i) in unresolved(turn)" :key="i">{{ item }}</li>
            </ul>
          </div>
        </template>

        <div v-else-if="turn.result && turn.result.type === 'notice'" class="wa-callout">
          <p class="wa-callout-title">{{ noticeTitle(turn.result.reason) }}</p>
          <p>{{ turn.result.text }}</p>
        </div>

        <div v-if="turn.waiting" class="wa-callout">
          <p class="wa-callout-title">需要补充一点信息</p>
          <p>{{ turn.waiting.question }}</p>
          <p class="wa-meta">在下面的输入框里补充后点「回答」；不想继续就点「结束提问」。</p>
        </div>

        <div v-if="turn.error" class="wa-callout wa-callout-error">
          <p>{{ turn.error.text }}</p>
          <p v-if="turn.error.login || turn.error.search || canRetry(turn) || turn.error.reconnect" class="wa-inline-actions">
            <router-link v-if="turn.error.login" class="wa-link" :to="{ path: '/login', query: { redirect: '/' } }">去登录</router-link>
            <button v-if="canRetry(turn)" type="button" class="wa-link" @click="create(turn)">重试</button>
            <button v-if="turn.error.reconnect" type="button" class="wa-link" @click="reconnect(turn)">重新连接</button>
            <button v-if="turn.error.search" type="button" class="wa-link" aria-haspopup="dialog" @click="openPalette()">搜索文档</button>
          </p>
        </div>

        <div v-if="sources(turn).length" class="wa-sources">
          <p class="wa-sources-title">参考资料</p>
          <ol class="wa-source-list">
            <li v-for="source in sources(turn)" :key="source.key">
              <router-link class="wa-source" :to="source.href">
                <span class="wa-source-n">[{{ source.n }}]</span>
                <span class="wa-source-title">{{ source.title }}</span>
                <span v-if="source.section" class="wa-source-section">{{ source.section }}</span>
              </router-link>
            </li>
          </ol>
        </div>

        <div v-if="canRate(turn)" class="wa-feedback">
          <p v-if="turn.feedback.sent" class="wa-meta">已收到反馈，谢谢。</p>
          <template v-else-if="turn.feedback.askReason">
            <span class="wa-meta">哪里不好？</span>
            <button
              v-for="reason in reasons"
              :key="reason.value"
              type="button"
              class="wa-chip"
              :disabled="turn.feedback.sending"
              @click="rate(turn, 'down', reason.value)"
            >{{ reason.label }}</button>
            <button type="button" class="wa-chip" :disabled="turn.feedback.sending" @click="rate(turn, 'down', null)">跳过</button>
          </template>
          <template v-else>
            <span class="wa-meta">这个回答有帮助吗？</span>
            <button type="button" class="wa-chip" :disabled="turn.feedback.sending" @click="rate(turn, 'up', null)">
              <ThumbsUp :size="14" :stroke-width="1.75" aria-hidden="true" />有帮助
            </button>
            <button type="button" class="wa-chip" :disabled="turn.feedback.sending" @click="turn.feedback.askReason = true">
              <ThumbsDown :size="14" :stroke-width="1.75" aria-hidden="true" />没帮助
            </button>
          </template>
          <p v-if="turn.feedback.error" class="wa-meta">{{ turn.feedback.error }}</p>
        </div>
      </li>
    </ol>

    <form class="wa-form" @submit.prevent="submit">
      <textarea
        ref="input"
        v-model="draft"
        class="wa-input"
        rows="2"
        :aria-labelledby="labelledby"
        :aria-describedby="inputError ? errorId : undefined"
        :aria-invalid="inputError ? 'true' : undefined"
        :placeholder="placeholder"
        :disabled="!!runningTurn"
        @keydown="onKeydown"
        @input="inputError = ''"
      ></textarea>
      <div class="wa-actions">
        <p v-if="inputError" :id="errorId" class="wa-input-error" role="alert">{{ inputError }}</p>
        <button v-if="turns.length && !activeTurn" type="button" class="wa-btn" @click="newConversation">新对话</button>
        <button v-if="waitingTurn" type="button" class="wa-btn" :disabled="sending" @click="endClarification">结束提问</button>
        <button
          v-if="runningTurn"
          type="button"
          class="wa-btn"
          :disabled="cancelling || !runningTurn.runId"
          @click="stop"
        >{{ cancelling ? "正在停止" : "停止" }}</button>
        <button v-else type="submit" class="wa-btn wa-btn-primary" :disabled="sending">{{ waitingTurn ? "回答" : "提问" }}</button>
      </div>
    </form>
  </div>
</template>

<script>
import { useId } from "vue";
import { ThumbsDown, ThumbsUp } from "lucide-vue-next";
import MarkdownRenderer from "@/components/MarkdownRenderer.vue";
import { openPalette } from "@/composables/usePalette.js";
import { useUserStore } from "@/store/userStore.js";
import {
  agentCancelRun,
  agentCreateRun,
  agentGetRun,
  agentOpenEvents,
  agentResumeRun,
  agentSendFeedback,
} from "@/net/index.js";
import {
  createSseParser,
  isClosingEvent,
  isSettledStatus,
  nextCursor,
  parseFrameData,
  reconnectDelay,
} from "@/utils/agentStream.js";
import {
  FEEDBACK_REASONS,
  describeAgentFailure,
  noticeTitle,
  prepareAnswerMarkdown,
  questionError,
  runErrorText,
  sourceItems,
  unresolvedItems,
} from "@/utils/agentAnswer.js";

// 从发起 fetch 起这么久没收到任何字节（包括 `: ping` 心跳）就断开重连：
// 全站同时推送的流有上限，超出的连响应头都不回，不主动断开会一直卡住。
const IDLE_ABORT_MS = 45000;
// 连续这么多次没连上就停下来让用户手动重连；run 在服务端照常执行，不受影响。
const MAX_RECONNECTS = 8;
const STORAGE_PREFIX = "agent-trial:";
const MAX_SAVED_TURNS = 10;
const EXPIRED_REPLY = { text: "补充说明已超时，请重新提问。" };

function newRequestId() {
  const c = globalThis.crypto;
  if (c && typeof c.randomUUID === "function") return c.randomUUID();
  const bytes = new Uint8Array(16);
  c.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

// 把 net/index.js 的回调式接口包成 Promise：成功给 { data }，失败给 { failure }，从不 reject。
function call(fn, ...args) {
  return new Promise((resolve) => {
    fn(...args, (data) => resolve({ data }), (message, httpStatus, code) => resolve({ failure: { message, httpStatus, code } }));
  });
}

// 事件流没拿到 text/event-stream 时按 JSON 读错误：网关的 {code, message}，或 Agent 透传的
// {error: {code, message}} / {detail}；读不出（Agent 未处理的异常是纯文本 500）就只按 HTTP 状态处理。
async function readStreamFailure(res) {
  let body = null;
  try {
    body = await res.json();
  } catch {
    body = null;
  }
  const message = body
    ? body.message || (body.error && body.error.message) || (typeof body.detail === "string" ? body.detail : "")
    : "";
  return { httpStatus: res.status, code: body && typeof body.code === "number" ? body.code : null, message };
}

export default {
  name: "WikiAgent",
  components: { MarkdownRenderer, ThumbsDown, ThumbsUp },
  props: {
    // AgentSlot 的标题 id：输入框用它做无障碍名称，区域名称前后一致
    labelledby: { type: String, default: undefined },
  },
  setup() {
    return { errorId: `wa-error-${useId()}` };
  },
  data() {
    return {
      draft: "",
      inputError: "",
      conversationId: null,
      turns: [],
      sending: false,
      cancelling: false,
      reasons: FEEDBACK_REASONS,
    };
  },
  computed: {
    storageKey() {
      const id = useUserStore().userInfo?.id;
      return id ? `${STORAGE_PREFIX}${id}` : "";
    },
    lastTurn() {
      return this.turns.length ? this.turns[this.turns.length - 1] : null;
    },
    runningTurn() {
      const turn = this.lastTurn;
      return turn && (turn.phase === "creating" || turn.phase === "running") ? turn : null;
    },
    waitingTurn() {
      const turn = this.lastTurn;
      return turn && turn.phase === "waiting" ? turn : null;
    },
    activeTurn() {
      return this.runningTurn || this.waitingTurn;
    },
    busy() {
      return !!this.runningTurn;
    },
    placeholder() {
      return this.waitingTurn ? "补充说明，例如你的身份或目前所处的阶段" : "问一个关于在厦大马校学习和生活的问题";
    },
  },
  created() {
    // 不放进 data：这些只在流程里用，不需要响应式
    this.unmounted = false;
    this.streamSeq = 0;
    this.controller = null;
  },
  mounted() {
    this.restore();
  },
  beforeUnmount() {
    this.unmounted = true;
    this.streamSeq++;
    this.abortStream();
  },
  methods: {
    openPalette,
    noticeTitle,

    answerMarkdown(turn) {
      return prepareAnswerMarkdown(turn.result.text, turn.result.sources);
    },
    sources(turn) {
      return turn.result ? sourceItems(turn.result.sources) : [];
    },
    unresolved(turn) {
      return unresolvedItems(turn.result && turn.result.unresolved);
    },
    canRate(turn) {
      if (turn.phase !== "done" || !turn.result || !turn.runId) return false;
      // 资料不足的点踩会进知识缺口队列，同样值得收集；用户自己结束的澄清就不必评价了
      return turn.result.type === "answer" || (turn.result.type === "notice" && turn.result.reason !== "clarification_declined");
    },
    // 只有创建没成功（还没有 runId）时才能原样重试；同一个 requestId 不会重复建 run
    canRetry(turn) {
      return turn.phase === "failed" && !turn.runId && !!turn.requestId && !!(turn.error && turn.error.retry);
    },

    addTurn(question) {
      this.turns.push({
        key: `${Date.now()}-${this.turns.length}`,
        question,
        reply: "",
        runId: null,
        requestId: newRequestId(),
        phase: "creating",
        progress: "正在提交",
        cursor: 0,
        result: null,
        waiting: null,
        error: null,
        feedback: { askReason: false, sending: false, sent: false, error: "" },
      });
      // 要改响应式代理才会刷新界面；直接改刚 push 进去的原对象不会触发更新
      return this.turns[this.turns.length - 1];
    },

    onKeydown(event) {
      // Enter 提交、Shift+Enter 换行；输入法组字时的 Enter 是确认候选词，不能提交
      if (event.key === "Enter" && !event.shiftKey && !event.isComposing && event.keyCode !== 229) {
        event.preventDefault();
        this.submit();
      }
    },

    submit() {
      if (this.runningTurn || this.sending) return;
      if (this.waitingTurn) {
        this.reply(this.waitingTurn);
        return;
      }
      const error = questionError(this.draft);
      if (error) {
        this.inputError = error;
        return;
      }
      const turn = this.addTurn(this.draft.trim());
      this.draft = "";
      this.inputError = "";
      this.create(turn);
    },

    async create(turn) {
      turn.phase = "creating";
      turn.error = null;
      turn.progress = "正在提交";
      const payload = { requestId: turn.requestId, kind: "ask", message: turn.question };
      // 不传 scope 就是全站（不能传 null，会 422）；接着问时带上同一个会话
      if (this.conversationId) payload.conversationId = this.conversationId;
      const { data, failure } = await call(agentCreateRun, payload);
      if (this.unmounted) return;
      if (failure) {
        this.fail(turn, describeAgentFailure(failure));
        return;
      }
      turn.runId = data.runId;
      if (data.conversationId) this.conversationId = data.conversationId;
      turn.phase = "running";
      turn.progress = "排队中";
      this.save();
      this.follow(turn);
    },

    fail(turn, failure) {
      turn.phase = "failed";
      turn.progress = "";
      turn.error = failure;
      this.save();
    },

    reconnect(turn) {
      turn.error = null;
      turn.phase = "running";
      turn.progress = "正在重新连接";
      this.follow(turn);
    },

    // 每个 run 只开一条流：新开的 follow 让旧循环在下一次检查时自行退出
    async follow(turn) {
      const seq = ++this.streamSeq;
      this.abortStream();
      let failures = 0;
      while (!this.unmounted && seq === this.streamSeq && turn.phase === "running") {
        const outcome = await this.listen(turn);
        if (this.unmounted || seq !== this.streamSeq) return;
        if (outcome.failure) {
          const failure = describeAgentFailure(outcome.failure);
          // 401 / 403 / 404 这类答复重连也不会变
          if (!failure.retry) {
            this.fail(turn, failure);
            return;
          }
        }
        // 流结束了（收到关流事件，或被网关定期切断、空闲超时）：一律以快照为准，决定收尾还是续传。
        // 直接重连会死循环：run 已结束且游标追平时，服务端一个事件都不发就关流。
        const { data, failure } = await call(agentGetRun, turn.runId);
        if (this.unmounted || seq !== this.streamSeq) return;
        if (data && isSettledStatus(data.status)) {
          this.applySnapshot(turn, data);
          return;
        }
        if (failure && !describeAgentFailure(failure).retry) {
          this.fail(turn, describeAgentFailure(failure));
          return;
        }
        failures = outcome.received ? 0 : failures + 1;
        if (failures > MAX_RECONNECTS) {
          this.fail(turn, { text: "连接中断了。问题仍在后台处理，可以稍后重新连接查看结果。", reconnect: true });
          return;
        }
        await sleep(outcome.received ? 300 : reconnectDelay(failures));
      }
    },

    async listen(turn) {
      const controller = new AbortController();
      this.controller = controller;
      let idle = null;
      const resetIdle = () => {
        clearTimeout(idle);
        idle = setTimeout(() => controller.abort(), IDLE_ABORT_MS);
      };
      resetIdle();
      let received = false;
      try {
        const res = await agentOpenEvents(turn.runId, turn.cursor, controller.signal);
        const type = res.headers.get("content-type") || "";
        // 先看 res.ok，再看是否以 text/event-stream 开头（实际带 ; charset=utf-8，不能全等比较）
        if (!res.ok || !type.startsWith("text/event-stream")) {
          return { received, failure: await readStreamFailure(res) };
        }
        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        const parser = createSseParser();
        for (;;) {
          const { value, done } = await reader.read();
          if (done) return { received };
          resetIdle();
          for (const frame of parser.push(decoder.decode(value, { stream: true }))) {
            received = true;
            turn.cursor = nextCursor(turn.cursor, frame);
            this.onFrame(turn, frame);
            if (isClosingEvent(frame.event)) return { received };
          }
        }
      } catch {
        // 空闲超时、网络中断、组件卸载都会走到这里，交给 follow 按快照决定
        return { received };
      } finally {
        clearTimeout(idle);
        // 收尾时关掉连接，不留一个没人读的流占着全站的推送名额
        controller.abort();
        if (this.controller === controller) this.controller = null;
      }
    },

    onFrame(turn, frame) {
      const data = parseFrameData(frame);
      if (!data) return;
      if (frame.event === "status") {
        // 最新一条还是 QUEUED（没有 message）时显示「排队中」；之后的 message 是给用户看的中文，只显示最新一条
        if (data.message) turn.progress = data.message;
        else if (data.status === "QUEUED") turn.progress = "排队中";
      } else if (frame.event === "snapshot" && isSettledStatus(data.status)) {
        // 游标早于重放窗口时服务端先给整份快照
        this.applySnapshot(turn, data);
      }
      // sources / delta / done / error / clarification_required 的内容都以流结束后的快照为准
    },

    applySnapshot(turn, snapshot) {
      turn.cursor = Math.max(turn.cursor, Number(snapshot.lastEventSeq) || 0);
      turn.progress = "";
      if (snapshot.status === "SUCCEEDED") {
        turn.phase = "done";
        turn.result = snapshot.result || null;
        turn.waiting = null;
        turn.error = null;
      } else if (snapshot.status === "WAITING_USER") {
        const waiting = snapshot.waiting || {};
        if (waiting.type && waiting.type !== "clarification") {
          // 编辑确认等其它等待类型不在问答助手的范围内
          this.fail(turn, { text: "这个任务需要在编辑器里处理，问答助手暂不支持。" });
          return;
        }
        turn.phase = "waiting";
        turn.waiting = { interruptId: waiting.interruptId, question: waiting.question || "可以再具体说明一下吗？" };
        turn.error = null;
        this.$nextTick(() => this.$refs.input && this.$refs.input.focus());
      } else if (snapshot.status === "FAILED" || snapshot.status === "CANCELLED" || snapshot.status === "EXPIRED") {
        turn.phase = "failed";
        turn.waiting = null;
        turn.error = { text: runErrorText(snapshot.status, snapshot.error), search: snapshot.status === "FAILED" };
      } else {
        turn.phase = "running";
        turn.progress = snapshot.status === "QUEUED" ? "排队中" : "正在处理";
      }
      this.save();
    },

    async reply(turn) {
      const error = questionError(this.draft, "请输入补充说明");
      if (error) {
        this.inputError = error;
        return;
      }
      const message = this.draft.trim();
      this.sending = true;
      const { failure } = await call(agentResumeRun, turn.runId, {
        interruptId: turn.waiting.interruptId,
        decision: "reply",
        message,
      });
      this.sending = false;
      if (this.unmounted) return;
      if (failure) {
        // 澄清过期后 resume 返回 410 或 409，都按过期处理，引导用户重新提问
        if (failure.httpStatus === 409 || failure.httpStatus === 410) {
          turn.waiting = null;
          this.fail(turn, EXPIRED_REPLY);
        } else {
          this.inputError = describeAgentFailure(failure).text;
        }
        return;
      }
      this.draft = "";
      turn.reply = message;
      turn.waiting = null;
      turn.phase = "running";
      turn.progress = "排队中";
      this.save();
      this.follow(turn);
    },

    async endClarification() {
      const turn = this.waitingTurn;
      if (!turn || this.sending) return;
      this.sending = true;
      const { failure } = await call(agentResumeRun, turn.runId, {
        interruptId: turn.waiting.interruptId,
        decision: "reject",
      });
      this.sending = false;
      if (this.unmounted) return;
      turn.waiting = null;
      if (failure) {
        const expired = failure.httpStatus === 409 || failure.httpStatus === 410;
        this.fail(turn, expired ? EXPIRED_REPLY : describeAgentFailure(failure));
        return;
      }
      turn.phase = "running";
      turn.progress = "正在结束";
      this.follow(turn);
    },

    async stop() {
      const turn = this.runningTurn;
      if (!turn || !turn.runId || this.cancelling) return;
      this.cancelling = true;
      const { data, failure } = await call(agentCancelRun, turn.runId);
      this.cancelling = false;
      if (this.unmounted) return;
      if (data && isSettledStatus(data.status)) {
        // 排队中的 run 当场变 CANCELLED（也可能刚好已经答完）：不用再听流
        this.streamSeq++;
        this.abortStream();
        this.applySnapshot(turn, data);
      } else if (failure) {
        turn.progress = describeAgentFailure(failure).text;
      } else {
        // 执行中的 run 只打标记，worker 约 10 秒内发现才停，期间仍可能以回答结束；继续听流
        turn.progress = "正在停止…";
      }
    },

    async rate(turn, rating, reason) {
      turn.feedback.sending = true;
      turn.feedback.error = "";
      const payload = { runId: turn.runId, rating };
      if (reason) payload.reason = reason;
      const { failure } = await call(agentSendFeedback, payload);
      turn.feedback.sending = false;
      if (failure) {
        turn.feedback.error = describeAgentFailure(failure).text;
        return;
      }
      turn.feedback.sent = true;
      turn.feedback.askReason = false;
    },

    newConversation() {
      this.streamSeq++;
      this.abortStream();
      this.turns = [];
      this.conversationId = null;
      this.inputError = "";
      this.save();
      this.$nextTick(() => this.$refs.input && this.$refs.input.focus());
    },

    abortStream() {
      if (this.controller) {
        this.controller.abort();
        this.controller = null;
      }
    },

    save() {
      if (!this.storageKey) return;
      try {
        const turns = this.turns
          .filter((turn) => turn.runId)
          .slice(-MAX_SAVED_TURNS)
          .map((turn) => ({ runId: turn.runId, question: turn.question, reply: turn.reply }));
        if (turns.length) {
          sessionStorage.setItem(this.storageKey, JSON.stringify({ conversationId: this.conversationId, turns }));
        } else {
          sessionStorage.removeItem(this.storageKey);
        }
      } catch {
        // 隐私模式等存不了就不记：只是刷新后看不到这轮对话
      }
    },

    async restore() {
      if (!this.storageKey) return;
      let saved = null;
      try {
        saved = JSON.parse(sessionStorage.getItem(this.storageKey) || "null");
      } catch {
        saved = null;
      }
      if (!saved || !Array.isArray(saved.turns)) return;
      this.conversationId = typeof saved.conversationId === "string" ? saved.conversationId : null;
      const restored = saved.turns
        .filter((item) => item && typeof item.runId === "string" && typeof item.question === "string")
        .map((item) => {
          const turn = this.addTurn(item.question);
          turn.runId = item.runId;
          turn.requestId = null;
          turn.reply = typeof item.reply === "string" ? item.reply : "";
          turn.phase = "running";
          turn.progress = "正在加载";
          return turn;
        });
      for (const turn of restored) {
        const { data, failure } = await call(agentGetRun, turn.runId);
        if (this.unmounted) return;
        if (data && isSettledStatus(data.status)) this.applySnapshot(turn, data);
        else if (data) this.follow(turn);
        else this.fail(turn, describeAgentFailure(failure));
      }
    },
  },
};
</script>

<style scoped>
.wa {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 10px;
}

/* 用 text-secondary 而不是 text-muted：说明文字要在两套主题下都达到 AA 对比度 */
.wa-note,
.wa-meta,
.wa-progress {
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  line-height: var(--lh-ui);
}
.wa-progress { font-size: var(--fs-ui); }

.wa-turns {
  display: flex;
  flex-direction: column;
  gap: 16px;
  list-style: none;
}
.wa-turn {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--border);
}
.wa-turn:first-child {
  padding-top: 0;
  border-top: 0;
}

/* 用户自己的话按纯文本显示（保留换行），不经 Markdown */
.wa-question {
  align-self: flex-start;
  max-width: 100%;
  padding: 6px 10px;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--bg-subtle);
  color: var(--text-primary);
  font-size: var(--fs-ui);
  line-height: var(--lh-ui);
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

/* 回答放在首页的小面板里：正文收紧到编辑器预览的尺寸，标题不再加分隔线和大段留白 */
.wa-answer :deep(.markdown-body) {
  font-size: 15px;
  line-height: 1.75;
}
.wa-answer :deep(.markdown-body :is(h1, h2, h3, h4)) {
  margin: 1.2em 0 0.5em;
  padding-top: 0;
  border-top: 0;
  font-size: 1rem;
}
.wa-answer :deep(.markdown-body p) { margin: 0.6em 0; }

.wa-unresolved ul {
  margin-top: 4px;
  padding-left: 1.4em;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  line-height: var(--lh-ui);
}

/* 资料不足、澄清、失败都是一段说明：左侧细色条区分，不用整块底色 */
.wa-callout {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 2px 0 2px 12px;
  border-left: 3px solid var(--border-strong);
  color: var(--text-body);
  font-size: var(--fs-ui);
  line-height: var(--lh-ui);
}
.wa-callout-error { border-left-color: var(--danger); }
.wa-callout-title {
  color: var(--text-primary);
  font-weight: 600;
}
.wa-inline-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.wa-link {
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
.wa-link:hover { color: var(--accent-hover); }

.wa-sources-title {
  margin-bottom: 4px;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  font-weight: 600;
}
.wa-source-list {
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  list-style: none;
}
.wa-source-list li + li { border-top: 1px solid var(--border); }
.wa-source {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 2px 8px;
  min-height: 40px;
  padding: 9px 12px;
  color: var(--text-body);
  font-size: var(--fs-ui);
  line-height: 1.5;
  transition: background-color var(--dur) ease;
}
.wa-source:hover {
  background: var(--bg-subtle);
  text-decoration: none;
}
/* 行与行紧挨着，焦点环向内收，免得被边框裁掉 */
.wa-source:focus-visible { outline-offset: -2px; }
.wa-source-n {
  flex-shrink: 0;
  color: var(--brand-blue);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
.wa-source-title {
  min-width: 0;
  color: var(--text-primary);
  font-weight: 500;
  overflow-wrap: anywhere;
}
.wa-source-section {
  min-width: 0;
  color: var(--text-secondary);
  font-size: var(--fs-sm);
  overflow-wrap: anywhere;
}

.wa-feedback {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.wa-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 28px;
  padding: 0 10px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  color: var(--text-body);
  font: inherit;
  font-size: var(--fs-sm);
  cursor: pointer;
  transition: background-color var(--dur) ease, border-color var(--dur) ease;
}
.wa-chip:hover:not(:disabled) {
  border-color: var(--text-muted);
  background: var(--bg-subtle);
}
.wa-chip:disabled {
  cursor: default;
  opacity: 0.6;
}

.wa-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.wa-input {
  box-sizing: border-box;
  width: 100%;
  min-height: 64px;
  padding: 8px 10px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  color: var(--text-primary);
  font: inherit;
  font-size: var(--fs-ui);
  line-height: var(--lh-ui);
  resize: vertical;
  transition: border-color var(--dur) ease;
}
.wa-input::placeholder { color: var(--text-muted); }
.wa-input:hover:not(:disabled) { border-color: var(--text-muted); }
.wa-input:disabled {
  background: var(--bg-subtle);
  color: var(--text-secondary);
  cursor: not-allowed;
}

.wa-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}
.wa-input-error {
  margin-right: auto;
  color: var(--danger);
  font-size: var(--fs-sm);
}
.wa-btn {
  height: 32px;
  padding: 0 14px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  color: var(--text-primary);
  font: inherit;
  font-size: var(--fs-ui);
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--dur) ease, border-color var(--dur) ease, color var(--dur) ease;
}
.wa-btn:hover:not(:disabled) { border-color: var(--text-muted); }
.wa-btn:disabled {
  cursor: default;
  opacity: 0.6;
}
.wa-btn-primary {
  border-color: transparent;
  background: var(--accent);
  color: var(--accent-contrast);
}
.wa-btn-primary:hover:not(:disabled) {
  border-color: transparent;
  background: var(--accent-hover);
}

/* 手机上按钮加高到 40px 并平分一行，手指点得准 */
@media (max-width: 640px) {
  .wa-actions .wa-btn {
    flex: 1;
    height: 40px;
  }
  .wa-input-error { flex-basis: 100%; }
}
</style>
