# AGENTS.md

给 coding agent（Claude Code、Codex、Cursor、Copilot 等）的开发规范。人类贡献者请先读
[`README.md`](README.md)；部署与运维细节见 [`DEPLOY.md`](DEPLOY.md)。

## 项目是什么

- SurviveXMUM 的**前端**（公开仓库），线上 <https://surivivexmum.wiki>。
- Vue 3 + Vite 6 + Element Plus + Pinia + vue-router 4；图标用 `lucide-vue-next`；
  Markdown 用 markdown-it 渲染、DOMPurify 消毒。
- **后端在私有仓库 `503133214/SurviveXMUM-server`**（Spring Boot + MySQL + Redis + MinIO）。
  本仓库没有任何文章数据，内容全部在运行时从 `/api` 取。
- 前端应用在 **`wiki/`** 目录下，所有 npm 命令都在 `wiki/` 里执行。

## 常用命令

```bash
cd wiki
npm ci
WIKI_API_TARGET=https://surivivexmum.wiki/api npm run dev   # 读线上内容，不用起后端
npm run dev                                                  # 连本机后端 localhost:8080
npm test                                                     # node --test，纯函数单测
npm run build
```

- Node 20（与 `wiki/Dockerfile`、CI 一致）。
- 连线上接口时 vite 代理会去掉 `track=1`，本地浏览不计入线上阅读数；**不要**绕开这个代理
  直接请求线上的写接口。

## 目录速览

```
wiki/src/net/index.js        所有后端接口（axios，baseURL=/api）
wiki/src/wiki/index.js       内容门面：manifest、页面、搜索、面包屑
wiki/src/views/              路由页面
wiki/src/components/         通用组件、Markdown 渲染、搜索面板、侧栏
  AgentSlot.vue              首页预留的站内问答助手挂载点（接入约定见文件头注释）
wiki/src/utils/              纯函数（有单测的放这里）
  shortcut.js                搜索快捷键提示文案（⌘K / Ctrl K）
  motion.js                  「减少动效」判断，JS 滚动的 behavior 从这里取
wiki/src/assets/global.css   设计令牌与 Element Plus 皮肤
wiki/test/                   单测
deploy.sh / ci-deploy.sh     服务器部署脚本；.github/workflows/frontend.yml 是 CI/CD
```

## 分支、提交与发布

- 从 `dev` 拉特性分支 → PR 到 `dev` → `dev` 再 PR 到 `main`。
- **`main` 合并即自动部署到线上**（GitHub Actions → 服务器 `ci-deploy.sh` → `./deploy.sh main`）。
  不要直接 push `main`；`main` 有 ruleset，PR 需要 1 个 approving review。
- 提交标题用英文 conventional 格式（`feat:` / `fix:` / `chore:` / `docs:` / `ci:`），正文用中文写清
  **为什么**这样改，而不只是改了什么。
- 这是**公开仓库**：不要写入服务器 IP、SSH 登录方式、主机上的绝对路径或任何密钥；
  这些只放在私有后端仓库的 `DEPLOY.md`。不要提交 `.env*`。
- 跨前后端的功能：**先上线后端**并确认接口可用，再部署依赖它的前端。

## 代码约定

- 组件以 Options API 为主，跟随所在文件的写法；注释用中文，解释原因而不是复述代码。
- 请求一律走 `wiki/src/net/index.js` 的同源 `/api`，不要在组件里写死域名。
- 后端的 19 位雪花 ID 一律按**字符串**处理（JS Number 会丢精度，曾导致管理端 404）。
- 页面内容来自用户投稿，**不可信**：Markdown 渲染后必须经 DOMPurify；不要对未消毒内容用 `v-html`。
- 标题锚点 id 统一用 `wiki/src/utils/slug.js`（正文渲染和搜索跳转共用，两边必须一致）。
- 能抽成纯函数的逻辑放进 `wiki/src/utils/` 并在 `wiki/test/` 补单测。
- 搜索快捷键的提示文案统一用 `wiki/src/utils/shortcut.js` 的 `shortcutLabel()`，不要再各写一份平台判断。
- `scrollIntoView` / `scrollTo` 不要写死 `behavior: 'smooth'`，用 `wiki/src/utils/motion.js` 的
  `scrollBehavior()`：JS 里显式的 smooth 会绕过 CSS 对 `prefers-reduced-motion` 的处理。
- 站内问答助手接进 `wiki/src/components/AgentSlot.vue` 的默认插槽，不要另开位置；接口、Markdown 消毒、
  引用锚点和无障碍的约定写在它的文件头注释里，动手前先读。

## 样式与界面

- 颜色、阴影、圆角、间距用 `global.css` 里的设计令牌（CSS 变量），不要写死颜色；
  暗色模式在 `html.dark` 下覆盖同名变量。令牌值写 hex，不用 `oklch()`（部分内置浏览器不支持）。
- 覆盖 Element Plus 样式时选择器以 `body` 开头：它的按组件样式是懒加载注入的，常排在 `global.css` 之后。
- **界面里不用 emoji。** UI 图标用 `lucide-vue-next`；内容数据里的 emoji（页面 / 篇章的 `icon`
  字段）一律经 `WikiIcon` 组件显示成线性图标，映射在 `wiki/src/utils/icons.js`。
  出现新的 emoji 时补映射，并更新 `wiki/test/icons.test.js`。
- 整站是平面、像纸质手册的风格，下面几条是为此定的，别把删掉的装饰加回来：
  - 字体只用 `global.css` 里的系统字体栈，不引 Google Fonts 等外部字体（中国大陆加载会卡住）。
  - 字距一律为 0（负字距会把中文挤在一起），字号不小于 12px，不用大写变换。
  - 圆角只用 `--radius-xs` / `--radius-sm` / `--radius` / `--radius-lg`（2 / 4 / 6 / 8px）；
    除头像和滚动条滑块外不做胶囊形。
  - 不用渐变、毛玻璃（`backdrop-filter`）和发光；层次靠 1px 边框和留白，阴影只给浮层
    （菜单、对话框、搜索面板）。
  - 动效只做 150ms 以内（`--dur`）的颜色 / 背景 / 透明度过渡：不做悬停上浮或缩放，不做入场动画
    和循环动画；要求减少动效时也不平滑滚动（见上文 `scrollBehavior()`）。
  - 文案用平实的中文：不写口号、英文眉标和凑数的统计数字，数字只在能帮人做决定时出现。
  - `WikiIcon` 只出现在首页导航表、侧栏顶层篇章、编辑器的图标选择和搜索面板的条目里；
    标题、元信息和普通列表行不加图标。
- CSS 类名**不要以 `ad-` 开头**：会被广告拦截规则整块隐藏（线上出过事故）。
- 每处改动都要同时适配桌面与手机（约 390px 宽）、亮 / 暗两套主题；尊重
  `prefers-reduced-motion`；可交互元素要有 `:focus-visible` 样式。

## 没有明确要求时不要改

- `wiki/nginx.conf` 的缓存头与 `@stale_asset` 自愈、`wiki/index.html` 里的资源加载失败监听、
  `main.js` 里清除 `sessionStorage` 标记——这是「部署后白屏 / 看到旧版本」的治本方案，
  原理见 `DEPLOY.md` 第 5 节。
- `ci-deploy.sh` 的文件名与位置：服务器 `authorized_keys` 里写死了它的路径，改名会让自动部署失效。
- 改 `deploy.sh`、`ci-deploy.sh`、`docker-compose.yml`、`wiki/Dockerfile`、`wiki/nginx.conf`
  或 CI 工作流时，同步更新 `DEPLOY.md`。

## 提交 PR 前

- `npm test` 与 `npm run build` 通过（CI 会再跑一遍）。
- 涉及界面：桌面与手机宽度、亮与暗主题都实际看过；PR 里附桌面端与移动端截图。
- PR 描述写清改了什么、怎么验证的、哪些没验证到。
