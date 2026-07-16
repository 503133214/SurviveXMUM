# API 概览

SurviveXMUM 是一个数据库驱动的在线 Wiki。前端负责文档浏览、Markdown 编辑与管理界面，后端负责认证、内容持久化、投稿审核、公开版本、收藏关注、通知和图片上传。

本文面向准备调用站点 API、调试前后端联动或参与代码开发的贡献者。接口尚未提供 `/v1` 版本前缀，也不承诺作为第三方公共平台长期兼容；正式集成前请关注仓库 `dev` 分支的变更。

## 快速导航

- [完整接口参考](./endpoints.md)
- [本地开发、测试与提交代码](./development.md)
- [内容投稿贡献指南](/docs/贡献指南)
- [GitHub 仓库](https://github.com/503133214/SurviveXMUM)

## 服务地址

| 环境 | API Base URL | 说明 |
|---|---|---|
| 生产环境 | `https://surivivexmum.wiki/api` | 宿主机 nginx 会移除 `/api` 后再转发给后端 |
| 本地后端直连 | `http://localhost:8080` | Spring Boot 控制器本身没有 `/api` 前缀 |
| 本地前端 | `/api` | Vite 将请求代理到 `localhost:8080` 并移除 `/api` |

例如，生产环境的文档清单地址是：

```text
GET https://surivivexmum.wiki/api/wiki/manifest
```

## 统一响应格式

除文件本身外，接口统一返回 JSON envelope：

```json
{
  "code": 0,
  "message": "success",
  "traceId": null,
  "data": {}
}
```

- `code = 0` 表示成功。
- `data` 是业务数据，也可能为 `null`。
- `traceId` 当前通常为 `null`，请不要依赖它一定存在。
- 常见业务错误码为 `400`、`401`、`403`、`404`、`409`、`500`。

> 认证失败和权限不足目前也会返回 **HTTP 200**，错误状态放在响应体 `code` 中。调用方不能只判断 HTTP 状态码。

## 认证与角色

登录或注册成功后，`data.token` 是 JWT。访问受保护接口时加入请求头：

```http
Authorization: Bearer <token>
```

Token 默认有效 7 天；退出登录后会进入 Redis 黑名单。服务端每次请求还会检查用户是否被封禁、删除或调整角色，因此旧 Token 不会绕过最新权限。

| 角色 | 能力 |
|---|---|
| `USER` | 投稿、草稿、收藏、关注、浏览历史、通知和反馈 |
| `ADMIN` | 普通用户能力 + 审核投稿、管理页面、处理反馈 |
| `SUPER_ADMIN` | 管理员能力 + 用户、分类、审计、公告和不可逆清理操作 |

注册默认仅允许学校邮箱域名。自动化测试或第三方调用不要使用真实密码、验证码或生产 Token 作为示例数据。

## 数据约定

### ID

Java `Long` 和雪花 ID 在 JSON 中会序列化为**字符串**，例如：

```json
{ "id": "2070850960572116993" }
```

JavaScript 客户端应一直按字符串保存，不能转换成 `Number`，否则可能丢失精度。

### 页面路径

页面 API 使用的路径不含 `/docs/`，例如：

```text
生活篇/医疗
```

浏览器页面对应 `/docs/生活篇/医疗`。路径含中文、空格或 `/` 时，应使用标准 URL/query 编码；命令行可使用 `curl --data-urlencode`。

### 时间与 Markdown

- 公开内容时间通常是带 `+08:00` 时区的 ISO 8601 字符串。
- 页面正文是 Markdown 字符串，由前端 MarkdownIt 渲染，并在插入 DOM 前使用 DOMPurify 清洗。
- `GET /wiki/page?track=true` 会增加浏览量；后台任务和调试工具通常应保持默认的 `track=false`。

## 三个最小示例

### 健康检查

```bash
curl https://surivivexmum.wiki/api/health
```

### 读取一篇公开文档

```bash
curl --get \
  --data-urlencode "path=生活篇/医疗" \
  https://surivivexmum.wiki/api/wiki/page
```

### 登录后读取自己的投稿

```bash
curl -X POST \
  -H "Content-Type: application/json" \
  -d '{"userEmail":"your-name@xmu.edu.my","password":"<password>"}' \
  https://surivivexmum.wiki/api/login

curl -H "Authorization: Bearer <token>" \
  https://surivivexmum.wiki/api/wiki/revision/mine
```

请把 Token 放进环境变量或秘密管理工具，不要写入仓库、日志、截图或 issue。

## 核心内容流程

```text
公开读取：manifest → page → Markdown 渲染

用户投稿：草稿（可选）→ 提交 revision → 管理员审核
          → 通过后更新 wiki_page → 写入公开版本快照
          → 通知作者与关注该页面的用户

管理员直改：更新 wiki_page → 写入公开版本快照 → 通知关注者
```

公开查询只返回 `PUBLISHED` 且未删除的页面。公开版本历史不会返回作者邮箱、审核意见或审核人等管理信息。

## 浏览器调用边界

后端按站点同源 `/api` 使用方式设计，Spring Security 当前没有启用跨域 CORS。`curl`、服务器端程序不受浏览器 CORS 限制；其他域名中的网页直接调用生产 API 通常会被浏览器阻止。

图片上传接口只有在 MinIO 存储启用时才注册，支持 JPG、PNG、GIF、WebP，单文件最大 10 MB。

## 权威实现位置

- 路由与鉴权：[`SecurityConfig.java`](https://github.com/503133214/SurviveXMUM/blob/dev/backend/src/main/java/wiki/xmum/security/SecurityConfig.java)
- 控制器：[`backend/.../controller`](https://github.com/503133214/SurviveXMUM/tree/dev/backend/src/main/java/wiki/xmum/controller)
- 请求 DTO：[`backend/.../dto`](https://github.com/503133214/SurviveXMUM/tree/dev/backend/src/main/java/wiki/xmum/domain/dto)
- 响应 VO：[`backend/.../vo`](https://github.com/503133214/SurviveXMUM/tree/dev/backend/src/main/java/wiki/xmum/domain/vo)
- 前端请求封装：[`wiki/src/net/index.js`](https://github.com/503133214/SurviveXMUM/blob/dev/wiki/src/net/index.js)

文档与实现冲突时，以 `dev` 分支代码和实际返回为准，并欢迎提交修正文档的 Pull Request。
