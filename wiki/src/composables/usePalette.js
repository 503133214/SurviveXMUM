// 全站搜索面板（⌘K）的开关。面板本身只在 App.vue 挂一次，顶栏搜索、首页问答区的
// 「搜索文档」、文档页和 404 页的入口，以及 ⌘K / 「/」 快捷键都通过这里打开它。
import { reactive } from "vue";

export const palette = reactive({ open: false, query: "" });

export function openPalette(query = "") {
  palette.query = query;
  palette.open = true;
}

export function closePalette() {
  palette.open = false;
}
