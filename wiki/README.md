# SurviveXMUM Wiki（前端）

厦门大学马来西亚分校生存指南的前端站点 —— Vue 3 + Vite + Element Plus。

## 快速开始

```sh
npm install      # 安装依赖
npm run dev      # 本地开发（HMR）
npm run build    # 生产构建（输出到 dist/）
npm run preview  # 预览生产构建
```

开发时前端通过 `/api` 调用后端，`vite.config.js` 已把 `/api` 代理到 `http://localhost:8080`（并去掉 `/api` 前缀）。需要先在本地或服务器跑起后端（见 `../backend/`）。

## 内容如何维护（重点）

**内容全部存在数据库里，运行时通过后端 API 获取，前端不再有任何构建期的 Markdown 文件。**

- 普通用户、管理员都在**网站上**直接编辑 / 投稿，管理员审核通过后即时生效。
- 前端启动后调用 `GET /wiki/manifest`（导航树 / 首页 / 搜索索引）和 `GET /wiki/page?path=...`（单页内容）。
- Markdown 在浏览器端渲染（`MarkdownRenderer.vue`），并用 DOMPurify 做 XSS 清洗。
- 图片由后端处理：编辑器上传到 `POST /wiki/image`（存 MinIO）。
- 历史遗留的少量图片仍放在 `public/docs/**/img/` 下，数据库内容以相对路径引用它们；这些目录**仅保留图片**，已不再有 `.md` 文件。

> 投稿 / 审核的完整流程见站内的「贡献指南」页面，以及仓库根目录的 `../README.md`。

## 目录结构

```
src/
├── wiki/index.js          # 内容门面：调用后端 manifest/page，封装查询、面包屑、上一篇/下一篇、搜索
├── net/index.js           # 与后端交互的全部接口（鉴权、投稿、审核、用户/页面管理）
├── components/
│   ├── Header.vue         # 顶栏：搜索 + 主题切换 + 用户菜单
│   ├── GlobalSearch.vue   # 全站搜索（标题/小标题/标签，⌘K 唤起）
│   ├── WikiSidebar.vue    # 侧边栏（带筛选）
│   ├── WikiSidebarNode.vue# 侧边栏递归节点
│   ├── MarkdownRenderer.vue # Markdown 渲染 + 目录 + 代码复制
│   ├── AdminUsersPanel.vue  # 后台：用户管理（仅超级管理员）
│   ├── AdminPagesPanel.vue  # 后台：页面管理
│   └── MarkdownDiff.vue     # 审核：内容差异对比
├── views/                 # 页面（Home / Doc / Login / Profile / Edit / Admin …）
├── store/userStore.js     # 登录态与角色（isAdmin / isSuperAdmin）
├── composables/useTheme.js# 亮 / 暗主题
└── assets/global.css      # 设计令牌（CSS 变量）
```

## 推荐 IDE

[VSCode](https://code.visualstudio.com/) + [Volar](https://marketplace.visualstudio.com/items?itemName=Vue.volar)（禁用 Vetur）。
