# SurviveXMUM Wiki

厦门大学马来西亚分校生存指南的前端站点 —— Vue 3 + Vite + Element Plus。

## 快速开始

```sh
npm install      # 安装依赖
npm run dev      # 本地开发（自动生成内容清单 + 热更新）
npm run build    # 生产构建
npm run preview  # 预览生产构建
```

## 📝 如何更新 Wiki 内容（重点）

**你只需要写 Markdown，导航 / 首页 / 搜索全部自动更新**，不用碰任何前端代码。

1. 在 `public/docs/<分类>/` 下新建一个 `.md` 文件，例如 `生活篇/如何办理银行卡.md`。
2. 写正文，第一行的 `# 标题` 会自动成为页面标题。
3. `npm run dev` 时保存即热更新；提交 PR 即可上线。

### 可选：Frontmatter（精细控制）

```yaml
---
title: 行前指南          # 默认取正文第一个 # 标题
icon: ✈️                # 侧边栏 / 卡片图标
order: 1                # 同级排序，越小越靠前
description: 出发前准备   # 默认自动截取首段
tags: [新生, 准备]        # 用于站内搜索
draft: false            # true 会标记为「草稿」
---
```

### 可选：分类元数据

在分类文件夹放 `_category.json` 控制分组的名称 / 图标 / 排序 / 简介：

```json
{ "label": "入学篇", "icon": "✈️", "order": 1, "description": "行前准备、签证……" }
```

> 以 `_` 或 `.` 开头的文件/文件夹不会进入导航（用于元数据与资源）。
> 图片放进分类下的 `img/` 目录，用相对路径 `![](img/x.jpg)` 引用即可。

完整说明见站内的「贡献指南」页面（`public/docs/贡献指南.md`）。

## 🏗️ 内容流水线如何工作

```
public/docs/**.md  ──►  scripts/generate-manifest.mjs  ──►  src/wiki.data.js  ──►  src/wiki/index.js  ──►  UI
   (作者写这里)           (扫描 frontmatter / 标题 /              (导航树 + 扁平页面          (getPage / 面包屑 /
                          小标题 / git 更新时间)                  列表 + 搜索索引)            上一篇下一篇 / 搜索)
```

- `npm run dev` / `npm run build` 会先运行 `wiki:manifest` 生成清单。
- 开发时，`vite.config.js` 里的 `wiki-content-watcher` 插件监听 `public/docs`，
  文件增删改后**实时重建清单**并通过 HMR 刷新，无需重启。

## 目录结构

```
src/
├── wiki/index.js          # 内容门面：查询、面包屑、上一篇/下一篇、搜索
├── wiki.data.js           # 自动生成（勿手改）
├── components/
│   ├── Header.vue         # 顶栏：搜索 + 主题切换 + 用户菜单
│   ├── GlobalSearch.vue   # 全站搜索（标题/小标题/标签，支持键盘）
│   ├── WikiSidebar.vue    # 侧边栏（带筛选）
│   ├── WikiSidebarNode.vue# 侧边栏递归节点
│   └── MarkdownRenderer.vue # Markdown 渲染 + 目录 + 代码复制
├── views/                 # 页面（Home / Doc / Login / Profile …）
├── composables/useTheme.js# 亮 / 暗主题
└── assets/global.css      # 设计令牌（CSS 变量）
```

## 推荐 IDE

[VSCode](https://code.visualstudio.com/) + [Volar](https://marketplace.visualstudio.com/items?itemName=Vue.volar)（禁用 Vetur）。
