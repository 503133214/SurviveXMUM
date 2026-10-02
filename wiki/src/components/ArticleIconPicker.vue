<template>
  <!-- 浮层挂到 body，避免文章信息卡片的裁切和编辑器的层叠上下文遮住选项。 -->
  <el-popover
    v-model:visible="open"
    trigger="click"
    placement="bottom-start"
    :width="336"
    :show-arrow="false"
    :hide-after="0"
    :teleported="true"
    append-to="body"
    role="dialog"
    title="选择文章图标"
    transition="article-icon-popover"
    popper-class="article-icon-popover"
    @after-enter="focusChoice(focusedIndex)"
  >
    <template #reference>
      <button
        ref="trigger"
        type="button"
        class="article-icon-trigger"
        :class="{ 'has-icon': modelValue }"
        :aria-label="modelValue ? `更换文章图标，当前：${selectedLabel}` : '选择文章图标（可选）'"
        :aria-expanded="open"
        aria-haspopup="dialog"
        :title="modelValue ? `图标：${selectedLabel}` : '选择图标（可选）'"
        @keydown.esc.stop.prevent="close(true)"
      >
        <!-- 选择器只显示用户选中的值，不使用标题关键词替换它。 -->
        <WikiIcon v-if="modelValue" :icon="modelValue" :category="category" :size="20" />
        <Plus v-else :size="20" :stroke-width="1.75" aria-hidden="true" />
      </button>
    </template>

    <div ref="panel" class="article-icon-panel" @keydown.esc.stop.prevent="close(true)">
      <button type="button" class="article-icon-close" aria-label="关闭图标选择" @click="close(true)">
        <X :size="18" aria-hidden="true" />
      </button>
      <input
        ref="search"
        v-model="query"
        class="article-icon-search"
        type="search"
        aria-label="搜索图标"
        placeholder="搜索图标名称"
        @keydown.down.prevent="focusChoice(0)"
      />
      <div class="article-icon-options" role="group" aria-label="可选图标">
        <button
          v-for="(choice, index) in filteredChoices"
          :key="choice.icon"
          type="button"
          class="article-icon-option"
          :class="{ selected: isSelected(choice.icon) }"
          :aria-label="choice.label"
          :aria-pressed="isSelected(choice.icon)"
          :tabindex="index === focusedIndex ? 0 : -1"
          @focus="focusedIndex = index"
          @click="select(choice.icon)"
          @keydown="moveChoice($event, index)"
        >
          <WikiIcon :icon="choice.icon" :size="19" />
          <span>{{ choice.label }}</span>
        </button>
        <p v-if="!filteredChoices.length" class="article-icon-empty" role="status">没有匹配的图标，试试其他名称。</p>
      </div>
      <div class="article-icon-footer">
        <span>{{ modelValue ? `当前：${selectedLabel}` : '图标可选，不影响提交' }}</span>
        <button v-if="modelValue" type="button" class="article-icon-clear" @click="select('')">清除图标</button>
      </div>
    </div>
  </el-popover>
</template>

<script>
import { nextTick } from 'vue'
import { Plus, X } from 'lucide-vue-next'
import WikiIcon from '@/components/WikiIcon.vue'
import { ICON_CHOICES, iconLabel, normalizeEmoji } from '@/utils/icons.js'

export default {
  name: 'ArticleIconPicker',
  components: { WikiIcon, Plus, X },
  props: {
    modelValue: { type: String, default: '' },
    category: { type: String, default: '' },
  },
  emits: ['update:modelValue'],
  data() {
    return { open: false, query: '', focusedIndex: 0 }
  },
  computed: {
    selectedLabel() { return iconLabel(this.modelValue) },
    filteredChoices() {
      const query = this.query.trim()
      return ICON_CHOICES.map(icon => ({ icon, label: iconLabel(icon) }))
        .filter(choice => !query || choice.label.includes(query))
    },
  },
  watch: {
    query() { this.focusedIndex = 0 },
    open(value) {
      if (value) {
        this.query = ''
        const selected = ICON_CHOICES.findIndex(icon => this.isSelected(icon))
        this.focusedIndex = Math.max(0, selected)
      }
    },
  },
  mounted() {
    document.addEventListener('focusin', this.onFocusOutside)
  },
  beforeUnmount() {
    document.removeEventListener('focusin', this.onFocusOutside)
  },
  methods: {
    isSelected(icon) { return normalizeEmoji(this.modelValue) === normalizeEmoji(icon) },
    async close(restoreFocus = false) {
      this.open = false
      if (restoreFocus) {
        await nextTick()
        this.$refs.trigger?.focus({ preventScroll: true })
      }
    },
    select(icon) {
      this.$emit('update:modelValue', icon)
      this.close(true)
    },
    onFocusOutside(event) {
      if (this.open && !this.$refs.panel?.contains(event.target) && !this.$refs.trigger?.contains(event.target)) {
        this.close()
      }
    },
    focusChoice(index) {
      const options = this.$refs.panel?.querySelectorAll('.article-icon-option')
      if (!options?.length) return
      this.focusedIndex = Math.max(0, Math.min(index, options.length - 1))
      options[this.focusedIndex].focus()
    },
    moveChoice(event, index) {
      // 方向键在六列选项间移动；Tab 只经过当前项，避免逐个遍历全部图标。
      const offsets = { ArrowRight: 1, ArrowLeft: -1, ArrowDown: 6, ArrowUp: -6 }
      let next = index
      if (event.key in offsets) next += offsets[event.key]
      else if (event.key === 'Home') next = 0
      else if (event.key === 'End') next = this.filteredChoices.length - 1
      else return
      event.preventDefault()
      this.focusChoice(next)
    },
  },
}
</script>

<style scoped>
.article-icon-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 42px;
  width: 42px;
  min-height: 42px;
  padding: 0;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--bg-surface);
  color: var(--text-secondary);
  cursor: pointer;
  transition: background var(--dur), color var(--dur), border-color var(--dur);
}
.article-icon-trigger:hover { background: var(--bg-hover); }
.article-icon-trigger.has-icon { color: var(--accent); border-color: var(--accent); }
.article-icon-trigger:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
</style>

<style>
/* Teleport 内容不依赖父页面的 scoped 样式，主题继续从根节点的令牌继承。 */
body .el-popover.article-icon-popover {
  box-sizing: border-box;
  max-width: calc(100vw - 24px);
  padding: 12px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--bg-surface);
  color: var(--text-body);
  box-shadow: var(--shadow-md);
  font: var(--fs-sm)/var(--lh-ui) var(--font-sans);
}
body .el-popover.article-icon-popover .el-popover__title {
  margin-bottom: 12px;
  padding-right: 32px;
  color: var(--text-primary);
  font-size: var(--fs-ui);
  font-weight: 600;
  line-height: var(--lh-ui);
}
body .article-icon-close {
  position: absolute;
  top: 8px;
  right: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
}
body .article-icon-search {
  box-sizing: border-box;
  width: 100%;
  min-height: 36px;
  margin-bottom: 8px;
  padding: 6px 8px;
  border: 1px solid var(--border-strong);
  border-radius: var(--radius-sm);
  background: var(--bg-page);
  color: var(--text-body);
  font: inherit;
}
body .article-icon-search::placeholder { color: var(--text-muted); }
body .article-icon-options {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 4px;
  max-height: min(320px, 45vh);
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 3px;
}
body .article-icon-option {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-width: 0;
  min-height: 54px;
  padding: 5px 0;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  font: var(--fs-xs)/var(--lh-tight) var(--font-sans);
  cursor: pointer;
  transition: background var(--dur), color var(--dur);
}
body .article-icon-option:hover,
body .article-icon-close:hover { background: var(--bg-hover); color: var(--text-primary); }
body .article-icon-option.selected { border-color: var(--accent); background: var(--accent-soft); color: var(--accent); }
body .article-icon-empty { grid-column: 1 / -1; padding: 16px 0; margin: 0; color: var(--text-muted); }
body .article-icon-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--border);
  color: var(--text-muted);
  font-size: var(--fs-xs);
}
body .article-icon-clear {
  min-height: 30px;
  padding: 4px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--accent);
  font: inherit;
  cursor: pointer;
}
body .article-icon-clear:hover { background: var(--accent-soft); }
body .article-icon-panel :is(button, input):focus-visible { outline: 2px solid var(--accent); outline-offset: 1px; }
@media (prefers-reduced-motion: reduce) {
  body .article-icon-trigger,
  body .article-icon-option { transition: none; }
}
</style>
