# 前端部署文档（Deployment Runbook）

SurviveXMUM **前端**的线上部署说明。Vue 构建产物由 nginx 容器托管，
由宿主机 nginx 反向代理。

> 前后端已拆成两个仓库，**各自独立部署、互不依赖**：
> - 前端（本仓库，公开）：`503133214/SurviveXMUM`
> - 后端（私有）：`503133214/SurviveXMUM-server`，数据库迁移、环境变量与后端运维见该仓库
>
> **本文是公开仓库的一部分，因此不写服务器地址、登录方式与主机上的绝对路径。**
> 这些放在私有仓库 `SurviveXMUM-server` 的 `DEPLOY.md` 里。下文用 `<部署目录>`
> 指代服务器上的前端仓库检出位置。
>
> 最后更新：2026-09-08。改动 `deploy.sh`、`docker-compose.yml`、`wiki/Dockerfile`
> 或 `wiki/nginx.conf` 时，请同步更新本文。

---

## 1. 架构总览

```
公网 https://surivivexmum.wiki   (宿主机 nginx, Let's Encrypt 证书, 80/443)
│
├── /          → 前端容器  127.0.0.1:8081     (本仓库, nginx 托管 vite 构建产物)
├── /api/      → 后端容器  :8080              (SurviveXMUM-server 仓库)
└── /wiki/     → MinIO     127.0.0.1:9000     (图片对象存储, 走 https 域名)
```

- **部署分支：`dev`**。
- 前端产物在 **Docker 内构建**（npm 在镜像里跑），本地不需要先 build。
- 后端是独立容器、独立仓库、独立部署命令；**发前端不需要动后端**，反之亦然。
- 内容全部来自后端 API，前端镜像里没有任何文章数据。

---

## 2. 仓库里的部署文件

| 文件 | 作用 |
|---|---|
| `docker-compose.yml` | 只编排 `frontend` 一个容器 |
| `wiki/Dockerfile` | Node 20 构建 → nginx 1.27 托管静态文件 |
| `wiki/nginx.conf` | 前端容器内部 nginx：静态托管 + SPA 回退 + **缓存策略**，见第 5 节 |
| `deploy.sh` | 一键部署脚本 |

前端没有环境变量文件：API 一律走同源 `/api`，由宿主机 nginx 反代。

---

## 3. 日常部署（已上线，最常用）

代码合并到 `dev` 并 push 后，登录服务器执行一条命令：

```bash
cd <部署目录>   # 服务器上的前端仓库检出位置
./deploy.sh
```

`deploy.sh` 会依次：
`git pull --ff-only` → `docker compose up -d --build --force-recreate` → `docker image prune -f` → `docker compose ps`。

> - **只影响前端容器**，后端不受影响（后端部署见 `SurviveXMUM-server` 仓库）。
> - 首次构建约 2–4 分钟，之后有缓存更快。切换瞬间完成，页面几乎无感。
> - `--force-recreate` 是必须的：只有镜像内容变化时 compose 默认可能不重建容器，
>   表现为「构建成功但 `docker compose ps` 还是几小时前的 Up」。

部署完成后做 [第 6 节验证](#6-验证)。

---

## 4. 首次部署 / 换新服务器

> 前提：服务器已装好 Docker + docker compose，域名已解析并配好 HTTPS 证书
> （本项目用 1Panel 签 Let's Encrypt）。后端与 MySQL/Redis/MinIO 的准备见
> `SurviveXMUM-server` 仓库的 `DEPLOY.md`。

### 4.1 拉代码

```bash
git clone https://github.com/503133214/SurviveXMUM.git
cd SurviveXMUM
git checkout dev
```

前端没有环境变量文件，拉下来即可构建。

### 4.2 配置宿主机 nginx 反向代理

在站点 vhost 的 HTTPS server 块内加入：

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
> `Cache-Control`，第 5 节的缓存策略就失效了。

### 4.3 起容器

```bash
cd <部署目录> && ./deploy.sh
```

---

## 5. 缓存策略（改 `wiki/nginx.conf` 前先读）

「用户不清缓存就白屏 / 看到旧版本」是本项目复发过多次的 bug，现在的规则是治本方案，
**不要为了「简化配置」把它删掉**：

| 路径 | 头 | 原因 |
|---|---|---|
| `/index.html`（含所有 SPA 路由回退） | `no-cache, must-revalidate` | 不加的话浏览器按 `Last-Modified` 启发式缓存，部署后仍用旧 index → 指向已被 prune 的旧 chunk → 白屏 |
| `/assets/*` | `immutable, max-age=31536000` | 文件名带 hash，内容变即换名，可以永久缓存 |
| `/assets/*` 未命中 | `@stale_asset` 返回 200 自愈脚本 | 已经缓存了旧 index 的老客户端跑的是旧 JS，新代码触达不到它们；服务器对缺失 chunk 不返回 404，而是返回一段脚本强制重取 index 并整页刷新（`sessionStorage` 防循环，`main.js` 挂载成功后清除） |

`wiki/index.html` 里还有一个捕获阶段的 `error` 监听，作用相同（入口脚本 404 时自愈）。

---

## 6. 验证

```bash
D=surivivexmum.wiki
curl -sI  https://$D/                       # 首页 200
curl -sI  https://$D/ | grep -i cache-control   # 应为 no-cache, must-revalidate
curl -s   https://$D/api/wiki/manifest      # 应为 JSON（说明后端在，且 nginx 反代正常）
```

容器侧：

```bash
cd <部署目录>
docker compose ps                     # wiki-frontend 应是 Up 且 CREATED 是刚刚
docker compose logs frontend --tail 20
```

**最终人工确认**：浏览器打开站点 → 文档能读、图片能显示 → 登录后上传一张图片，
链接应为 `https://surivivexmum.wiki/wiki/...`。（此环节需登录态，curl 测不了。）

---

## 7. 常用运维命令

```bash
cd <部署目录>

docker compose ps                  # 容器状态
docker compose logs -f frontend    # 前端容器日志
docker compose restart frontend    # 只重启（不重新构建）
docker compose down                # 停止前端容器（不影响后端与数据服务）
docker compose up -d --build       # 重建并启动
```

---

## 8. 本地开发要点

```bash
cd wiki
npm ci
npm run dev      # /api 代理到 localhost:8080，需要本地或远端有后端
npm test         # node --test 跑 wiki/test/*.test.js 里的纯函数单测
npm run build    # 部署前的 sanity check（真正的构建在 Docker 里）
```

- 前端代码始终用同源 `/api`，不要把生产域名写死在组件里。
- 只改样式/交互时不必起后端，可直接对着线上环境验证。
- 后端在 `SurviveXMUM-server` 仓库，本地联调时先把它跑起来。

---

## 9. 回滚

```bash
cd <部署目录>
git log --oneline -5
git reset --hard <上一个 commit>
./deploy.sh
```

前端回滚只换静态产物，不涉及数据，安全且可反复执行。

---

## 10. 常见问题

| 现象 | 排查 |
|---|---|
| `/api/*` 返回 HTML 而非 JSON | nginx 缺 `/api/` 反代（落到了 SPA fallback），见 4.2；或后端没起来，去 `SurviveXMUM-server` 查 |
| 前端路由刷新 404 | 静态服务器需要 SPA fallback 到 `index.html` |
| 构建成功但还是旧版本 | 容器没重建：`docker compose up -d --force-recreate`（`deploy.sh` 已带该参数） |
| **白屏 / 看到旧版本，无痕正常** | 有**两个**互不相干的原因，别只查一个：① 缓存（见第 5 节，确认 `curl -sI https://域名/` 返回 `no-cache`）；② **广告拦截插件**——无痕模式默认禁用扩展，所以「无痕正常」不能证明是缓存问题。曾因 CSS 类名用了 `ad-` 前缀（`ad-head`/`ad-list`…）被 EasyList 通用规则整块隐藏。**前端类名一律不要用 `ad-` 开头。** |
| 部署后页面空白，刷新就好 | 旧标签页在用被替换掉的旧 chunk；容器 nginx 有自愈脚本，本地调试直接硬刷新 |
| 图片 403 / 链接是 http+IP | 属于后端与 MinIO 配置，见 `SurviveXMUM-server` 仓库 |

---

## 11. 关键信息速查

- 域名：`surivivexmum.wiki`（注意拼写 sur**i**vivexmum）
- 本仓库：`503133214/SurviveXMUM`（公开），部署分支 `dev`
- 后端仓库：`503133214/SurviveXMUM-server`（私有），部署分支 `main`
- 容器名：`wiki-frontend`（`127.0.0.1:8081`）；`wiki-backend` 与 `minio` 不归本仓库管
- 服务器地址、SSH 登录方式、主机上的绝对路径与 nginx vhost 文件名：见私有仓库
