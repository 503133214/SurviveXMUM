<template>
  <div class="global-search" ref="root">
    <el-input
      ref="input"
      v-model="query"
      :placeholder="placeholder"
      class="search-input"
      clearable
      @focus="open = true"
      @keydown.down.prevent="move(1)"
      @keydown.up.prevent="move(-1)"
      @keydown.enter.prevent="choose()"
      @keydown.esc="close"
    >
      <template #prefix><el-icon><Search /></el-icon></template>
      <template #suffix>
        <span class="kbd" v-if="!open && !query">{{ shortcutLabel }}</span>
      </template>
    </el-input>

    <transition name="fade">
      <div v-if="open && query" class="search-panel">
        <div v-if="results.length === 0" class="search-empty">
          没有找到与 “{{ query }}” 相关的内容
        </div>
        <ul v-else class="result-list">
          <li
            v-for="(r, i) in results"
            :key="r.page.path"
            :class="['result-item', { active: i === activeIndex }]"
            @mouseenter="activeIndex = i"
            @mousedown.prevent="go(r.page)"
          >
            <span class="result-icon">{{ r.page.icon || categoryIcon(r.page.category) }}</span>
            <span class="result-body">
              <span class="result-title" v-html="highlight(r.page.title)"></span>
              <span class="result-sub">
                <span class="result-cat">{{ r.page.category || '首页' }}</span>
                <span v-if="r.matchedHeadings.length" class="result-heading">
                  › {{ r.matchedHeadings[0] }}
                </span>
              </span>
            </span>
          </li>
        </ul>
        <div class="search-foot">
          <span><kbd>↑</kbd><kbd>↓</kbd> 选择</span>
          <span><kbd>↵</kbd> 打开</span>
          <span><kbd>esc</kbd> 关闭</span>
        </div>
      </div>
    </transition>
  </div>
</template>

<script>
import { Search } from "@element-plus/icons-vue";
import { searchPages, categories } from "@/wiki";

export default {
  name: "GlobalSearch",
  components: { Search },
  props: {
    placeholder: { type: String, default: "搜索文档…" },
  },
  data() {
    const isMac = typeof navigator !== "undefined" && /Mac|iPhone|iPad/i.test(navigator.platform || navigator.userAgent || "");
    return { query: "", open: false, activeIndex: 0, isMac };
  },
  computed: {
    results() {
      return searchPages(this.query);
    },
    shortcutLabel() {
      return this.isMac ? "⌘K" : "Ctrl K";
    },
  },
  watch: {
    query() {
      this.activeIndex = 0;
      this.open = true;
    },
  },
  methods: {
    categoryIcon(category) {
      const cat = categories().find((c) => c.label === category || c.slug === category);
      return cat ? cat.icon : "📄";
    },
    highlight(text) {
      const q = this.query.trim();
      if (!q) return this.escape(text);
      const terms = q.split(/\s+/).filter(Boolean).map(this.escapeRe);
      const re = new RegExp(`(${terms.join("|")})`, "gi");
      return this.escape(text).replace(re, "<mark>$1</mark>");
    },
    escape(s) {
      return String(s).replace(/[&<>"]/g, (c) =>
        ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c])
      );
    },
    escapeRe(s) {
      return s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    },
    move(delta) {
      if (!this.results.length) return;
      this.open = true;
      this.activeIndex =
        (this.activeIndex + delta + this.results.length) % this.results.length;
    },
    choose() {
      const r = this.results[this.activeIndex];
      if (r) this.go(r.page);
    },
    go(page) {
      this.$router.push(`/docs/${page.path}`);
      this.close();
      this.query = "";
    },
    close() {
      this.open = false;
      this.$refs.input?.blur();
    },
    onClickOutside(e) {
      if (this.$refs.root && !this.$refs.root.contains(e.target)) this.open = false;
    },
    onKeydown(e) {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        this.$refs.input?.focus();
        this.open = true;
      }
    },
  },
  mounted() {
    document.addEventListener("click", this.onClickOutside);
    window.addEventListener("keydown", this.onKeydown);
  },
  beforeUnmount() {
    document.removeEventListener("click", this.onClickOutside);
    window.removeEventListener("keydown", this.onKeydown);
  },
};
</script>

<style scoped>
.global-search { position: relative; width: 100%; }

.search-input :deep(.el-input__wrapper) {
  border-radius: 20px;
  background: var(--bg-subtle);
  box-shadow: none;
  border: 1px solid transparent;
  transition: all 0.2s ease;
}
.search-input :deep(.el-input__wrapper:hover),
.search-input :deep(.el-input__wrapper.is-focus) {
  border-color: var(--brand);
  background: var(--bg-surface);
}
.kbd {
  font-size: 11px;
  color: var(--text-muted);
  border: 1px solid var(--border);
  border-radius: 4px;
  padding: 1px 5px;
}

.search-panel {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  right: 0;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-lg);
  overflow: hidden;
  z-index: 1100;
}
.search-empty { padding: 24px; text-align: center; color: var(--text-muted); font-size: 14px; }
.result-list { list-style: none; margin: 0; padding: 6px; max-height: 360px; overflow-y: auto; }
.result-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
}
.result-item.active { background: var(--bg-hover); }
.result-icon { font-size: 1.3rem; flex-shrink: 0; }
.result-body { display: flex; flex-direction: column; min-width: 0; }
.result-title { color: var(--text-primary); font-weight: 600; font-size: 14px; }
.result-title :deep(mark) {
  background: transparent;
  color: inherit;
  font-weight: 800;
  text-decoration: underline;
  text-decoration-thickness: 2px;
  text-underline-offset: 2px;
}
.result-sub { font-size: 12px; color: var(--text-muted); }
.result-heading { color: var(--brand); margin-left: 4px; }
.search-foot {
  display: flex;
  gap: 16px;
  padding: 8px 14px;
  border-top: 1px solid var(--border);
  font-size: 11px;
  color: var(--text-muted);
}
.search-foot kbd {
  border: 1px solid var(--border);
  border-radius: 3px;
  padding: 0 4px;
  margin-right: 2px;
}
.fade-enter-active, .fade-leave-active { transition: opacity 0.15s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
