# SurviveXMUM · 前端

厦门大学马来西亚分校自救指南 —— <https://surivivexmum.wiki>

## 简介

SurviveXMUM 是一个帮助厦门大学马来西亚分校（Xiamen University Malaysia, XMUM）学生
更快适应校园与周边生活的指南，由在校学生发起与维护。

本项目是一个**带后端的在线 Wiki**：内容全部保存在数据库中，用户在网站上直接编辑、
投稿，管理员审核通过后即时生效，**不需要重新构建或合并代码**。

本仓库是**前端**（Vue 3 + Vite + Element Plus）。后端在私有仓库
`503133214/SurviveXMUM-server`（Spring Boot 3 + MySQL + Redis + MinIO，JWT 鉴权）。
两边**各自独立部署、互不依赖**：前端只监听 `127.0.0.1:8081`，后端只监听 `:8080`，
由宿主机 nginx 分别反代 `/` 和 `/api/`。

## 如何贡献内容（推荐：直接在网站上）

内容不通过 GitHub 上的 Markdown 文件维护，而是**直接在网站上完成**：

1. **注册 / 登录**：使用 `@xmu.edu.my` 校园邮箱注册（需邮箱验证码），然后登录。
2. **编辑或新建**：在任意页面点「编辑」提出修改，或新建条目；用站内 Markdown 编辑器撰写，图片可直接上传。
3. **提交投稿**：提交后进入「待审核」队列。
4. **管理员审核**：通过后内容**立即上线**，或被驳回并附说明。
5. **查看进度**：在「个人中心」查看投稿状态，也能看到自己的草稿与讨论。

详见站内[贡献指南](https://surivivexmum.wiki/docs/贡献指南)。

> 角色：普通用户（投稿、讨论）、管理员（审核 + 页面 / 评论管理）、超级管理员（含用户管理等全部权限）。

## 本地开发

需要 Node.js 20+。

```bash
cd wiki
npm ci
npm run dev      # /api 代理到 localhost:8080
npm test         # 纯函数单测
npm run build
```

只改样式或交互时不必起后端。需要联调时，把 `SurviveXMUM-server` 跑起来即可。
更多说明见 [`wiki/README.md`](wiki/README.md) 与站内[本地开发](https://surivivexmum.wiki/docs/api/development)页。

## 目录

```
wiki/              前端应用（Vue 3 + Vite）
  src/net/         API 封装
  src/wiki/        manifest、页面、搜索、标签门面
  src/views/       路由页面
  src/components/  通用 UI 与 Markdown 渲染
docker-compose.yml 前端容器编排
deploy.sh          一键部署
DEPLOY.md          前端部署与运维文档
Art Resources/     Logo 等设计源文件
```

## 参与代码开发

1. Fork 本仓库，从 `dev` 分支创建特性分支。
2. 提交清晰的 commit，向 `dev` 分支发起 Pull Request。
3. 涉及页面交互时，请附桌面端与移动端（约 390px 宽）的截图。

后端改动请到 `SurviveXMUM-server` 仓库。

## 部署

构建、缓存策略与反向代理配置见 [`DEPLOY.md`](DEPLOY.md)。

线上发布由维护者在服务器上执行 `./deploy.sh`；**服务器地址、登录方式与运维细节
不在本公开仓库中**，见私有仓库 `SurviveXMUM-server`。

## 许可证

[GNU GENERAL PUBLIC LICENSE Version 3](LICENSE)
