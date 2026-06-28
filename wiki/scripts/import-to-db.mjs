// scripts/import-to-db.mjs
//
// 一次性把 public/docs/** 的 wiki 内容导出为 MySQL 种子 SQL（wiki_category + wiki_page）。
// 复用 generate-manifest.mjs 的 buildManifest 解析，保证标题/分类/排序/标签/headings 与前端一致。
//
// 用法：node scripts/import-to-db.mjs > seed.sql
//      然后在服务器：mysql wiki < seed.sql
//
// 注意：脚本开头会清空 wiki_category / wiki_page（不动 user / wiki_revision），便于重复执行。

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildManifest } from './generate-manifest.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DOCS_PATH = path.resolve(__dirname, '../public/docs');

function sql(v) {
  if (v === null || v === undefined) return 'NULL';
  return "'" + String(v).replace(/\\/g, '\\\\').replace(/'/g, "\\'") + "'";
}

function num(v, fallback) {
  return typeof v === 'number' && !Number.isNaN(v) ? v : fallback;
}

function stripFrontmatter(raw) {
  return raw.replace(/^﻿?---\r?\n[\s\S]*?\r?\n---\r?\n?/, '');
}

function readBody(routePath) {
  const file = path.join(DOCS_PATH, routePath + '.md');
  try {
    return stripFrontmatter(fs.readFileSync(file, 'utf8'));
  } catch {
    return '';
  }
}

// 收集分类节点（含嵌套，理论上只有一层）。
function collectCategories(nodes, acc = []) {
  for (const n of nodes) {
    if (n.type === 'category') {
      acc.push(n);
      if (n.children) collectCategories(n.children, acc);
    }
  }
  return acc;
}

const manifest = buildManifest({ useGit: true });
const categories = collectCategories(manifest.tree);

// 分类 slug → id
let catId = 1;
const catIdBySlug = new Map();
const out = [];

out.push('-- AUTO-GENERATED seed by scripts/import-to-db.mjs');
out.push('-- 重复执行安全：仅清空内容表，不动 user / wiki_revision');
out.push('SET NAMES utf8mb4;');
out.push('DELETE FROM `wiki_page`;');
out.push('DELETE FROM `wiki_category`;');
out.push('');

for (const c of categories) {
  const id = catId++;
  catIdBySlug.set(c.slug, id);
  out.push(
    `INSERT INTO \`wiki_category\` (id, slug, label, icon, description, sort_order) VALUES (` +
      `${id}, ${sql(c.slug)}, ${sql(c.label)}, ${sql(c.icon || '📁')}, ${sql(c.description || '')}, ${num(c.order, 999)});`
  );
}
out.push('');

let pageId = 1000;
for (const p of manifest.pages) {
  const id = pageId++;
  const body = readBody(p.path);
  const slug = p.path.includes('/') ? p.path.slice(p.path.lastIndexOf('/') + 1) : p.path;
  const categorySlug = p.category || '';
  const categoryId = categorySlug && catIdBySlug.has(categorySlug) ? catIdBySlug.get(categorySlug) : 'NULL';
  out.push(
    `INSERT INTO \`wiki_page\` ` +
      `(id, category_id, category_slug, slug, path, title, icon, description, tags, headings, content, sort_order, status, view_count) VALUES (` +
      `${id}, ${categoryId}, ${sql(categorySlug)}, ${sql(slug)}, ${sql(p.path)}, ${sql(p.title)}, ` +
      `${sql(p.icon || '')}, ${sql(p.description || '')}, ${sql(JSON.stringify(p.tags || []))}, ` +
      `${sql(JSON.stringify(p.headings || []))}, ${sql(body)}, ${num(p.order, 999)}, 'PUBLISHED', 0);`
  );
}

process.stdout.write(out.join('\n') + '\n');
process.stderr.write(`[import] ${categories.length} 个分类，${manifest.pages.length} 篇页面 → SQL 已生成\n`);
