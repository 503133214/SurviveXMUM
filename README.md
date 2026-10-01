# SurviveXMUM

[![Frontend CI/CD](https://github.com/503133214/SurviveXMUM/actions/workflows/frontend.yml/badge.svg?branch=main)](https://github.com/503133214/SurviveXMUM/actions/workflows/frontend.yml)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue.svg)](LICENSE)

厦门大学马来西亚分校自救指南 —— <https://surivivexmum.wiki>

SurviveXMUM 是一个帮助厦门大学马来西亚分校（Xiamen University Malaysia, XMUM）学生
更快适应校园与周边生活的指南，由在校学生发起与维护。

它是一个**带后端的在线 Wiki**：内容全部保存在数据库中，用户在网站上直接编辑、投稿，
管理员审核通过后即时生效，**不需要重新构建或合并代码**。

本仓库是**前端**。后端在私有仓库 `503133214/SurviveXMUM-server`
（Spring Boot 3 + MySQL + Redis + MinIO，JWT 鉴权），两边各自独立部署、互不依赖。

## 技术栈

Vue 3 · Vite 6 · Element Plus · Pinia · vue-router 4 · lucide-vue-next ·
markdown-it + DOMPurify · Node 20

## 如何贡献内容

内容不通过 GitHub 上的 Markdown 文件维护，而是**直接在网站上完成**：

1. **注册 / 登录**：使用 `@xmu.edu.my` 校园邮箱注册（需邮箱验证码），然后登录。
2. **编辑或新建**：在任意页面点「编辑」提出修改，或新建条目；用站内 Markdown 编辑器撰写，图片可直接上传。
3. **提交投稿**：提交后进入「待审核」队列。
4. **管理员审核**：通过后内容**立即上线**，或被驳回并附说明。
5. **查看进度**：在「个人中心」查看投稿状态、草稿与讨论。

详见站内[贡献指南](https://surivivexmum.wiki/docs/贡献指南)。

> 角色：普通用户（投稿、讨论）、管理员（审核 + 页面 / 评论管理）、超级管理员（含用户管理等全部权限）。

## 本地开发

需要 Node.js 20。所有 npm 命令都在 `wiki/` 目录下执行：

```bash
cd wiki
npm ci
npm run dev       # 本地开发，/api 代理到 localhost:8080
npm test          # 纯函数单测（node --test）
npm run build     # 生产构建，输出到 wiki/dist/
npm run preview   # 预览生产构建
```

只改界面时不必起后端，直接读线上内容（代理会去掉 `track=1`，本地浏览不计入线上阅读数）：

```bash
WIKI_API_TARGET=https://surivivexmum.wiki/api npm run dev
```

需要联调时把 `SurviveXMUM-server` 在本机跑起来即可。推荐编辑器：VS Code +
[Vue - Official](https://marketplace.visualstudio.com/items?itemName=Vue.volar)（打开仓库时会提示安装）。

### 内容从哪里来

前端不包含任何文章数据，全部在运行时从同源 `/api` 获取：

- `GET /wiki/manifest`：导航树、首页数据与搜索索引；`GET /wiki/page?path=...`：单页内容。
- Markdown 在浏览器端渲染（`MarkdownRenderer.vue`），渲染结果一律经 DOMPurify 消毒。
- 编辑器上传的图片由后端存入 MinIO（`POST /wiki/image`）。

## 目录结构

```
.
├── wiki/                    前端应用（Vue 3 + Vite）
│   ├── src/
│   │   ├── net/index.js     所有后端接口（axios，baseURL=/api）
│   │   ├── wiki/index.js    内容门面：manifest、页面、搜索、标签、面包屑
│   │   ├── views/           路由页面（首页、文档、编辑、个人中心、管理后台…）
│   │   ├── components/      通用组件、Markdown 渲染、搜索面板、侧栏、后台面板
│   │   ├── utils/           纯函数（有单测）
│   │   ├── store/           Pinia：登录态与角色
│   │   ├── composables/     主题、搜索面板等组合式函数
│   │   └── assets/global.css 设计令牌（CSS 变量）与 Element Plus 皮肤
│   ├── public/              静态资源（favicon、站点 Logo）
│   ├── test/                单测
│   ├── Dockerfile           Node 构建 → nginx 托管
│   └── nginx.conf           容器内 nginx：静态托管、SPA 回退、缓存策略
├── design/logo/             Logo 设计源文件（.ai / SVG / PNG）
├── .github/workflows/       CI/CD
├── docker-compose.yml       前端容器编排
├── deploy.sh                服务器部署脚本
├── ci-deploy.sh             自动部署的服务器端入口
├── DEPLOY.md                部署与运维文档
└── AGENTS.md                给 coding agent 的开发规范
```

## 参与代码开发

1. Fork 本仓库，从 `dev` 分支创建特性分支。
2. 提交标题用英文 conventional 格式（`feat:` / `fix:` / `chore:` / `docs:` / `ci:`），正文写清为什么这样改。
3. 向 `dev` 发起 Pull Request；`npm test` 与 `npm run build` 需通过（CI 会再跑一遍）。
4. 涉及界面时附桌面端与移动端（约 390px 宽）截图，并确认亮 / 暗两套主题。

代码约定（设计令牌、图标、Markdown 安全、ID 处理等）见 [`AGENTS.md`](AGENTS.md)。
后端改动请到 `SurviveXMUM-server` 仓库。

## 部署

`dev` 是日常集成分支；`dev` 经 PR 合并进 `main` 后，GitHub Actions 自动构建并部署到线上。
构建方式、缓存策略、反向代理与回滚见 [`DEPLOY.md`](DEPLOY.md)。

服务器地址、登录方式与运维细节不在本公开仓库中。

## 许可证

[GNU General Public License v3.0](LICENSE)
