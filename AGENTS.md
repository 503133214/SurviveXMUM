# AGENTS.md

给 coding agent（Claude Code、Codex、Cursor、Copilot 等）的开发规范。人类贡献者请先读
[`README.md`](README.md)；部署与运维细节见 [`DEPLOY.md`](DEPLOY.md)。

## 项目是什么

- SurviveXMUM 的**前端**（公开仓库），线上 <https://surivivexmum.wiki>。
- Vue 3 + Vite 6 + Element Plus + Pinia + vue-router 4；图标用 `lucide-vue-next`；
  Markdown 用 markdown-it 渲染、DOMPurify 消毒。
- 本仓库没有任何文章数据，内容全部在运行时从 `/api` 取。另有两个**私有**仓库，三者各自部署：
  - **`503133214/SurviveXMUM-server`**（Spring Boot + MySQL + Redis + MinIO）：业务接口、权限、审核，
    以及 Agent 网关（把 `/api/agent/**`、`/api/admin/agent/**` 转给 Agent 服务）。普通接口的文档是站内
    `/docs/api/` 下的三页（不含 Agent 接口），部署见该仓库 `DEPLOY.md`。
  - **`503133214/SurviveXMUM-agent`**（Python + FastAPI + PostgreSQL）：站内问答 Agent，浏览器从不直连它。
    网关契约以该仓库 `docs/java-contract.md` 为准，哪些功能做完了、线上能不能用以 `docs/STATUS.md` 为准；
    下文「联调站内问答 Agent」只摘前端必须知道的部分。
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
  直接请求线上的写接口。`/api/agent/**` 同样会打到线上，而创建 run 是写操作：开发 Agent 相关功能只连本机后端。
- 连本机后端时，后端 `.env` 以它仓库的 `.env.example` 为准。注意两项：
  - `AGENT_GATEWAY_SECRET`（至少 32 字节）必填，缺了后端整个起不来，哪怕你不碰 Agent。
  - `JWT_SECRET` 留空或保留示例值时，后端每次启动随机生成签名密钥：重启后端后前端收到 `code: 401`，
    重新登录即可，不是前端的 bug。

## 目录速览

```
wiki/src/net/index.js        所有后端接口（axios，baseURL=/api）
wiki/src/wiki/index.js       内容门面：manifest、页面、搜索、面包屑
wiki/src/views/              路由页面
wiki/src/components/         通用组件、Markdown 渲染、搜索面板、侧栏
  AgentSlot.vue              首页预留的站内问答助手挂载点（接入约定见下文「联调站内问答 Agent」）
  ArticleEditor.vue          编辑页的正文编辑器（工具栏、预览、写作帮助）
wiki/src/utils/              纯函数（有单测的放这里）
  slug.js                    标题锚点 id；Agent 算引用锚点也照它的规则（改动见「代码约定」）
  icons.js                   内容 emoji → Lucide 线性图标的映射
  markdownEditing.js         编辑器工具栏的纯函数（含插入链接时的协议校验）
  shortcut.js                搜索快捷键提示文案（⌘K / Ctrl K）
  motion.js                  「减少动效」判断，JS 滚动的 behavior 从这里取
  navGroups.js               首页「内容导航」每个篇章先露哪几篇（阅读最多的三篇，其余点展开）
wiki/src/assets/global.css   设计令牌与 Element Plus 皮肤
wiki/test/                   单测
deploy.sh / ci-deploy.sh     服务器部署脚本；.github/workflows/frontend.yml 是 CI/CD
```

## 分支、提交与发布

- 从 `dev` 拉特性分支 → PR 到 `dev` → `dev` 再 PR 到 `main`。
  有 PR 直接合进了 `main` 时 `dev` 会落后：先把 `main` 合回 `dev` 再拉分支，否则新分支缺这些改动，
  回到 `main` 时还会冲突。
- **`main` 合并即自动部署到线上**（GitHub Actions → 服务器 `ci-deploy.sh` → `./deploy.sh main`）。
  不要直接 push `main`；`main` 有 ruleset，PR 需要 1 个 approving review。
- 提交标题用英文 conventional 格式（`feat:` / `fix:` / `chore:` / `docs:` / `ci:`），正文用中文写清
  **为什么**这样改，而不只是改了什么。
- 这是**公开仓库**：不要写入服务器 IP、SSH 登录方式、主机上的绝对路径或任何密钥；
  这些只放在私有后端仓库的 `DEPLOY.md`。不要提交 `.env*`。
- 跨仓库的功能按依赖顺序上线：**后端（server）→ Agent 服务 → 前端**，每一步确认线上接口可用再走下一步。
  前端 `main` 合并即上线，所以依赖未上线接口的代码不要合进 `main`（要先合就放在默认关闭的开关后面，
  先和维护者确认）。问答在线上是否可用、还缺哪些前提，看 Agent 仓库 `docs/STATUS.md`；没就绪前
  `AgentSlot` 保持「筹备中」。

## 代码约定

- 组件以 Options API 为主，跟随所在文件的写法；注释用中文，解释原因而不是复述代码。
- 请求一律走 `wiki/src/net/index.js` 的同源 `/api`，不要在组件里写死域名。
- 后端的 19 位雪花 ID 一律按**字符串**处理（JS Number 会丢精度，曾导致管理端 404）。
- 页面内容来自用户投稿，**不可信**：Markdown 渲染后必须经 DOMPurify；不要对未消毒内容用 `v-html`。
- 通知、公告的 `link` 后端写入时只放行两类：`/` 开头且不是 `//` 的站内路径，和带主机名的 http(s) 网址；含空白、
  反斜杠或控制字符的一律拒收。加这条校验之前存下的旧通知没清洗过，所以前端照样要过滤。
  打开时分开处理：站内路径用 `router.push`，http(s) 用 `window.open(link, '_blank', 'noopener,noreferrer')`，
  其它值忽略。别把 http(s) 交给 `router.push`：vue-router 会把它当相对路径解析（在 `/docs/a/b` 上变成
  `/docs/a/https://…`），落到找不到页面。致谢墙等其它来源的 `link` 渲染成 `href` 前同样要过滤，
  不要假定后端已经校验过协议。
- 署名（`displayName`、`authorName`、`replyToName`）原样显示，打不打码由后端决定：在册用户没设昵称时
  公开完整校园邮箱，账号注销后后端重新打码。前端不要自己打码或拼邮箱。改昵称走 `PUT /user/profile
  {nickname}`：去掉首尾空白后最多 30 个 UTF-16 码元（`s.trim().length`，emoji 算 2），不能含 `@` 和控制字符，
  空串或纯空白表示清除。它只影响之后显示的署名，版本历史和站点动态里已发布版本的署名是发布时的快照，
  不会跟着变，界面文案别承诺更多。
- 标题锚点 id 统一用 `wiki/src/utils/slug.js`，正文渲染和搜索跳转共用。锚点规则不止这一个文件：重复标题加
  `-1` / `-2`、slug 为空时回退 `h-N` 在 `MarkdownRenderer.vue` 的 `heading_open` 里，`DocPage.vue` 会去掉正文开头的 H1。
  Agent 的分块（`src/wiki_agent/indexing/chunker.py`）照这套规则算引用锚点，Agent 仓库 `docs/java-contract.md`
  也要求后端将来的检索接口照它算。改其中任何一处都要通知另外两个仓库：Agent 要同步改 `chunker.py` 的
  `slugify`、提升 `CHUNKING_VERSION` 并重建向量索引（已生成回答里存的锚点不会跟着变）。
- 能抽成纯函数的逻辑放进 `wiki/src/utils/` 并在 `wiki/test/` 补单测。
- 搜索快捷键的提示文案统一用 `wiki/src/utils/shortcut.js` 的 `shortcutLabel()`，不要再各写一份平台判断。
- `scrollIntoView` / `scrollTo` 不要写死 `behavior: 'smooth'`，用 `wiki/src/utils/motion.js` 的
  `scrollBehavior()`：JS 里显式的 smooth 会绕过 CSS 对 `prefers-reduced-motion` 的处理。
- 站内问答助手的首页入口接进 `wiki/src/components/AgentSlot.vue` 的默认插槽；要加别的入口（文档页
  「问这篇」、独立页面等）先和维护者确认。接口、事件流和引用以下一节为准，无障碍约定见它的文件头注释。

## 联调站内问答 Agent

链路：浏览器 → 同源 `/api/agent/**`（管理端 `/api/admin/agent/**`）→ 后端网关 → Agent 服务。网关校验登录，
给每个请求现签一个短期的 `X-Agent-Context` 再转发；浏览器看不到这个头，自己带了也会被忽略。
写这一节时（2026-10）线上还没部署 Agent，登录后访问 `/api/agent/**` 返回 503，最新进度看 Agent 仓库
`docs/STATUS.md`。本节与该仓库 `docs/java-contract.md` 有出入时以那边为准，并回来更新这里。

### 本地起三端

```bash
# Agent 仓库（需要 uv 和 Docker）
make setup && make db && make migrate   # 首次：装依赖、生成 .env、起 PostgreSQL、建表
make api                                # 终端 1：127.0.0.1:8090
make worker                             # 终端 2：不起它，run 会一直停在 QUEUED
# 后端仓库（JDK 17 或 21，本机 MySQL 与 Redis）
mvn spring-boot:run                     # localhost:8080
# 本仓库
cd wiki && npm run dev                  # /api 代理到 localhost:8080
```

- 后端和 Agent 的 `.env` 配**同一个** `AGENT_GATEWAY_SECRET`。Agent 的 `.env.example` 里 `AGENT_AUTH_MODE=dev`、
  `AGENT_GATEWAY_SECRET` 是注释掉的：Agent 能起来，但经网关的请求一律 HTTP 401「缺少有效的执行上下文」，原因是密钥没配，
  不是没登录。dev 模式下配上密钥就能校验网关的头，不必改成 `jwt`（`jwt` 模式缺密钥时 Agent 直接起不来）。
  `AGENT_GATEWAY_AUDIENCE` 改了也要两边一致。
- 后端找 Agent 用 `AGENT_BASE_URL`（默认 `http://127.0.0.1:8090`）；Agent 没起时只有 Agent 这两组接口返回 503。
- 密钥各管各的：`AGENT_GATEWAY_SECRET`（网关签上下文）、`JWT_SECRET`（用户登录）、`AGENT_TOOLS_SECRET`（Agent 将来调后端
  内部接口用；后端还没实现这组接口，fake 模式不用配）。值不能混用，也都不要写进本仓库。
- Agent 接口都要登录。本机新库可以在后端 `.env` 配 `WIKI_ADMIN_EMAIL` / `WIKI_ADMIN_PASSWORD` 得到超级管理员；要看普通用户
  的效果就注册一个账号，`MAIL_ENABLED=false`（默认）时验证码打印在后端日志里。
- Agent 默认 `AGENT_MODEL_PROVIDER=fake`、`AGENT_WIKI_TOOLS=fake`：不需要模型密钥，回答是固定的演示句子，引用指向站上不存在的
  `demo/` 页面。Agent 仓库 `dev` 分支起支持 `AGENT_DEV_CORPUS_PATH`，换成三篇真实公开页面的快照（只能配合
  `AGENT_WIKI_TOOLS=fake`）；要在本地点开这些引用，本机数据库里也得有这几页。
- 造界面状态（改了 `.env` 要重启 `make api`，`--reload` 只看代码）：
  - 新会话里问「那呢」→ 澄清；澄清时选「结束」→ `notice`；问一个和语料毫无重合的问题 → `notice`（资料不足）。
  - `AGENT_DAILY_BUDGET_MICROS_ONLINE=0` → 429；`AGENT_RUN_DEADLINE_SECONDS=0` → 超时失败；停掉 Agent → 503。
  - 先停掉 worker 再提问 → 一直排队，这时点停止 → 当场 `CANCELLED`。fake 模式一秒内就答完，执行中点停止几乎总是收到 `done`。
- Vite 代理不用改就能透传事件流（不缓冲、不加超时，浏览器断开时会关掉上游）。

### 接口

都要登录；浏览器侧路径都带 `/api` 前缀。

```
POST /agent/runs                 创建，HTTP 202，data = {runId, status, conversationId, deduplicated}
GET  /agent/runs/{id}            快照：result / error / waiting，见「结果在快照里」
GET  /agent/runs/{id}/events     事件流，见下
POST /agent/runs/{id}/resume     回答澄清，返回快照（status 回到 QUEUED）
POST /agent/runs/{id}/cancel     停止；幂等，不带 body，返回快照
POST /agent/feedback             {runId, rating: 'up' | 'down', reason?, comment?}
GET  /agent/usage                本人当日在 Agent 侧的次数与花费，不是额度；暂不做界面
/admin/agent/...                 维护任务、知识缺口、索引状态，仅 ADMIN / SUPER_ADMIN
```

- 创建 run 的 body 是 `{requestId, kind, message, conversationId?, scope?}`：
  - `requestId` 8–128 字符，每次新提交用新的 `crypto.randomUUID()`，重试同一次提交时复用。同一用户下它永久唯一，拿旧 id
    配新内容会 409。网关只转发 `Content-Type`、`Accept`、`Last-Event-ID` 和 query，幂等信息只能放 body。
  - `kind` 现阶段只用 `ask`。`message` 1–4000 字，按码点数（`[...s].length`）在前端先校验。
  - `scope` 省略这个键就是全站，别传 `null`（会 422）。「问这篇」传 `{type: 'page', pagePath}`（不带 `/docs/`），按篇章传
    `{type: 'category', categorySlug}`。
  - 字段一律 camelCase，多传任何字段都是 422。所有 id 按字符串处理。
- `resume` 的 body：补充说明是 `{interruptId, decision: 'reply', message}`（`message` 去掉空白后不能为空，否则 400），
  结束澄清是 `{interruptId, decision: 'reject'}`。
- 带 body 的请求必须是 `Content-Type: application/json`（axios 传对象时默认就是，用 `fetch` 要自己设），否则网关回
  HTTP 200 + `code: 500`，看着像后端故障。
- 同一会话同时只能有一个排队或执行中的 run，否则 409；run 进行中禁用输入框。等待澄清时会话不锁，但另发新问题会让旧
  run 的 `resume` 409：这时输入框只用来回答澄清，要问别的就开新会话。
- 反馈的 `reason` 取 `off_topic` / `wrong_source` / `outdated` / `missing` / `other`，`comment` 最多 1000 字。

### 响应与错误（和站内其它接口不一样）

- 其它接口一律 HTTP 200 + `code`；Agent 接口透传 Agent 的真实状态码：成功 200 / 202 + `{code: 0, data}`，失败
  4xx / 5xx + `{code: <同状态码>, message}`。`net/index.js` 的 `internal*` 遇到非 2xx 只会弹「发生了一些错误，请联系管理员」，
  所以 Agent 请求要自带 error 回调，读 `err.response.status` 和 `err.response.data.message`，并把 HTTP 状态单独传出去。
  别照抄 `listComments` 把 HTTP 状态塞进 failure 的 code 参数，否则下一条说的两种 401 分不开。
- 网关自己拦下的仍是 HTTP 200：未登录 `code: 401`，无权限 `403`，body 不是 JSON 对象 `400`，Content-Type 不是 JSON `500`。
  只有这种 HTTP 200 的 401 表示要登录。HTTP 401（body 里同样是 `code: 401`）是网关与 Agent 的密钥或 audience 配置问题：
  不要因此清 token，也不要把它的 message 展示给用户。
- Agent 的字符串错误码（`CONVERSATION_BUSY` 等）被网关丢掉了，只能按 HTTP 状态分支：400 请求不合法，404 run 不存在，
  409 会话忙或状态冲突（刷新快照），410 澄清已过期，429 当日预算用完，503 Agent 不可用，502 Agent 返回了坏数据。
  429 / 502 / 503 都退回「搜索文档」入口。
- Agent 自己报的错 `message` 是中文，可以直接展示（401 除外）；FastAPI 自动生成的错误（422 校验失败、路由不存在的
  404 / 405）`message` 是 JSON 文本。`message` 以 `[`、`{` 或 `"` 开头时换成通用文案。
- axios 全局超时 20 秒，比网关等 Agent 的上限短。创建 run 是异步的不受影响；以后要同步等 Agent 的请求单独设 `timeout`。

### 事件流

- 用 `fetch` + `ReadableStream` 自己解析，带 `Authorization: Bearer <token>` 和 `Accept: text/event-stream`，用
  `AbortController` 关闭。token 用 `net/index.js` 的 `takeAccessToken()` 取：`localStorage` 里存的是 `{token, expire}` 的
  JSON，不是 token 本身。`EventSource` 带不了请求头（后端只认 `Authorization`），axios 有全局超时，都不能用。
  订阅函数也放进 `net/index.js`；`fetch` 不走 axios 的 `baseURL`，路径要写全 `/api/agent/...`。解析和续传逻辑抽成
  `wiki/src/utils/` 里的纯函数并补单测。
- 先看 `res.ok`，再看 `content-type` 是否**以** `text/event-stream` 开头（实际带 `; charset=utf-8`，不要全等比较）。
  不是就试着按 JSON 读错误：`{code, message}`（网关），或 Agent 原样透传的 `{error: {code, message}}`、`{detail}`；
  读不出（Agent 未处理的异常是纯文本 500）就按 HTTP 状态给通用文案。
- 帧是 `id: <seq>`、`event: <type>`、`data: <单行 JSON>`。`snapshot` 帧没有 `id:`，游标只在帧带 id 时更新（否则重连会带上
  `cursor=undefined`，得到 422）。空闲时每 15 秒有一行 `: ping`：不当事件处理，但用来判断连接还活着。
- 事件：
  - `status` `{status, stage?, message?, …}`：一订阅就能收到。最新一条还是 `QUEUED`（没有 `message`）时显示「排队中」，
    不是错误；之后的 `message` 是给用户看的中文，只显示最新一条。
  - `sources` `{sources: [{sourceId, path, anchor, title, headingPath, excerpt, …}]}`。
  - `delta` `{text}`：结果校验完才分段发出，不是逐字流式；累加后整体渲染，不做打字机效果。`notice` 的文字也走 `delta`，
    所以不能凭 `delta` 断定是回答。
  - `clarification_required` `{interruptId, question}`：显示问题，`resume` 之后带游标重新订阅。
  - `done` `{status: 'SUCCEEDED'}`；`error` `{status, code, message}`（取消、失败、过期都走它）。
  - `proposal`、`confirmation_required` 是编辑助手用的，现在不做；`snapshot` 在游标太旧时先给整份快照。
- 断线续传：
  - 收到 `done`、`error`、`clarification_required`、`confirmation_required` 后服务端会关流，不要重连。
  - 流正常结束却没收到这四种，先 `GET` 快照：状态已定（终态或 `WAITING_USER`）就按快照收尾，否则带
    `?cursor=<最后的 id>`（或 `Last-Event-ID`）退避重连。直接重连会死循环：run 已结束且游标追平时，服务端一个事件都不发就关流。
  - 经后端网关的流本地和线上都会被定期切断，续传是必需的，不是兜底。全站同时推送的流有上限，超出的连响应头都不回：
    从发起 `fetch` 起约 45 秒没收到任何字节（包括 `: ping`）就 abort，再带游标重连。
  - 每个 run 只开一条流，结束或组件卸载就 abort。
- 停止：断开订阅不会取消 run，要调 `cancel`。它返回快照：排队中或等待澄清的 run 当场变 `CANCELLED`；执行中的只打标记，
  worker 约 10 秒内发现才停，期间仍可能以 `done` 结束。快照已是终态就收尾，否则继续听流，直到 `error` 或 `done`。
- 结果在快照里（刷新页面也照这样恢复）：
  - `SUCCEEDED` 读 `result.type`：`answer`（`text`、`citations`、`sources`、`unresolved`、`reduced`）或 `notice`（`reason`、
    `text`，可能带 `sources`）。`notice.reason` 分资料不足、校验未过、用户结束澄清和拒答几类，按它选文案。
  - `FAILED` / `CANCELLED` / `EXPIRED` 的 `result` 为 null，读 `error {code, message}`。
  - `WAITING_USER` 读 `waiting {interruptId, type, question}` 和 `waitingExpiresAt`；`QUEUED` / `RUNNING` 用 `cursor=0` 重新订阅。
  - 澄清默认等 24 小时，过期后 run 变 `EXPIRED`，`resume` 返回 410 或 409：都按过期处理，引导用户重新提问。

### 回答与引用

- `answer.text` 是模型写的 Markdown，带 `[S1]` 这样的来源标记。Agent 不做 HTML 消毒，证据又来自用户投稿，所以一律交给
  `MarkdownRenderer`（DOMPurify），不要 `v-html`；`excerpt`、`question`、`message` 按纯文本显示。
- 交给 markdown-it 之前，先把 `[S#]` 换成自己拼的引用链接，并删掉**所有** `[...]: ...` 形式的引用定义行（标签不分大小写）：
  否则模型被投稿内容诱导写出 `[S1]: https://…` 时，正文里的 `[S1]` 全会变成外站链接，DOMPurify 不会拦。
- 引用链接由前端拼：`/docs/` 加上 `path`（每段 `encodeURIComponent`，标题里可能有空格和括号），`anchor` 非空时再加
  `#${anchor}`。直接用服务端给的 `anchor`（null 或空串按没有处理），不要自己对标题 slugify，也不要用模型写的链接。
- `anchor` 只是尽力而为：本地由 Agent 的 `chunker.py` 算，线上将来由后端的检索接口算（契约要求与 `slug.js` 一致）。
  已知偏差：不给重复标题加 `-1` / `-2`；slug 为空时给空串（前端是 `h-N`）；不识别 setext 标题，也不识别缩进、引用块、
  列表里的标题（算进前一个 `#` 标题）；不知道 DocPage 会去掉正文开头的 H1。引用会落到第一个同名标题、前一个标题或页顶。
- 放进文档页时注意：`MarkdownRenderer` 也会给回答里的标题加 id，可能和正文标题撞名。

### 现阶段只做

- 问答（`kind=ask`）、进度、来源与引用、澄清、停止、点赞 / 点踩。未登录时不显示输入框，给登录和搜索入口。
- 先别做：编辑助手、维护任务、额度或用量界面、会话历史列表（没有列出 run 的接口，要「接着问」就在本地记 `runId` /
  `conversationId`）。这些等 Agent 仓库 `docs/STATUS.md` 标为完成再排。

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
  - `WikiIcon` 只出现在首页篇章卡片的标题、侧栏顶层篇章、编辑器的图标选择、后台的分类 / 页面管理和
    搜索面板的条目里；其余标题（文档页标题、区块标题等）、元信息和普通列表行不加图标。
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
- 涉及 Agent：本机三端（fake 模式）实际跑过提问、澄清、停止、断开后续传，以及停掉 Agent 时的 503 降级。
- PR 描述写清改了什么、怎么验证的、哪些没验证到。
