// 标题锚点 id。MarkdownRenderer 用它给标题生成 id，搜索面板用它从「命中的小标题」
// 直接跳到对应段落，两边必须是同一个函数。
export function slugify(s) {
  return String(s)
    .trim()
    .toLowerCase()
    .replace(/\s+/g, "-")
    .replace(/[^\w一-龥-]/g, "")
    .replace(/-{2,}/g, "-")
    .replace(/^-+|-+$/g, "");
}
