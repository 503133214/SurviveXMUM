const SCHEME_OR_SPECIAL = /^(?:[a-z][a-z0-9+.-]*:|\/\/|#|\?)/i

function splitSuffix(href) {
  const index = href.search(/[?#]/)
  return index < 0
    ? { path: href, suffix: '' }
    : { path: href.slice(0, index), suffix: href.slice(index) }
}

function decodedPath(path) {
  try {
    return decodeURI(path)
  } catch {
    return path
  }
}

/** Match the legacy root bookmark even when Vue Router preserves URL encoding. */
export function isLegacyContributionGuidePath(path = '') {
  return decodedPath(String(path)).replace(/\/+$/, '') === '/贡献指南'
}

/** Build a lossless Vue Router target for an old contribution-guide bookmark. */
export function legacyContributionGuideRedirect(to = {}) {
  if (!isLegacyContributionGuidePath(to.path)) return null
  return {
    path: '/docs/贡献指南',
    query: to.query || {},
    hash: to.hash || '',
    replace: true,
  }
}

function decodedSegment(segment) {
  try {
    return decodeURIComponent(segment)
  } catch {
    return segment
  }
}

function resolveRelativePath(path, basePath, transformLast = (value) => value) {
  const segments = String(basePath)
    .replace(/\\/g, '/')
    .split('/')
    .filter((segment) => segment && segment !== '.')

  for (const segment of path.replace(/\\/g, '/').split('/')) {
    const semanticSegment = decodedSegment(segment)
    if (!segment || semanticSegment === '.') continue
    if (semanticSegment === '..') {
      if (segments.length) segments.pop()
      continue
    }
    segments.push(segment)
  }
  if (segments.length) segments[segments.length - 1] = transformLast(segments[segments.length - 1])
  return segments.join('/')
}

/**
 * Resolve a Markdown document link against the current Wiki directory.
 *
 * Old imported pages contain links such as ../../贡献指南.md. The previous
 * renderer appended those strings directly, allowing the browser to normalize
 * them outside /docs. Segment-by-segment resolution keeps every relative Wiki
 * link inside the document route while preserving query strings and anchors.
 */
export function resolveDocHref(href, basePath = '') {
  if (!href || SCHEME_OR_SPECIAL.test(href)) return href

  const { path, suffix } = splitSuffix(href)
  if (path.startsWith('/')) {
    // Compatibility for already-rendered bookmarks and manually authored links.
    if (isLegacyContributionGuidePath(path)) return `/docs/贡献指南${suffix}`
    return href
  }

  const resolved = resolveRelativePath(path, basePath, (segment) => segment.replace(/\.md$/i, ''))
  return `/docs/${resolved}${suffix}`
}

/** Resolve a relative Markdown image without allowing ../ to escape /docs. */
export function resolveDocAssetSrc(src, basePath = '') {
  if (!src || SCHEME_OR_SPECIAL.test(src) || src.startsWith('/')) return src
  const { path, suffix } = splitSuffix(src)
  return `/docs/${resolveRelativePath(path, basePath)}${suffix}`
}
