// 「减少动效」偏好的统一判断。
//
// global.css 里的 @media (prefers-reduced-motion: reduce) 只能管住 CSS 的
// scroll-behavior；JS 里显式写 behavior: 'smooth' 时浏览器不再看 CSS，照样平滑
// 滚动。所以凡是 scrollIntoView / scrollTo 都要用 scrollBehavior() 取值，不要写死
// 'smooth'。
//
// win 作为参数传入：单测里可以直接构造；node / 预渲染等没有 window 或
// matchMedia 的环境一律当作「不减少动效」，不会抛错。
const REDUCE_QUERY = '(prefers-reduced-motion: reduce)'

export function prefersReducedMotion(win = globalThis.window) {
  if (!win || typeof win.matchMedia !== 'function') return false
  try {
    return Boolean(win.matchMedia(REDUCE_QUERY)?.matches)
  } catch {
    return false
  }
}

/** 给 scrollIntoView / scrollTo 用的 behavior：要求减少动效时直接跳过去。 */
export function scrollBehavior(win = globalThis.window) {
  return prefersReducedMotion(win) ? 'auto' : 'smooth'
}
