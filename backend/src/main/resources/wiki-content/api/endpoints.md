# 接口参考

本文列出当前后端控制器提供的全部接口。生产调用时在路径前添加：

```text
https://surivivexmum.wiki/api
```

本地直接调用 Spring Boot 时使用 `http://localhost:8080`，不添加 `/api`。协议、鉴权、ID 与错误处理约定见 [API 概览](./api-overview.md)。

## 权限图例

| 标记 | 含义 |
|---|---|
| 公开 | 不需要 Token |
| 用户 | 需要有效 JWT，任意在册用户 |
| 管理员 | 需要 `ADMIN` 或 `SUPER_ADMIN` |
| 超级管理员 | 仅 `SUPER_ADMIN`，通常包含不可逆治理操作 |

以下所有 JSON 接口都使用统一 envelope；表格中的“返回”指 `data` 字段。

## 公开接口

### 认证

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| `POST` | `/send/code` | query: `email`, `type=register/reset` | `null` |
| `POST` | `/register` | `RegisterDTO` | `AuthVO` |
| `POST` | `/login` | `LoginDTO` | `AuthVO` |
| `POST` | `/password/reset` | `ResetPasswordDTO` | `null` |

验证码按邮箱和用途有 60 秒发送冷却，通常 5 分钟有效。注册邮箱默认限制为 `@xmu.edu.my`。

```json
// LoginDTO
{
  "userEmail": "your-name@xmu.edu.my",
  "password": "<password>"
}
```

```json
// RegisterDTO
{
  "userEmail": "your-name@xmu.edu.my",
  "password": "<6-to-20-characters>",
  "code": "<email-code>"
}
```

```json
// AuthVO
{
  "token": "<jwt>",
  "userInfo": {
    "id": "<string-id>",
    "userEmail": "your-name@xmu.edu.my",
    "nickname": "<nickname>",
    "avatar": null,
    "role": "USER"
  }
}
```

### Wiki 内容

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| `GET` | `/wiki/manifest` | 无 | `ManifestVO`，导航树、扁平页面和首页元数据 |
| `GET` | `/wiki/page` | query: `path?=README`, `track?=false` | `PageDetailVO`，含 Markdown 正文 |
| `GET` | `/wiki/page/revisions` | query: `path` | `PublicPageRevisionVO[]`，列表不含正文 |
| `GET` | `/wiki/page/revisions/{id}` | path: 版本 ID | `PublicPageRevisionVO`，含前后正文与变更字段 |
| `GET` | `/wiki/comments` | query: `path` | `CommentVO[]`，该页讨论串 |

公开内容只包含 `PUBLISHED` 且未删除的页面。`track=true` 会增加浏览量，不适合缓存预热或批量同步。

页面详情主要字段：

```json
{
  "id": "1066",
  "path": "api/api-overview",
  "title": "API 概览",
  "categorySlug": "api",
  "description": "站点架构、鉴权和协议约定",
  "tags": ["API", "开发"],
  "headings": ["服务地址", "统一响应格式"],
  "content": "# API 概览\n...",
  "version": 1,
  "lastUpdated": "2026-07-17T12:00:00+08:00",
  "viewCount": 10
}
```

版本列表会公开发布时作者或操作者的显示名快照：优先使用昵称，否则保存并返回掩码邮箱；无法识别时使用系统迁移或已注销身份标签。接口不会公开原始邮箱、审核意见、审核人 ID。详情的 `changedFields` 可能包含 `title`、`categorySlug`、`icon`、`description`、`tags`、`content`。

### 社区与健康状态

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| `GET` | `/contributors` | query: `limit=50`，范围 1–100 | `ContributorVO[]` |
| `GET` | `/contributors/page` | query: 必填 `path`，页面路径 | `PageContributorVO[]`，本页全部贡献者 |
| `GET` | `/contributors/{id}` | path: 用户 ID | `ContributorProfileVO` |
| `GET` | `/wall` | 无 | `WallEntry[]` |
| `GET` | `/health` | 无 | `{status, service}` |

`GET /contributors/page?path=...` 只接受当前公开且未删除的页面。`path` 为空、页面不存在、未发布或已删除时返回业务 404；页面存在但没有可确认的人类贡献者时返回 HTTP 200 和空数组。路径中包含 `/`、空格或中文时应进行 URL 编码。

返回项使用 `PageContributorVO`；字段形状与贡献榜条目相同，但 `count` 的统计范围不同：

```json
[
  {
    "userId": "1066",
    "displayName": "XMUM Wiki 编辑者",
    "avatar": null,
    "count": 3
  },
  {
    "userId": "1077",
    "displayName": "ab***@xmu.edu.my",
    "avatar": "https://example.invalid/avatar.png",
    "count": 1
  }
]
```

- `count` 是该用户对当前页面去重后的已发布编写、修改事件数，不是其全站投稿数。同一投稿产生的发布快照按投稿 ID 去重；管理员直接编写或修改的发布动作逐次计数，回滚、恢复操作不计数。
- 接口不使用贡献榜的 `limit`，返回本页全部可确认贡献者，依次按 `count` 降序、最近贡献时间降序、`displayName` 排序。
- 当前用户的 `displayName` 优先使用非空昵称；没有昵称时只返回打码邮箱。用户后来被删除或记录已无法关联当前账户时，可返回发布时保存的公开显示名，此时 `userId` 和 `avatar` 为 `null`。响应绝不包含原始邮箱。
- 部署页面版本功能之前的旧记录会用已通过投稿补齐；可确认作者没有其他可计数事件时，其迁移基线最多补作一次编写事件。若页面只有无法确认人类作者的系统迁移记录，结果为空数组。

## 登录用户接口

以下请求需要：

```http
Authorization: Bearer <token>
```

### 当前用户与退出

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| `GET` | `/user/info` | 无 | `UserInfoVO` |
| `POST` | `/login/logout` | 无 | `null`，当前 Token 进入黑名单 |

### 投稿

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| `POST` | `/wiki/revision` | `RevisionSubmitDTO` | `{id}` |
| `GET` | `/wiki/revision/mine` | 无 | 当前用户的 `RevisionVO[]` |
| `GET` | `/wiki/revision/{id}` | path: 本人的投稿 ID | `RevisionDetailVO` |

```json
{
  "type": "UPDATE",
  "path": "生活篇/医疗",
  "categorySlug": "生活篇",
  "title": "医疗",
  "icon": "🏥",
  "description": "就医与常用药信息",
  "tags": ["生活", "医疗"],
  "content": "# 医疗\n\n正文...",
  "baseVersion": 3
}
```

- `type` 为 `CREATE` 或 `UPDATE`。
- 更新已有页面时传 `path` 和读取页面得到的 `baseVersion`。
- `baseVersion` 用于在投稿详情中标记内容是否已经落后；当前投稿接口不会因此自动拒绝提交，审核者应在通过前检查 `stale` 并人工合并。
- 投稿通过前不会出现在公开页面或公开版本历史中。

### 草稿

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| `POST` | `/wiki/drafts` | `DraftSaveDTO`，带 `id` 时更新 | `{id, savedAt}` |
| `GET` | `/wiki/drafts` | 无 | `DraftVO[]`，列表不含完整正文 |
| `GET` | `/wiki/drafts/by-path` | query: `path` | `DraftVO` 或 `null` |
| `GET` | `/wiki/drafts/{id}` | path: 本人的草稿 ID | `DraftVO` 详情 |
| `DELETE` | `/wiki/drafts/{id}` | path: 本人的草稿 ID | `null` |

`DraftSaveDTO` 与投稿字段相近，额外支持可选 `id`。每个用户最多 20 份草稿，正文上限约 300,000 字符，标签最多 10 个。

### 图片上传

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| `POST` | `/wiki/image` | multipart: `file` | `{objectName, url, markdown}` |

仅在 MinIO 存储启用时存在。支持 JPG、PNG、GIF、WebP，单文件最大 10 MB。

```bash
curl -X POST \
  -H "Authorization: Bearer <token>" \
  -F "file=@./image.png" \
  https://surivivexmum.wiki/api/wiki/image
```

### 收藏、关注与浏览历史

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| `GET` | `/user/favorites` | 无 | 收藏列表 |
| `POST` | `/user/favorites` | `{path, notifyUpdates?}` | `{favorited, id, notifyUpdates}` |
| `PUT` | `/user/favorites/{id}/notification` | `{notifyUpdates: boolean}` | 收藏状态 |
| `POST` | `/user/favorites/{id}/remove` | path: 当前用户的收藏 ID | `null` |
| `GET` | `/user/favorites/check` | query: `path` | `{favorited, id, notifyUpdates}` |
| `GET` | `/user/history` | 无 | 最近 50 条浏览记录 |
| `POST` | `/user/history` | `{path}` | `null` |
| `POST` | `/user/history/clear` | 无 | `null` |

`notifyUpdates=true` 表示关注这一页的后续动态：发布新版本（`PAGE_UPDATED`）以及有人新开讨论（`COMMENT_NEW`）都会写入站内通知。服务端会校验收藏所有权，不能用别人的收藏 ID 修改设置。

### 通知

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| `GET` | `/notifications` | 无 | 最近 30 条 `NotificationVO[]` |
| `GET` | `/notifications/unread-count` | 无 | `{count}` |
| `POST` | `/notifications/{id}/read` | path: 当前用户的通知 ID | `null` |
| `POST` | `/notifications/read-all` | 无 | `null` |

### 反馈

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| `POST` | `/feedback` | `FeedbackSubmitDTO` | `{id}` |
| `GET` | `/feedback/my` | 无 | 当前用户的 `FeedbackVO[]` |

```json
{
  "type": "bug",
  "title": "问题摘要",
  "content": "复现步骤与期望结果",
  "rating": 4,
  "contact": "可选联系方式"
}
```

`type` 常用值为 `bug`、`feature`、`ui`、`other`。

### 讨论区

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| `POST` | `/comments` | `{path, content, parentId?}` | `{id}` |
| `GET` | `/comments/mine` | 无 | `MyCommentVO[]`，本人最近 50 条（不含自删） |
| `DELETE` | `/comments/{id}` | path: 本人的评论 ID | `null` |

正文最多 1000 字。同一用户两条评论至少间隔 15 秒，5 分钟内不能在同一页重复发送完全相同的内容，否则返回业务码 `400`。

`parentId` 指向被回复的那条评论，可以是主楼也可以是楼中回复；服务端始终把新评论挂到该楼的主楼下，因此讨论只有两层。

通知规则：**回复**只提醒被回复的那个人（`COMMENT_REPLY`，回复自己不发）；**新开主楼**提醒所有对该页开了「关注更新」的人（`COMMENT_NEW`，发帖者自己除外）。回复不触达关注者，避免一层热闹把关注者刷屏。

自删是软删除（`status=DELETED`），主楼下若还有可见回复会保留一个不含作者信息的占位，避免回复变成孤儿。

## 管理员接口

管理员接口会修改公开内容或审核状态。自动化工具应先读取详情和版本号，不要对生产数据进行试调用。

### 投稿审核

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| `GET` | `/admin/revisions` | query: `status=PENDING`, `from?`, `to?`, `keyword?` | `RevisionVO[]` |
| `GET` | `/admin/revisions/counts` | 无 | 各审核状态数量 |
| `GET` | `/admin/revision/{id}` | path: 投稿 ID | `RevisionDetailVO` |
| `POST` | `/admin/revision/{id}/approve` | 无 | `null` |
| `POST` | `/admin/revision/{id}/reject` | `{comment?}` | `null` |

审核通过会在同一事务内更新或创建公开页面、追加完整版本快照，并通知投稿者和关注者。

### 页面管理

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| `GET` | `/admin/pages` | query: `keyword?`, `category?`, `includeDeleted=false`, `page=1`, `size=20` | `PageResult<PageAdminVO>` |
| `GET` | `/admin/page/{id}` | path: 页面 ID | `PageAdminVO`，含正文 |
| `POST` | `/admin/pages` | `PageUpsertDTO` | `{id}` |
| `PUT` | `/admin/page/{id}` | `PageUpsertDTO` | `null` |
| `DELETE` | `/admin/page/{id}` | 软删除 | `null` |
| `POST` | `/admin/page/{id}/restore` | 恢复回收站页面 | `null` |

```json
{
  "title": "页面标题",
  "categorySlug": "生活篇",
  "icon": "📄",
  "description": "页面简介",
  "tags": ["标签"],
  "content": "# 页面标题\n\n正文...",
  "sortOrder": 10,
  "status": "PUBLISHED",
  "version": 3
}
```

更新时必须提交当前 `version`。冲突会返回业务码 `409`，不能盲目重试覆盖。

### 反馈处理

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| `GET` | `/admin/feedback` | query: `status?`, `keyword?` | `FeedbackVO[]` |
| `POST` | `/admin/feedback/{id}/reply` | `{reply, status}` | `null` |

反馈状态使用小写：`pending`、`processing`、`resolved`、`rejected`。

### 评论管理

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| `GET` | `/admin/comments` | query: `status?`, `path?`, `keyword?`, `page=1`, `size=20` | `PageResult<AdminCommentVO>` |
| `POST` | `/admin/comments/{id}/status` | `{status, reason?}` | `null` |

`status` 取 `VISIBLE`（恢复显示）或 `HIDDEN`（隐藏），其余值返回业务码 `400`；作者已自删的评论不能再隐藏。隐藏理由最多 200 字，会随 `COMMENT_HIDDEN` 通知发给作者。两种操作都会写入审计日志（`COMMENT_HIDE` / `COMMENT_SHOW`）。

## 超级管理员接口

> 本节包含广播和物理删除。调用成功后可能无法恢复，必须在人工确认目标、权限和备份后执行。

### 投稿改判与清理

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| `POST` | `/admin/revision/{id}/reapprove` | 无 | `null` |
| `POST` | `/admin/revision/{id}/revoke` | `{comment?}` | `{pageAction}` |
| `PUT` | `/admin/revision/{id}/comment` | `{comment?}` | `null` |
| `DELETE` | `/admin/revision/{id}` | 仅非待审核且满足内容安全守卫 | `null` |

`pageAction` 可能是 `ROLLED_BACK`、`PAGE_DELETED` 或 `CONTENT_KEPT`。

### 用户管理

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| `GET` | `/admin/users` | query: `keyword?`, `role?`, `status?`, `includeDeleted=false`, `page=1`, `size=20` | `PageResult<UserAdminVO>` |
| `POST` | `/admin/users` | `UserUpsertDTO` | `{id}` |
| `PUT` | `/admin/users/{id}` | `UserUpsertDTO` | `null` |
| `DELETE` | `/admin/users/{id}` | 软删除 | `null` |
| `POST` | `/admin/users/{id}/restore` | 恢复用户 | `null` |
| `DELETE` | `/admin/users/{id}/purge` | 仅已软删用户；删除账号及收藏、历史、通知和草稿 | `null` |
| `GET` | `/admin/users/{id}/revisions` | path: 用户 ID | `RevisionVO[]` |

`UserUpsertDTO` 字段为 `email`、`password`、`nickname`、`role`、`status`。不要在日志或错误消息中输出密码。

用户 purge 不等同于完整个人数据擦除：投稿与审计中的邮箱快照等历史记录会继续保留，反馈中的联系方式也不由该接口处理。

### 分类管理

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| `GET` | `/admin/categories` | 无 | `CategoryAdminVO[]` |
| `POST` | `/admin/categories` | `CategoryUpsertDTO` | `{id}` |
| `PUT` | `/admin/categories/{id}` | `CategoryUpsertDTO` | `null` |
| `DELETE` | `/admin/categories/{id}` | 仅空分类 | `null` |

`CategoryUpsertDTO` 字段为 `slug`、`label`、`icon`、`description`、`sortOrder`。`slug` 创建后不可修改，因为它已经进入页面路径。

### 致谢墙

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| `GET` | `/admin/wall` | 无 | `WallEntry[]` |
| `POST` | `/admin/wall` | `WallEntryDTO` | `{id}` |
| `PUT` | `/admin/wall/{id}` | `WallEntryDTO` | `null` |
| `DELETE` | `/admin/wall/{id}` | 物理删除 | `null` |

`WallEntryDTO` 字段为 `name`、`avatar`、`description`、`link`、`category`、`sortOrder`。

### 公告、审计与不可逆页面清理

| 方法 | 路径 | 请求 | 返回 |
|---|---|---|---|
| `POST` | `/admin/broadcast` | `{title, content, link?}` | `{sent}` |
| `GET` | `/admin/audit` | query: `from?`, `to?`, `keyword?`, `action?`, `page=1`, `size=20` | `PageResult<AuditVO>` |
| `DELETE` | `/admin/feedback/{id}` | 物理删除反馈 | `null` |
| `DELETE` | `/admin/comments/{id}` | 物理删除评论，删主楼会连带楼中回复 | `null` |
| `DELETE` | `/admin/page/{id}/purge` | 页面须已在回收站 | `null` |
| `DELETE` | `/admin/page-version/{id}` | 不能删除当前版本 | `null` |

公告标题最多 200 字、正文最多 500 字、链接最多 400 字。广播会向所有在册活跃用户写入站内通知。

## 常见错误码

| `code` | 含义 | 典型处理 |
|---|---|---|
| `0` | 成功 | 使用 `data` |
| `400` | 参数或业务校验失败 | 展示 `message`，修正请求 |
| `401` | 未登录、Token 无效或已退出 | 清理本地 Token 并重新登录 |
| `403` | 角色权限不足 | 不要重试，检查账号角色 |
| `404` | 页面、投稿或记录不存在，或不允许当前用户读取 | 检查 ID、路径和所有权 |
| `409` | 版本冲突 | 重新读取最新数据并人工合并 |
| `500` | 未处理的服务端异常 | 记录请求上下文，稍后重试并联系维护者 |

请再次注意：这些业务错误目前多数仍使用 HTTP 200 返回，客户端必须检查 JSON `code`。

## 字段权威来源

- 请求对象：[`domain/dto`](https://github.com/503133214/SurviveXMUM/tree/dev/backend/src/main/java/wiki/xmum/domain/dto)
- 响应对象：[`domain/vo`](https://github.com/503133214/SurviveXMUM/tree/dev/backend/src/main/java/wiki/xmum/domain/vo)
- 路由实现：[`controller`](https://github.com/503133214/SurviveXMUM/tree/dev/backend/src/main/java/wiki/xmum/controller)
- 权限规则：[`SecurityConfig.java`](https://github.com/503133214/SurviveXMUM/blob/dev/backend/src/main/java/wiki/xmum/security/SecurityConfig.java)

接口变化时，请在同一个 Pull Request 中同步更新本页。
