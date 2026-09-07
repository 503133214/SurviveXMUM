# 本地开发

本页说明如何在本地启动 SurviveXMUM、验证修改并提交 Pull Request。内容文章本身由数据库维护；如果只是修改 Wiki 内容，请优先阅读[贡献指南](/docs/贡献指南)，无需搭建开发环境。

## 技术栈与运行结构

```text
浏览器
  └─ Vue 3 + Vite + Element Plus
       └─ /api（开发代理会移除此前缀）
            └─ Spring Boot 3 / Java 17
                 ├─ MySQL：页面、投稿、版本、用户等持久数据
                 ├─ Redis：验证码与已退出 JWT 黑名单
                 └─ MinIO：用户上传图片（本地可关闭）
```

前端不会在构建时读取 Markdown 文件。导航调用 `GET /wiki/manifest`，正文调用 `GET /wiki/page`，所有公开内容以数据库为准。

## 环境要求

- Git
- JDK **17 或 21**（不要用更新的 JDK：Lombok 注解处理会失败，编译时报大量“找不到符号 getXxx()”。macOS 上可用 `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test` 显式指定）
- Maven 3.9+
- Node.js 20+ 与 npm
- MySQL 8.x
- Redis 6+ 或 7+
- MinIO（仅在需要调试图片上传时安装）

Docker 不是日常本地开发的必需项，但生产镜像和整体联调使用 Docker Compose。

## 获取代码

```bash
git clone https://github.com/503133214/SurviveXMUM.git
cd SurviveXMUM
git checkout dev
git switch -c feature/your-feature
```

建议从最新 `dev` 创建功能分支；不要把生产环境文件、Token 或数据库导出提交到仓库。

## 准备 MySQL

创建本地数据库：

```sql
CREATE DATABASE IF NOT EXISTS wiki
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

后端启动时会依次执行 `schema.sql` 和 `migration-v*.sql`。迁移必须可重复执行，因为当前初始化机制不是 Flyway，不会自动记录某个脚本是否执行过。

## 配置后端

```bash
cd backend
cp .env.example .env
```

至少检查以下变量：

```ini
SPRING_PROFILES_ACTIVE=dev

DB_HOST=localhost
DB_PORT=3306
DB_NAME=wiki
DB_USER=root
DB_PASSWORD=<local-password>

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DB=1

JWT_SECRET=<local-long-random-secret>
MAIL_ENABLED=false
MINIO_ENABLED=false
```

`.env`、`.env.prod` 和任何真实密钥都不应进入 Git。若要测试图片上传，再启用 MinIO 并填写 endpoint、access key、secret key、bucket 和浏览器可访问的 public URL。

## 启动后端

```bash
cd backend
mvn spring-boot:run
```

验证：

```bash
curl http://localhost:8080/health
curl http://localhost:8080/wiki/manifest
```

本地直连后端时没有 `/api` 前缀。`/health` 只是轻量存活响应，不探测外部依赖；`/wiki/manifest` 成功可确认数据库读取链路，Redis、邮件和 MinIO 仍应通过各自相关功能单独验证。

## 启动前端

另开一个终端：

```bash
cd wiki
npm ci
npm run dev
```

打开 Vite 输出的本地地址。开发服务器会把 `/api/*` 代理到 `http://localhost:8080/*`，因此前端代码始终使用同源 `/api`，不要把生产域名写死在组件中。

## 项目结构

```text
backend/
  src/main/java/wiki/xmum/
    controller/       HTTP 路由与基础参数接收
    service/          业务规则与事务
    mapper/           MyBatis-Plus 数据访问
    domain/dto/       请求对象
    domain/vo/        对外响应对象
    security/         JWT、角色和当前用户
    common/           统一响应封装、业务异常与全局异常处理
    config/           Jackson、MyBatis-Plus、MinIO、内置文档初始化
    util/             Markdown、JSON、标题与分类标识校验
  src/main/resources/
    db/               建表与幂等迁移
    wiki-content/     内置开发文档（改动规则见下一节）

wiki/
  src/net/            前端 API 封装
  src/wiki/           manifest、页面、搜索、标签和相邻页门面
  src/components/     通用 UI 与 Markdown 渲染
  src/views/          路由页面
  src/router/         Vue Router 路由与权限守卫
  src/store/          Pinia（当前登录用户）
  src/composables/    主题等可复用逻辑
  src/utils/          纯函数工具（含独立单测）
  src/directives/     自定义指令
  public/docs/        仅保留历史静态图片，不再保存文章 Markdown
```

## 修改内置开发文档

你正在读的这三页（`api/api-overview`、`api/endpoints`、`api/development`）比较特殊：它们的源文件在
`backend/src/main/resources/wiki-content/api/`，但站点内容以数据库为准，所以由
`DeveloperDocsSeeder` 在每次启动时同步。

同步是**保守**的：只有当线上页面的正文仍然逐字节等于某个“曾经内置过”的版本时才会被覆盖，
这样管理员在后台手工改过的内容永远不会被启动流程冲掉。因此：

> 改这三个 `.md` 之前，先记下当前文件的 sha256，并把它加进
> `DeveloperDocsSeeder.REPLACEABLE_BUNDLED_HASHES` 对应条目。

```bash
shasum -a 256 backend/src/main/resources/wiki-content/api/endpoints.md   # 先算，再改
```

只改文件而不登记哈希，线上那一页会被当成“管理员手工版本”而永远停在旧内容上——
本地看着是新的，线上没变，且不会有任何报错。

## 内容与发布流程

### 普通投稿

1. 用户保存草稿（可选）。
2. `POST /wiki/revision` 创建待审核投稿。
3. 管理员在后台审核通过或驳回。
4. 通过时事务内更新公开页、追加 `wiki_page_version` 快照并发送通知。

### 管理员修改

管理员直接更新页面也必须追加公开快照。新增发布入口时，不要只覆盖 `wiki_page`，应复用版本服务，确保公开历史和关注通知完整。

### 公开读取

所有公开查询都必须同时限制：

```text
status = PUBLISHED AND deleted = 0
```

新增公开接口时还需要在 `SecurityConfig` 明确放行；否则默认要求登录。

### 页面贡献者数据

页面底部的贡献者列表调用公开接口 `GET /contributors/page?path=...`。它是从已经公开发布的编写、修改事件派生的数据，不是独立维护的署名名单：

- 先按发布事件去重，再以稳定的用户 ID 聚合；不要按昵称或打码邮箱聚合，因为昵称可能变化，不同用户也可能得到相同显示文本。
- `count` 只表示该用户对请求页面的已发布编写、修改事件数。全站贡献榜中的 `count` 是全站口径，两者不能混用。
- 同一投稿对应的发布快照按投稿 ID 去重；管理员直接编写或修改的发布动作逐次计数，回滚、恢复操作不计数。旧数据中缺少版本快照的已通过投稿用于补齐；仅当某个可识别作者没有其他可计数事件时，才允许其迁移基线补作一次贡献，不可识别人类作者的系统迁移不应凭空生成贡献者。
- 结果返回本页全部可确认贡献者，按贡献次数降序、最近贡献时间降序、显示名排序。公开页没有可确认的人类贡献时应返回空数组。
- 当前用户的展示名优先使用昵称，否则使用打码邮箱；用户删除或无法关联当前账户后，只能返回发布时保存的公开显示名，并把 `userId`、头像置空。任何查询、日志 DTO、快照和响应 VO 都不得保存或返回原始邮箱。
- 接口必须先确认页面满足 `PUBLISHED` 且 `deleted = 0`。空路径、缺失页面和非公开页面统一按 404 处理，避免泄露未发布内容是否存在。

前端应直接使用页面级接口，不要下载全站贡献榜后逐个请求个人主页，也不要按公开版本历史里的显示名自行去重。

### 贡献者徽章

徽章同样是派生数据，没有授予记录表，规则集中在 `BadgeCatalog`：

- 全部口径来自已通过投稿与可见讨论，改规则只需要改这一个文件，不涉及迁移。
- 徽章按**家族**组织（投稿量、新建、校对、广度、讨论、资历）。同一家族只返回已达成的最高一档，
  否则一个老贡献者会同时挂着三枚在说同一件事的徽章。
- 贡献榜只返回已获得的徽章；个人主页额外返回每个家族里尚未达成的下一档，附 `progress` / `target`，
  用于展示进度。
- 新增徽章时注意成本：榜单会为上榜的每个人计算一次，任何新口径都应能从**已经查出来的数据**里算，
  或者只对上榜用户补一次批量查询，不要引入按人循环的查询。

### 页面讨论

讨论只有两层：`parent_id` 记录“回复了谁”（用于显示 `@`），`root_id` 记录楼层归属并从父评论继承，
因此回复回复也不会产生第三层。作者自删与管理员隐藏都是软删除；主楼被隐藏或自删后，
只要楼里还有可见回复，就保留一个**不含作者信息**的占位，避免回复变成孤儿。

## 数据库变更

1. 新建下一个 `backend/src/main/resources/db/migration-vN.sql`。
2. 使用 `IF NOT EXISTS`、information_schema 检查或等价方式保证重复执行安全。
3. 把脚本追加到 `application.yml` 的 `spring.sql.init.schema-locations`。
4. 同时更新 `schema.sql`，确保全新数据库直接得到最新结构。
5. 涉及公开页面内容时，要同步考虑版本快照、关注通知和历史隐私。

不要在应用启动迁移中写不可重复的随机数据，也不要让每次重启覆盖后来由管理员编辑的 Wiki 内容。

## 测试与构建

后端：

```bash
cd backend
mvn clean test
```

前端（`node --test` 跑 `wiki/test/*.test.js` 里的纯函数单测）与生产构建：

```bash
cd wiki
npm test
npm run build
```

后端的 Service 单测直接 `new` 出被测对象并注入 Mock，因此**给 Service 增加构造参数时必须同步改测试**，
否则编译不过（`PageAdminServiceTest`、`ContributorServiceTest`、`CommentServiceTest` 都属于这类）。
生产镜像构建时会 `-DskipTests`，测试只在本地和 CI 跑，不要指望部署环节帮你发现问题。

提交前还建议执行：

```bash
git diff --check
git status --short
```

涉及页面交互时，请至少检查桌面端和约 390px 宽的移动端；涉及 API 权限时，应同时验证公开、未登录、普通用户和管理角色的边界。

## 编码约定

- 后端保持 Controller 薄、Service 管业务与事务、Mapper 只做数据访问。
- 含敏感或内部字段的数据必须使用专用 VO，不能直接暴露带邮箱、审核意见等字段的持久化对象。
- 前端请求集中在 `wiki/src/net/index.js`，避免组件各自拼接鉴权逻辑。
- 用户提供的 Markdown/HTML 一律视为不可信输入。
- JavaScript 中的后端 ID 始终作为字符串处理。
- 不要提交与当前需求无关的大规模格式化或重命名。

## 提交 Pull Request

```bash
git add <files>
git commit -m "feat: describe the change"
git push origin feature/your-feature
```

向 `dev` 分支发起 Pull Request，并说明：

- 问题与实现方案
- 数据库或配置变化
- 已执行的测试
- 界面变化的桌面端/移动端截图
- 可能的回滚方式

生产部署由维护者执行，步骤见仓库根目录的 `DEPLOY.md`。外部贡献者不需要、也不应获取生产服务器或生产环境变量。

## 常见问题

| 现象 | 排查方向 |
|---|---|
| 后端无法连接数据库 | 检查数据库是否创建、端口、用户权限和 `.env` 加载目录 |
| Redis 连接失败 | 检查 Redis 是否启动、密码与 `REDIS_DB` |
| `/api/*` 返回 HTML | 本地检查 Vite proxy；生产检查 nginx `/api/` 反代 |
| 图片上传接口 404 | `MINIO_ENABLED=false` 时接口不会注册 |
| 图片上传后 403 | 检查 bucket 只读策略与 `MINIO_PUBLIC_URL` |
| 前端路由刷新 404 | 静态服务器需要 SPA fallback 到 `index.html` |
| API 看似 HTTP 200 但操作失败 | 检查 JSON 响应体里的业务 `code` 和 `message` |
| 编译报大量“找不到符号 getXxx()” | JDK 太新导致 Lombok 失效，改用 JDK 17 或 21 |
| 改了内置开发文档但线上没变 | 没有把改动前的 sha256 登记进 `DeveloperDocsSeeder` |
| 部署后页面空白，刷新就好 | 旧标签页在用被替换掉的旧 chunk；容器 nginx 有自愈脚本，本地调试直接硬刷新 |

更多调用细节见[接口参考](./endpoints.md)。
