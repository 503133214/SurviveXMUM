# 部署文档（Deployment Runbook）

SurviveXMUM 线上部署说明。后端 Spring Boot + 前端 Vue，均以 Docker 容器运行，
复用服务器上已有的 MySQL / Redis / MinIO，由宿主机 nginx 统一反向代理。

> 最后更新：2026-09-04。改动了部署相关文件（`deploy.sh`、`docker-compose.yml`、
> 两个 `Dockerfile`、`wiki/nginx.conf`、`application.yml` 的迁移列表）时，请同步更新本文。

---

## 1. 架构总览

```
公网 https://surivivexmum.wiki   (宿主机 nginx, Let's Encrypt 证书, 80/443)
│
├── /          → 前端容器  127.0.0.1:8081     (nginx 托管 vite 构建产物)
├── /api/      → 后端容器  :8080              (host 网络, nginx 去掉 /api 前缀)
└── /wiki/     → MinIO     127.0.0.1:9000     (图片对象存储, 走 https 域名)

后端容器使用 host 网络 → 容器内 localhost 直连宿主机现有：
    MySQL  127.0.0.1:3306
    Redis  127.0.0.1:6379
    MinIO  127.0.0.1:9000   (本身是 docker 容器，但不在本项目 compose 内)
```

- **数据服务（MySQL/Redis/MinIO）不在 compose 内**，复用宿主机现有实例，部署不影响数据。
- **部署分支：`dev`**，仓库位于服务器 `/opt/SurviveXMUM`。
- 前后端产物都在 **Docker 内构建**，本地不需要先 build。

---

## 2. 仓库里的部署文件

| 文件 | 作用 |
|---|---|
| `docker-compose.yml` | 编排 `backend` + `frontend` 两个容器 |
| `backend/Dockerfile` | Maven 编译 → JRE17 运行 `wiki-backend.jar`（**构建时 `-DskipTests`**） |
| `wiki/Dockerfile` | Node 20 构建 → nginx 1.27 托管静态文件 |
| `wiki/nginx.conf` | 前端容器内部 nginx：静态托管 + SPA 回退 + **缓存策略**，见第 6 节 |
| `backend/src/main/resources/db/*.sql` | 建表与迁移脚本，后端启动时自动执行，见第 5 节 |
| `backend/.env.example` | 生产环境变量模板 |
| `deploy.sh` | 一键部署脚本 |
| `backend/.env.prod` | **生产环境变量，含密钥，已 gitignore，只存在于服务器** |

---

## 3. 日常部署（已上线，最常用）

代码合并到 `dev` 并 push 后，登录服务器执行一条命令：

```bash
ssh root@83.229.122.162
cd /opt/SurviveXMUM
./deploy.sh
```

`deploy.sh` 会依次：
`git pull --ff-only` → `docker compose up -d --build --force-recreate` → `docker image prune -f` → `docker compose ps`。

> - 首次构建约 3–5 分钟，之后有缓存约 1–2 分钟。期间后端有约 15 秒中断。
> - `--force-recreate` 是必须的：只有镜像内容变化时 compose 默认可能不重建容器，
>   表现为「构建成功但 `docker compose ps` 还是几小时前的 Up」。
> - **镜像构建跳过测试**（构建机没有 MySQL/Redis）。测试要在本地或 CI 跑，见第 9 节。
> - **数据库迁移随后端启动自动执行**，无需手工进 MySQL。

部署完成后做 [第 7 节验证](#7-验证)。

---

## 4. 首次部署 / 换新服务器

> 前提：服务器已装好 Docker + docker compose，且已有 MySQL、Redis、MinIO 在运行，
> 域名已解析并配好 HTTPS 证书（本项目用 1Panel 签 Let's Encrypt）。

### 4.1 拉代码

```bash
cd /opt
git clone https://github.com/503133214/SurviveXMUM.git
cd SurviveXMUM
git checkout dev
```

### 4.2 准备生产环境变量 `backend/.env.prod`

复用已有的 env（若有），并**确保包含 MinIO 段**（注意：历史 `/etc/wiki-backend.env` 缺 MinIO 配置）：

```bash
cp /etc/wiki-backend.env backend/.env.prod   # 若已有；否则参考 backend/.env.example 新建
```

`backend/.env.prod` 必须包含（密钥用实际值替换）：

```ini
# 数据库（host 网络，用 localhost）
DB_HOST=localhost
DB_PORT=3306
DB_NAME=wiki
DB_USER=wiki
DB_PASSWORD=<生产数据库密码>

# Redis（生产无密码则 REDIS_PASSWORD 留空/不写）
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_DB=1

JWT_SECRET=<长随机串>

# 邮件、管理员等（按 .env.example）
MAIL_ENABLED=true
SMTP_HOST=...
WIKI_ADMIN_EMAIL=...
WIKI_ADMIN_PASSWORD=...

# MinIO —— 必须补全，否则图片上传失败
MINIO_ENABLED=true
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=<minio access key>
MINIO_SECRET_KEY=<minio secret key>
MINIO_BUCKET=wiki
MINIO_PUBLIC_URL=https://surivivexmum.wiki/wiki
```

> `docker-compose.yml` 会给后端注入 `SPRING_PROFILES_ACTIVE=prod`。
> 不要用默认的 `dev` profile 跑生产：日志级别与连接池配置都不一样。

### 4.3 设置 MinIO 桶为匿名只读（修 403）

图片要能被浏览器匿名读取，桶必须开放只读：

```bash
docker run --rm --network host \
  -e MC_HOST_sv="http://<ACCESS_KEY>:<SECRET_KEY>@localhost:9000" \
  minio/mc anonymous set download sv/wiki
```

验证：`curl -s -o /dev/null -w "%{http_code}\n" http://localhost:9000/wiki/`（能连通即可）。

### 4.4 配置宿主机 nginx 反向代理

在站点 vhost（本项目：`/etc/nginx/sites-available/vue-app`）的 HTTPS server 块内加入：

```nginx
client_max_body_size 20m;   # 允许图片上传

location /api/ {            # 后端，去掉 /api 前缀
    proxy_pass http://127.0.0.1:8080/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}

location /wiki/ {           # MinIO 图片
    proxy_pass http://127.0.0.1:9000;
    proxy_set_header Host $host;
}

location / {               # 前端容器
    proxy_pass http://127.0.0.1:8081;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

```bash
nginx -t && systemctl reload nginx
```

> 宿主机 nginx 必须是**纯透传**：不要加 `proxy_cache`，否则会盖掉容器发出的
> `Cache-Control`，第 6 节的缓存策略就失效了。

### 4.5 停掉旧的裸 jar 部署（如果有）

```bash
systemctl stop wiki-backend && systemctl disable wiki-backend
```

### 4.6 起容器

```bash
cd /opt/SurviveXMUM && ./deploy.sh
```

---

## 5. 数据库迁移

站点内容全部在数据库里，建表与结构变更由 Spring 在**每次后端启动时自动执行**，
脚本在 `backend/src/main/resources/db/`，执行顺序写死在 `application.yml`：

```yaml
spring.sql.init.schema-locations:
  classpath:db/schema.sql, classpath:db/migration-v2.sql, ... , classpath:db/migration-v11.sql
spring.sql.init.continue-on-error: false
```

规则：

1. **所有脚本每次启动都会重跑**，因此**必须写成可重复执行**：
   建表用 `CREATE TABLE IF NOT EXISTS`；加列/加索引用
   `information_schema` 判断 + `PREPARE/EXECUTE` 的动态 DDL（照抄 `migration-v8.sql` 的写法）。
2. **新增迁移必须同时做两件事**：新建 `migration-vN.sql`，并把它**追加到 `application.yml` 的
   `schema-locations` 末尾**。只加文件不登记 = 线上不会执行。
3. `continue-on-error: false` 意味着**迁移报错后端就起不来**。这是有意的（宁可不启动也不要半套结构），
   排查见第 10 节。
4. 迁移只改结构、不删数据。要清理数据请单独走人工 SQL 并先备份。

---

## 6. 前端缓存策略（改 `wiki/nginx.conf` 前先读）

「用户不清缓存就白屏 / 看到旧版本」是本项目复发过多次的 bug，现在的规则是治本方案，
**不要为了「简化配置」把它删掉**：

| 路径 | 头 | 原因 |
|---|---|---|
| `/index.html`（含所有 SPA 路由回退） | `no-cache, must-revalidate` | 不加的话浏览器按 `Last-Modified` 启发式缓存，部署后仍用旧 index → 指向已被 prune 的旧 chunk → 白屏 |
| `/assets/*` | `immutable, max-age=31536000` | 文件名带 hash，内容变即换名，可以永久缓存 |
| `/assets/*` 未命中 | `@stale_asset` 返回 200 自愈脚本 | 已经缓存了旧 index 的老客户端跑的是旧 JS，新代码触达不到它们；服务器对缺失 chunk 不返回 404，而是返回一段脚本强制重取 index 并整页刷新（`sessionStorage` 防循环，`main.js` 挂载成功后清除） |

`wiki/index.html` 里还有一个捕获阶段的 `error` 监听，作用相同（入口脚本 404 时自愈）。

---

## 7. 验证

```bash
D=surivivexmum.wiki
curl -sI  https://$D/                       # 首页 200
curl -s   https://$D/api/wiki/manifest      # 应为 JSON，不是 HTML
curl -s   https://$D/api/health             # {"code":0,...,"status":"UP"}
curl -s -o /dev/null -w "%{http_code}\n" \
          https://$D/wiki/                  # MinIO 反代连通

# 缓存头是否生效（回归第 6 节最常坏的一项）
curl -sI https://$D/ | grep -i cache-control          # 应为 no-cache, must-revalidate
```

容器侧：

```bash
cd /opt/SurviveXMUM
docker compose ps                    # 两个容器都应是 Up 且 CREATED 是刚刚
docker compose logs backend --tail 30 | grep -i "Started WikiApplication\|ERROR"
```

**最终人工确认**：浏览器登录 → 上传一张图片 → 链接应为 `https://surivivexmum.wiki/wiki/...`，
预览与发布后均正常显示。（此环节需登录态，curl 测不了。）

---

## 8. 常用运维命令

```bash
cd /opt/SurviveXMUM

docker compose ps                  # 容器状态
docker compose logs -f backend     # 后端日志
docker compose logs -f frontend    # 前端日志
docker compose restart backend     # 只重启后端（不重新构建）
docker compose down                # 停止全部容器（不影响 MySQL/Redis/MinIO）
docker compose up -d --build       # 重建并启动
```

---

## 9. 本地开发要点

```bash
# 前端
cd wiki && npm ci && npm run dev        # /api 代理到 localhost:8080
npm run build                           # 部署前的 sanity check（真正的构建在 Docker 里）

# 后端测试（镜像构建会跳过，必须本地跑）
cd backend && mvn test
```

- 后端 target 为 **Java 17**，镜像里用的是 `maven:3.9-eclipse-temurin-17`。
  本地已验证 **JDK 17 / 21** 可用；用过新的 JDK（如 Homebrew 默认的 26）会让 Lombok
  注解处理失败、编译不过。macOS 上显式指定版本：
  `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test`。
- 后端跑起来需要本机有 MySQL + Redis；只改前端时不必起后端，改用线上环境验证。

---

## 10. 回滚

### 回滚到上一个版本（代码）
```bash
cd /opt/SurviveXMUM
git log --oneline -5
git reset --hard <上一个 commit>
./deploy.sh
```

> 注意：**代码能回滚，数据库结构不会自动回滚**。若上一版新增了迁移，回滚后旧代码
> 面对的是新结构——绝大多数情况没问题（迁移都是加列/加表），但删列类变更必须人工处理。

### 紧急回滚到旧的 systemd jar
容器方案出问题时，旧 jar 与 unit 文件都保留着：
```bash
cd /opt/SurviveXMUM && docker compose down   # 释放 8080
systemctl enable --now wiki-backend          # 重新跑旧 jar
```
（注意：旧 jar 没有 MinIO 配置，图片上传不可用；仅作应急。）

---

## 11. 常见问题

| 现象 | 排查 |
|---|---|
| 图片 403 | MinIO 桶没设匿名只读，见 4.3 |
| 图片链接还是 http+IP | `backend/.env.prod` 里 `MINIO_PUBLIC_URL` 没改成 https 域名，改完 `docker compose up -d` |
| `/api/*` 返回 HTML 而非 JSON | nginx 缺 `/api/` 反代（落到了 SPA fallback），见 4.4 |
| 后端起不来 | `docker compose logs backend`：① `.env.prod` 缺值或 DB/Redis 连不上；② **迁移脚本报错**（`continue-on-error: false` 会直接终止启动），看日志里 `ScriptStatementFailedException` 指的是哪个 `migration-vN.sql` 第几行 |
| 构建成功但还是旧版本 | 容器没重建：`docker compose up -d --force-recreate`（`deploy.sh` 已带该参数） |
| 8080 被占用 | 旧 `wiki-backend.service` 还在跑：`systemctl stop wiki-backend` |
| 上传大图失败 | nginx `client_max_body_size` 太小，见 4.4 |
| **白屏 / 看到旧版本，无痕正常** | 有**两个**互不相干的原因，别只查一个：① 缓存（见第 6 节，确认 `curl -sI https://域名/` 返回 `no-cache`）；② **广告拦截插件**——无痕模式默认禁用扩展，所以「无痕正常」不能证明是缓存问题。曾因 CSS 类名用了 `ad-` 前缀（`ad-head`/`ad-list`…）被 EasyList 通用规则整块隐藏。**前端类名一律不要用 `ad-` 开头。** |
| ssh 报 REMOTE HOST IDENTIFICATION HAS CHANGED | 服务器 2026 年 9 月重装过，主机密钥已更换。确认指纹无误后本地执行 `ssh-keygen -R 83.229.122.162` 再连 |

---

## 12. 关键信息速查

- 服务器：`root@83.229.122.162`（Ubuntu 22.04）
- 域名：`surivivexmum.wiki`（注意拼写 sur**i**vivexmum）
- 仓库路径：`/opt/SurviveXMUM`，分支 `dev`；GitHub：`503133214/SurviveXMUM`
- nginx vhost：`/etc/nginx/sites-available/vue-app`（改前自动备份为 `*.bak.*`）
- 后端 env：`/opt/SurviveXMUM/backend/.env.prod`（不在 git 内）
- 容器名：`wiki-backend`（host 网络 :8080）、`wiki-frontend`（127.0.0.1:8081）；
  `minio` 是独立容器，不归本项目 compose 管
- 数据：MySQL 库 `wiki`（专用用户 `wiki`，密码在 `.env.prod`）、Redis db1、MinIO 桶 `wiki`
