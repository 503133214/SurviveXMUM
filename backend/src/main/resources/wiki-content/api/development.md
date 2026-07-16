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
- JDK 17 或更高版本
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
  src/main/resources/
    db/               建表与幂等迁移
    wiki-content/     首次初始化的内置 Wiki 文档

wiki/
  src/net/            前端 API 封装
  src/wiki/           manifest、页面、搜索和相邻页门面
  src/components/     通用 UI 与 Markdown 渲染
  src/views/          路由页面
  src/router/         Vue Router 路由与权限守卫
  public/docs/        仅保留历史静态图片，不再保存文章 Markdown
```

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

前端链接单元测试与生产构建：

```bash
cd wiki
npm test
npm run build
```

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

生产部署由维护者执行。外部贡献者不需要、也不应获取生产服务器或生产环境变量。

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

更多调用细节见[接口参考](./endpoints.md)。
