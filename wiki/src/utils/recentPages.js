// 最近浏览（仅存本机 localStorage），供搜索面板在没输入时给出「接着看」。
// 读写都可能在隐私模式下抛错，失败时当作没有记录。
const KEY = "wiki-recent-pages";
const MAX = 6;

export function readRecent() {
  try {
    const list = JSON.parse(localStorage.getItem(KEY) || "[]");
    return Array.isArray(list) ? list.filter((p) => p && typeof p.path === "string") : [];
  } catch {
    return [];
  }
}

export function pushRecent({ path, title, icon }) {
  if (!path) return;
  try {
    const list = readRecent().filter((p) => p.path !== path);
    list.unshift({ path, title: title || path, icon: icon || "" });
    localStorage.setItem(KEY, JSON.stringify(list.slice(0, MAX)));
  } catch {
    /* 存不下就算了 */
  }
}
