<template>
  <component
    :is="component"
    class="wiki-icon"
    :size="size"
    :stroke-width="strokeWidth"
    aria-hidden="true"
    focusable="false"
  />
</template>

<script>
import { markRaw } from "vue";
import { resolveIcon } from "@/utils/icons.js";

// 页面 / 篇章图标：数据里存的 emoji 在这里换成对应的线性图标（见 utils/icons.js）
export default {
  name: "WikiIcon",
  props: {
    icon: { type: String, default: "" },
    title: { type: String, default: "" },
    category: { type: String, default: "" },
    kind: { type: String, default: "page" },
    size: { type: [Number, String], default: 16 },
    strokeWidth: { type: [Number, String], default: 1.75 },
  },
  computed: {
    component() {
      return markRaw(resolveIcon({ icon: this.icon, title: this.title, category: this.category, kind: this.kind }));
    },
  },
};
</script>

<style scoped>
.wiki-icon { flex-shrink: 0; }
</style>
