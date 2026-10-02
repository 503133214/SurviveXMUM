// 首页「内容导航」里每个篇章先露哪几篇。
//
// 篇数不多（≤ max）的篇章全部列出，不出「展开」按钮——免得按钮点开只多一两篇。
// 篇数多的只露阅读量最高的 featured 篇，其余收进「展开其余 N 篇」。
// 露出的几篇和收起的部分都保持侧栏（清单）顺序：阅读量只决定「露哪几篇」，
// 不决定先后，展开后读起来和文档页侧栏一致，阅读量此消彼长也不会让行来回换位。
// 没有阅读量（新篇章、缓存里缺字段）一律按 0 算，自然退回侧栏顺序的前几篇。
export const NAV_MAX_SHOWN = 4
export const NAV_FEATURED = 3

export function splitFeatured(list, { max = NAV_MAX_SHOWN, featured = NAV_FEATURED } = {}) {
  const items = Array.isArray(list) ? list : []
  if (items.length <= max) return { featured: items.slice(), rest: [] }
  const picked = new Set(
    items
      .map((page, index) => ({ index, views: Number(page && page.viewCount) || 0 }))
      // 阅读量高的在前；同票按侧栏顺序，排序结果稳定
      .sort((a, b) => b.views - a.views || a.index - b.index)
      .slice(0, featured)
      .map((x) => x.index),
  )
  return {
    featured: items.filter((_, i) => picked.has(i)),
    rest: items.filter((_, i) => !picked.has(i)),
  }
}
