// scripts/generate-manifest.mjs
//
// Scans public/docs and produces src/wiki.data.js — the single source of truth
// for navigation, the homepage, breadcrumbs, prev/next and search.
//
// CONTENT AUTHORS DON'T TOUCH THIS FILE. To add or change a page you only:
//   1. Drop a .md file into public/docs/<分类>/
//   2. (optional) add YAML frontmatter at the top of the file:
//        ---
//        title: 行前指南
//        icon: ✈️
//        order: 1
//        description: 出发前要准备的一切
//        tags: [新生, 准备]
//        draft: false
//        ---
//   3. (optional) drop a _category.json in a folder to control the group:
//        { "label": "入学篇", "icon": "✈️", "order": 1, "description": "..." }
//
// Run automatically by `npm run dev` / `npm run build`, and live by the Vite
// watcher plugin in vite.config.js.

import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DOCS_PATH = path.resolve(__dirname, '../public/docs');
const OUTPUT_PATH = path.resolve(__dirname, '../src/wiki.data.js');

// Categories listed here keep this order; anything else is appended by `order`
// then alphabetically. Tweak freely — unknown folders still show up.
const DEFAULT_CATEGORY_ORDER = [
  '人生篇',
  '入学篇',
  '生活篇',
  '学习篇',
  '专业篇',
  '升学篇',
  '走进社会篇',
  '访谈篇',
  'api',
];

const CATEGORY_ICON_FALLBACK = {
  人生篇: '🌏',
  入学篇: '✈️',
  生活篇: '🏠',
  学习篇: '🎓',
  专业篇: '🎯',
  升学篇: '🎓',
  走进社会篇: '💼',
  访谈篇: '🎙️',
  api: '🔌',
};

// ---------------------------------------------------------------------------
// Tiny dependency-free YAML-frontmatter parser. Supports the small subset a
// wiki page realistically needs: scalars, inline arrays [a, b] and block lists.
// ---------------------------------------------------------------------------
function parseScalar(value) {
  const v = value.trim();
  if (v === '') return '';
  if (v === 'true') return true;
  if (v === 'false') return false;
  if (v === 'null' || v === '~') return null;
  if (/^-?\d+(\.\d+)?$/.test(v)) return Number(v);
  // inline array: [a, b, "c d"]
  if (v.startsWith('[') && v.endsWith(']')) {
    const inner = v.slice(1, -1).trim();
    if (!inner) return [];
    return inner.split(',').map((s) => unquote(s.trim())).filter((s) => s !== '');
  }
  return unquote(v);
}

function unquote(s) {
  if (
    (s.startsWith('"') && s.endsWith('"')) ||
    (s.startsWith("'") && s.endsWith("'"))
  ) {
    return s.slice(1, -1);
  }
  return s;
}

function parseFrontmatter(raw) {
  const match = raw.match(/^﻿?---\r?\n([\s\S]*?)\r?\n---\r?\n?/);
  if (!match) return { data: {}, body: raw };

  const body = raw.slice(match[0].length);
  const lines = match[1].split(/\r?\n/);
  const data = {};
  let currentKey = null;

  for (const line of lines) {
    if (line.trim() === '' || line.trim().startsWith('#')) continue;
    const listItem = line.match(/^\s*-\s+(.*)$/);
    if (listItem && currentKey) {
      if (!Array.isArray(data[currentKey])) data[currentKey] = [];
      data[currentKey].push(parseScalar(listItem[1]));
      continue;
    }
    const kv = line.match(/^([A-Za-z0-9_-]+)\s*:\s*(.*)$/);
    if (kv) {
      const key = kv[1];
      const value = kv[2];
      currentKey = key;
      if (value.trim() === '') {
        data[key] = []; // expect a block list to follow
      } else {
        data[key] = parseScalar(value);
      }
    }
  }
  return { data, body };
}

// ---------------------------------------------------------------------------
// Markdown introspection (no full parse needed).
// ---------------------------------------------------------------------------
function firstHeading(body) {
  const lines = body.split(/\r?\n/);
  let inFence = false;
  for (const line of lines) {
    if (/^\s*```/.test(line)) inFence = !inFence;
    if (inFence) continue;
    const h1 = line.match(/^#\s+(.+?)\s*#*\s*$/);
    if (h1) return h1[1].trim();
  }
  return null;
}

function collectHeadings(body) {
  const lines = body.split(/\r?\n/);
  const headings = [];
  let inFence = false;
  for (const line of lines) {
    if (/^\s*```/.test(line)) inFence = !inFence;
    if (inFence) continue;
    const m = line.match(/^(#{2,4})\s+(.+?)\s*#*\s*$/);
    if (m) headings.push(m[2].trim());
  }
  return headings;
}

function extractDescription(body) {
  const lines = body.split(/\r?\n/);
  let inFence = false;
  for (let raw of lines) {
    const line = raw.trim();
    if (/^```/.test(line)) {
      inFence = !inFence;
      continue;
    }
    if (inFence) continue;
    if (!line) continue;
    if (line.startsWith('#')) continue; // heading
    if (line.startsWith('![')) continue; // image
    if (line.startsWith('|')) continue; // table
    if (line.startsWith('<')) continue; // html block
    if (line.startsWith('>')) continue; // quote
    if (/^[-*+]\s/.test(line)) continue; // list
    // strip basic markdown for a clean preview
    const clean = line
      .replace(/!\[[^\]]*\]\([^)]*\)/g, '')
      .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
      .replace(/[*_`~]/g, '')
      .trim();
    if (clean) return clean.length > 90 ? clean.slice(0, 90) + '…' : clean;
  }
  return '';
}

function titleFromFilename(name) {
  let formatted = name.replace(/\.md$/i, '').replace(/[-_]/g, ' ');
  // Only title-case ASCII words; leave CJK untouched.
  return formatted.replace(/\b[a-z]/g, (c) => c.toUpperCase());
}

// ---------------------------------------------------------------------------
// Git / fs metadata.
// ---------------------------------------------------------------------------
function lastUpdated(filePath, useGit) {
  if (useGit) {
    try {
      const out = execFileSync(
        'git',
        ['log', '-1', '--format=%cI', '--', filePath],
        { cwd: DOCS_PATH, stdio: ['ignore', 'pipe', 'ignore'] }
      )
        .toString()
        .trim();
      if (out) return out;
    } catch {
      /* not a git repo / untracked → fall through */
    }
  }
  try {
    return fs.statSync(filePath).mtime.toISOString();
  } catch {
    return null;
  }
}

function readCategoryMeta(dirPath) {
  const metaPath = path.join(dirPath, '_category.json');
  if (fs.existsSync(metaPath)) {
    try {
      return JSON.parse(fs.readFileSync(metaPath, 'utf8'));
    } catch (e) {
      console.warn(`[wiki] 无法解析 ${metaPath}: ${e.message}`);
    }
  }
  return {};
}

// ---------------------------------------------------------------------------
// Build a page object from a markdown file.
// ---------------------------------------------------------------------------
function buildPage(fullPath, useGit) {
  const raw = fs.readFileSync(fullPath, 'utf8');
  const { data, body } = parseFrontmatter(raw);
  const relPath = path.relative(DOCS_PATH, fullPath).replace(/\\/g, '/');
  const routePath = relPath.replace(/\.md$/i, '');
  const fileName = path.basename(fullPath, '.md');
  const category = relPath.includes('/') ? relPath.split('/')[0] : '';

  return {
    type: 'page',
    title: data.title || firstHeading(body) || titleFromFilename(fileName),
    path: routePath,
    category,
    icon: data.icon || '',
    order: typeof data.order === 'number' ? data.order : null,
    description: data.description || extractDescription(body),
    tags: Array.isArray(data.tags) ? data.tags : data.tags ? [data.tags] : [],
    headings: collectHeadings(body),
    draft: data.draft === true,
    lastUpdated: lastUpdated(fullPath, useGit),
  };
}

function sortItems(a, b) {
  const ao = a.order == null ? Infinity : a.order;
  const bo = b.order == null ? Infinity : b.order;
  if (ao !== bo) return ao - bo;
  return String(a.title || a.label).localeCompare(String(b.title || b.label), 'zh');
}

// ---------------------------------------------------------------------------
// Walk a directory into a list of nodes (pages + nested categories).
// ---------------------------------------------------------------------------
function walk(dirPath, useGit) {
  const entries = fs.readdirSync(dirPath, { withFileTypes: true });
  const pages = [];
  const categories = [];

  for (const entry of entries) {
    if (entry.name.startsWith('.') || entry.name.startsWith('_')) continue;
    const full = path.join(dirPath, entry.name);

    if (entry.isDirectory()) {
      if (entry.name === 'img' || entry.name === 'images' || entry.name === 'assets') {
        continue; // asset folders are not navigation
      }
      const children = walk(full, useGit);
      if (children.length === 0) continue;
      const meta = readCategoryMeta(full);
      categories.push({
        type: 'category',
        slug: path.relative(DOCS_PATH, full).replace(/\\/g, '/'),
        label: meta.label || entry.name,
        icon: meta.icon || CATEGORY_ICON_FALLBACK[entry.name] || '📁',
        order:
          typeof meta.order === 'number'
            ? meta.order
            : DEFAULT_CATEGORY_ORDER.indexOf(entry.name) !== -1
              ? DEFAULT_CATEGORY_ORDER.indexOf(entry.name)
              : null,
        description: meta.description || '',
        children,
      });
    } else if (entry.isFile() && /\.md$/i.test(entry.name)) {
      // root README is handled separately as the home doc
      if (dirPath === DOCS_PATH && /^readme$/i.test(path.basename(entry.name, '.md'))) {
        continue;
      }
      pages.push(buildPage(full, useGit));
    }
  }

  pages.sort(sortItems);
  categories.sort(sortItems);
  // pages before nested categories within a level reads better in a sidebar
  return [...pages, ...categories];
}

// ---------------------------------------------------------------------------
// Flatten the tree into the reading order used for search & prev/next.
// ---------------------------------------------------------------------------
function flatten(nodes, acc) {
  for (const node of nodes) {
    if (node.type === 'page') acc.push(node);
    else if (node.children) flatten(node.children, acc);
  }
  return acc;
}

export function buildManifest({ useGit = true } = {}) {
  if (!fs.existsSync(DOCS_PATH)) {
    return { tree: [], pages: [], home: null, generatedAt: new Date().toISOString() };
  }

  const tree = walk(DOCS_PATH, useGit);

  // Home doc (root README.md)
  let home = null;
  const readmePath = fs
    .readdirSync(DOCS_PATH)
    .find((n) => /^readme\.md$/i.test(n));
  if (readmePath) {
    home = buildPage(path.join(DOCS_PATH, readmePath), useGit);
    home.title = home.title || '首页';
  }

  const pages = flatten(tree, []);
  if (home) pages.unshift(home);

  return { tree, pages, home, generatedAt: new Date().toISOString() };
}

export function generateManifest(options = {}) {
  const manifest = buildManifest(options);
  const fileContent = `// ⚠️ AUTO-GENERATED by scripts/generate-manifest.mjs — do not edit by hand.
// Add markdown files under public/docs/ instead; this regenerates on dev/build.
// Generated: ${manifest.generatedAt}

export const tree = ${JSON.stringify(manifest.tree, null, 2)};

export const pages = ${JSON.stringify(manifest.pages, null, 2)};

export const home = ${JSON.stringify(manifest.home, null, 2)};

export const generatedAt = ${JSON.stringify(manifest.generatedAt)};
`;
  fs.writeFileSync(OUTPUT_PATH, fileContent, 'utf8');
  return manifest;
}

// Run directly: `node scripts/generate-manifest.mjs`
if (import.meta.url === `file://${process.argv[1]}`) {
  console.log('[wiki] 正在生成内容清单 (manifest)…');
  const m = generateManifest({ useGit: true });
  console.log(
    `[wiki] 完成：${m.pages.length} 篇文档，${m.tree.filter((n) => n.type === 'category').length} 个分类 → src/wiki.data.js`
  );
}
