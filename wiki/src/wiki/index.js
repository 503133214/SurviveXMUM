// Front-end facade over the wiki content API (backend: GET /wiki/manifest, /wiki/page).
// Content now lives in the database and is fetched at runtime (no build-time wiki.data.js).
//
// `tree` and `pages` are reactive arrays mutated IN PLACE by loadManifest(), so every
// consumer that imported them keeps a valid live reference.
import { reactive } from 'vue'
import axios from 'axios'

export const tree = reactive([])
export const pages = reactive([])
export const state = reactive({ home: null, generatedAt: null, loaded: false, loading: false })

// The home doc (root README) path is fixed.
export const HOME_PATH = 'README'

// 仓库地址（顶栏 GitHub 入口用）。曾经还有个 EDIT_BASE 指向 wiki/public/docs 里的
// Markdown 源文件，内容迁进数据库、目录删除后已无意义，随之移除。
export const REPO = 'https://github.com/503133214/SurviveXMUM'

const CACHE_KEY = 'wiki_manifest_v1'

function applyManifest(m) {
  tree.splice(0, tree.length, ...(m.tree || []))
  pages.splice(0, pages.length, ...(m.pages || []))
  state.home = m.home || null
  state.generatedAt = m.generatedAt || null
  state.loaded = true
}

/**
 * Load the content manifest. Renders instantly from a sessionStorage cache (so
 * the sidebar/home appear immediately even on slow networks), then refreshes
 * from the backend in the background.
 */
export async function loadManifest(force = false) {
  if (state.loaded && !force) return
  if (state.loading) return
  // 1) 用缓存即时渲染（弱网/重载时不再白屏等待）
  try {
    const cached = sessionStorage.getItem(CACHE_KEY)
    if (cached && !tree.length) applyManifest(JSON.parse(cached))
  } catch { /* ignore */ }
  // 2) 后台拉取最新并更新缓存
  state.loading = true
  try {
    const { data } = await axios.get('/wiki/manifest')
    if (data && data.code === 0 && data.data) {
      applyManifest(data.data)
      try { sessionStorage.setItem(CACHE_KEY, JSON.stringify(data.data)) } catch { /* quota */ }
    }
  } catch (e) {
    console.warn('加载内容清单失败:', e)
  } finally {
    state.loading = false
  }
}

/** Fetch a single page's full content (markdown) from the backend.
 *  track=true 才计入浏览量（仅 DocPage 阅读时传；编辑页加载正文不计）。 */
export async function fetchPageContent(pathStr, track = false) {
  const path = pathStr || HOME_PATH
  const params = track ? { path, track: 1 } : { path }
  const { data } = await axios.get('/wiki/page', { params })
  if (data && data.code === 0) return data.data
  throw new Error(data?.message || '页面加载失败')
}

/** Look up a single page meta by its route path ("生活篇/医疗"). */
export function getPage(pathStr) {
  if (!pathStr) return state.home
  return pages.find((p) => p.path === pathStr) || null
}

/** 顶层篇章（首页导航表、搜索面板、编辑器分类下拉、管理后台、站点动态筛选等用）。 */
export function categories() {
  return tree.filter((n) => n.type === 'category')
}

/** Pages in sidebar reading order, excluding the home doc.
 *  只有下面的 getAdjacent 用到，所以不导出。 */
function orderedPages() {
  return pages.filter((p) => p.path !== HOME_PATH)
}

/** Previous / next page relative to the given path, for footer nav. */
export function getAdjacent(pathStr) {
  const list = orderedPages()
  const idx = list.findIndex((p) => p.path === pathStr)
  if (idx === -1) return { prev: null, next: null }
  return {
    prev: idx > 0 ? list[idx - 1] : null,
    next: idx < list.length - 1 ? list[idx + 1] : null,
  }
}

/** Breadcrumb trail: [{ label }, …, { label, path }]. */
export function getBreadcrumbs(pathStr) {
  const crumbs = []
  if (!pathStr || pathStr === HOME_PATH) return crumbs
  const segments = pathStr.split('/')
  for (let i = 0; i < segments.length - 1; i++) {
    const slug = segments.slice(0, i + 1).join('/')
    const cat = findCategoryBySlug(tree, slug)
    crumbs.push({ label: cat ? cat.label : segments[i], icon: cat?.icon })
  }
  const page = getPage(pathStr)
  crumbs.push({ label: page ? page.title : segments[segments.length - 1], path: pathStr })
  return crumbs
}

function findCategoryBySlug(nodes, slug) {
  for (const node of nodes) {
    if (node.type === 'category') {
      if (node.slug === slug) return node
      const found = findCategoryBySlug(node.children || [], slug)
      if (found) return found
    }
  }
  return null
}

/**
 * 轻量的前端搜索：在标题、分类、描述和小标题里匹配，按得分从高到低返回。
 * manifest 已整份在内存里，直接线性扫描即可，不必为此引入搜索库。
 */
export function searchPages(query, limit = 12) {
  const q = query.trim().toLowerCase()
  if (!q) return []
  const terms = q.split(/\s+/).filter(Boolean)

  const scored = []
  for (const page of pages) {
    const title = (page.title || '').toLowerCase()
    const cat = (page.category || '').toLowerCase()
    const desc = (page.description || '').toLowerCase()

    let score = 0
    const matchedHeadings = []
    for (const term of terms) {
      if (title === term) score += 100
      else if (title.includes(term)) score += 40
      if (cat.includes(term)) score += 12
      if (desc.includes(term)) score += 8
      const hHit = (page.headings || []).find((h) => h.toLowerCase().includes(term))
      if (hHit) {
        score += 15
        if (!matchedHeadings.includes(hHit)) matchedHeadings.push(hHit)
      }
    }
    if (score > 0) scored.push({ page, score, matchedHeadings })
  }

  scored.sort((a, b) => b.score - a.score)
  return scored.slice(0, limit)
}
