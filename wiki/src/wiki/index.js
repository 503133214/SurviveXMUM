// Front-end facade over the auto-generated content manifest (src/wiki.data.js).
// Everything the UI needs about wiki content flows through here.
import { tree, pages, home, generatedAt } from '@/wiki.data.js'

export { tree, pages, home, generatedAt }

// The home page route (root README) lives at /docs/README.
export const HOME_PATH = home?.path || 'README'

// Repo + branch used to build "edit this page" links.
export const REPO = 'https://github.com/503133214/SurviveXMUM'
export const EDIT_BASE = `${REPO}/edit/main/wiki/public/docs`

/** Look up a single page by its route path ("生活篇/医疗"). */
export function getPage(pathStr) {
  if (!pathStr) return home
  return pages.find((p) => p.path === pathStr) || null
}

/** Top-level categories only (for the homepage cards). */
export function categories() {
  return tree.filter((n) => n.type === 'category')
}

/** Pages in sidebar reading order, excluding the home doc. */
export function orderedPages() {
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
  // map folder segments to category labels where possible
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
      const found = findCategoryBySlug(node.children, slug)
      if (found) return found
    }
  }
  return null
}

/**
 * Lightweight client-side search across title, category, tags and headings.
 * Returns scored results, best first. No dependencies.
 */
export function searchPages(query, limit = 12) {
  const q = query.trim().toLowerCase()
  if (!q) return []
  const terms = q.split(/\s+/).filter(Boolean)

  const scored = []
  for (const page of pages) {
    const title = (page.title || '').toLowerCase()
    const cat = (page.category || '').toLowerCase()
    const tags = (page.tags || []).join(' ').toLowerCase()
    const headings = (page.headings || []).join(' ').toLowerCase()
    const desc = (page.description || '').toLowerCase()

    let score = 0
    const matchedHeadings = []
    for (const term of terms) {
      if (title === term) score += 100
      else if (title.includes(term)) score += 40
      if (tags.includes(term)) score += 25
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
