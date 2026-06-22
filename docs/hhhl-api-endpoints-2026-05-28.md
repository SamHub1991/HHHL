# HHHL 线上接口清单

生成时间：2026-05-28
实例：`https://dc.hhhl.cc`
接口来源：`POST /api/endpoints`，并用 `GET /api.json` 交叉确认。

## 实例能力快照

| 项 | 值 |
| --- | --- |
| 名称 | hhhl |
| 版本 | 2025.5.2-dev |
| MiAuth | True |
| 最大帖子长度 | 3000 |
| 最大 CW 长度 | 500 |
| 默认喜欢 | ❤️ |
| 本地时间线 | True |
| 全局时间线 | True |
| Bubble 时间线 | False |
| 公开发帖 | True |
| 匿名搜索帖子权限 | False |
| 聊天能力 | available |
| 趋势 | True |
| 联邦视图 | True |
| 最大文件大小 | 262144000 |

## 头像上传系统改进（2026-06-22）

### 新增功能

1. **头像文件夹管理**
   - 头像统一上传到 `avatars` 文件夹
   - 自动创建文件夹（如果不存在）
   - 文件夹 ID 缓存避免重复创建

2. **图片压缩和裁剪**
   - 自动裁剪为正方形（512x512）
   - 自动压缩（质量 80%）
   - 跨平台支持（Android/iOS）
   - 处理失败时降级为原图上传

3. **上传限制**
   - 冷却时间：5 秒
   - 每日限制：5 次
   - 文件大小限制：5MB
   - 支持格式：JPG、PNG、GIF、WebP

4. **旧头像清理**
   - 自动删除旧头像文件
   - 释放存储空间

### 新增接口

| 接口 | 用途 | 状态 |
| --- | --- | --- |
| `drive/folders/create` | 创建头像文件夹 | 已接入 |
| `drive/files/create` | 上传头像（带 folderId） | 已接入 |
| `drive/files/delete` | 删除旧头像 | 已接入 |

### 新增类

| 类 | 平台 | 用途 |
| --- | --- | --- |
| `ImageProcessor` | 跨平台 | 图片处理接口 |
| `AndroidImageProcessor` | Android | Android 图片压缩实现 |
| `IOSImageProcessor` | iOS | iOS 图片压缩实现（占位） |

### 修改类

| 类 | 修改内容 |
| --- | --- |
| `UserProfileStateHolder` | 集成图片压缩、文件夹管理、上传限制 |
| `UserProfileUiState` | 添加冷却时间、剩余次数字段 |
| `ProfileScreen` | 显示上传限制信息、禁用状态控制 |

## 对比摘要

| 项 | 数量 |
| --- | ---: |
| 线上 `/api/endpoints` 接口数 | 452 |
| OpenAPI 路径数 | 452 |
| 本地静态提取接口调用数 | 124 |
| 本地精确匹配线上接口数 | 114 |
| 本地动态前缀匹配数 | 8 |
| 本地疑似旧/未列入清单接口数 | 2 |

说明：`本地动态/同模块调用` 表示本地代码提取到了类似 `channels`、`chat/messages` 这样的动态拼接前缀，线上清单里是 `channels/show`、`chat/messages/search` 这类完整接口。

### 本地疑似旧/未列入清单接口

- `miauth/check`
- `signin-flow`

### 本地动态前缀匹配

- `admin/announcements`
- `antennas`
- `channels`
- `chat/messages`
- `clips`
- `flash`
- `pages`
- `users/lists`

## 全量接口

### admin (117)

| 接口 | 本地状态 |
| --- | --- |
| `admin/abuse-report/notification-recipient/create` | 本地未接入 |
| `admin/abuse-report/notification-recipient/delete` | 本地未接入 |
| `admin/abuse-report/notification-recipient/list` | 本地未接入 |
| `admin/abuse-report/notification-recipient/show` | 本地未接入 |
| `admin/abuse-report/notification-recipient/update` | 本地未接入 |
| `admin/abuse-user-reports` | 本地已精确接入 |
| `admin/accounts/create` | 本地未接入 |
| `admin/accounts/delete` | 本地未接入 |
| `admin/accounts/find-by-email` | 本地未接入 |
| `admin/ad/create` | 本地未接入 |
| `admin/ad/delete` | 本地未接入 |
| `admin/ad/list` | 本地未接入 |
| `admin/ad/update` | 本地未接入 |
| `admin/announcements/create` | 本地已精确接入 |
| `admin/announcements/delete` | 本地已精确接入 |
| `admin/announcements/list` | 本地已精确接入 |
| `admin/announcements/update` | 本地已精确接入 |
| `admin/approve-user` | 本地未接入 |
| `admin/avatar-decorations/create` | 本地未接入 |
| `admin/avatar-decorations/delete` | 本地未接入 |
| `admin/avatar-decorations/list` | 本地未接入 |
| `admin/avatar-decorations/update` | 本地未接入 |
| `admin/captcha/current` | 本地未接入 |
| `admin/captcha/save` | 本地未接入 |
| `admin/chat/rooms/list` | 本地未接入 |
| `admin/chat/rooms/messages` | 本地未接入 |
| `admin/chat/rooms/show` | 本地未接入 |
| `admin/chat/rooms/update` | 本地未接入 |
| `admin/cw-instance` | 本地未接入 |
| `admin/cw-note` | 本地未接入 |
| `admin/cw-user` | 本地未接入 |
| `admin/decline-user` | 本地未接入 |
| `admin/delete-account` | 本地未接入 |
| `admin/delete-all-files-of-a-user` | 本地未接入 |
| `admin/drive/clean-remote-files` | 本地未接入 |
| `admin/drive/cleanup` | 本地未接入 |
| `admin/drive/files` | 本地未接入 |
| `admin/drive/show-file` | 本地未接入 |
| `admin/emoji/add` | 本地未接入 |
| `admin/emoji/add-aliases-bulk` | 本地未接入 |
| `admin/emoji/copy` | 本地未接入 |
| `admin/emoji/delete` | 本地未接入 |
| `admin/emoji/delete-bulk` | 本地未接入 |
| `admin/emoji/import-zip` | 本地未接入 |
| `admin/emoji/list` | 本地未接入 |
| `admin/emoji/list-remote` | 本地未接入 |
| `admin/emoji/remove-aliases-bulk` | 本地未接入 |
| `admin/emoji/set-aliases-bulk` | 本地未接入 |
| `admin/emoji/set-category-bulk` | 本地未接入 |
| `admin/emoji/set-license-bulk` | 本地未接入 |
| `admin/emoji/update` | 本地未接入 |
| `admin/federation/delete-all-files` | 本地未接入 |
| `admin/federation/refresh-remote-instance-metadata` | 本地未接入 |
| `admin/federation/remove-all-following` | 本地未接入 |
| `admin/federation/update-instance` | 本地已精确接入 |
| `admin/forward-abuse-user-report` | 本地未接入 |
| `admin/gen-vapid-keys` | 本地未接入 |
| `admin/get-index-stats` | 本地未接入 |
| `admin/get-table-stats` | 本地未接入 |
| `admin/get-user-ips` | 本地未接入 |
| `admin/invite/create` | 本地未接入 |
| `admin/invite/list` | 本地未接入 |
| `admin/meta` | 本地已精确接入 |
| `admin/nsfw-user` | 本地未接入 |
| `admin/promo/create` | 本地未接入 |
| `admin/queue/clear` | 本地未接入 |
| `admin/queue/deliver-delayed` | 本地未接入 |
| `admin/queue/inbox-delayed` | 本地未接入 |
| `admin/queue/jobs` | 本地未接入 |
| `admin/queue/promote-jobs` | 本地未接入 |
| `admin/queue/queue-stats` | 本地未接入 |
| `admin/queue/queues` | 本地未接入 |
| `admin/queue/remove-job` | 本地未接入 |
| `admin/queue/retry-job` | 本地未接入 |
| `admin/queue/show-job` | 本地未接入 |
| `admin/queue/stats` | 本地未接入 |
| `admin/reject-quotes` | 本地未接入 |
| `admin/relays/add` | 本地未接入 |
| `admin/relays/list` | 本地未接入 |
| `admin/relays/remove` | 本地未接入 |
| `admin/reset-password` | 本地未接入 |
| `admin/resolve-abuse-user-report` | 本地已精确接入 |
| `admin/restart-migration` | 本地未接入 |
| `admin/roles/annotate-condition` | 本地未接入 |
| `admin/roles/assign` | 本地未接入 |
| `admin/roles/clone` | 本地未接入 |
| `admin/roles/create` | 本地未接入 |
| `admin/roles/delete` | 本地未接入 |
| `admin/roles/list` | 本地已精确接入 |
| `admin/roles/show` | 本地未接入 |
| `admin/roles/unassign` | 本地未接入 |
| `admin/roles/update` | 本地未接入 |
| `admin/roles/update-default-policies` | 本地未接入 |
| `admin/roles/users` | 本地已精确接入 |
| `admin/send-email` | 本地未接入 |
| `admin/server-info` | 本地未接入 |
| `admin/set-root` | 本地未接入 |
| `admin/show-moderation-logs` | 本地未接入 |
| `admin/show-user` | 本地未接入 |
| `admin/show-users` | 本地已精确接入 |
| `admin/silence-user` | 本地未接入 |
| `admin/suspend-user` | 本地未接入 |
| `admin/system-webhook/create` | 本地未接入 |
| `admin/system-webhook/delete` | 本地未接入 |
| `admin/system-webhook/list` | 本地未接入 |
| `admin/system-webhook/show` | 本地未接入 |
| `admin/system-webhook/test` | 本地未接入 |
| `admin/system-webhook/update` | 本地未接入 |
| `admin/unnsfw-user` | 本地未接入 |
| `admin/unset-user-avatar` | 本地未接入 |
| `admin/unset-user-banner` | 本地未接入 |
| `admin/unsilence-user` | 本地未接入 |
| `admin/unsuspend-user` | 本地未接入 |
| `admin/update-abuse-user-report` | 本地未接入 |
| `admin/update-meta` | 本地未接入 |
| `admin/update-proxy-account` | 本地未接入 |
| `admin/update-user-note` | 本地未接入 |

### announcements (2)

| 接口 | 本地状态 |
| --- | --- |
| `announcements` | 本地已精确接入 |
| `announcements/show` | 本地已精确接入 |

### antennas (6)

| 接口 | 本地状态 |
| --- | --- |
| `antennas/create` | 本地动态/同模块调用：antennas |
| `antennas/delete` | 本地已精确接入 |
| `antennas/list` | 本地已精确接入 |
| `antennas/notes` | 本地已精确接入 |
| `antennas/show` | 本地动态/同模块调用：antennas |
| `antennas/update` | 本地动态/同模块调用：antennas |

### ap (2)

| 接口 | 本地状态 |
| --- | --- |
| `ap/get` | 本地已精确接入 |
| `ap/show` | 本地已精确接入 |

### app (3)

| 接口 | 本地状态 |
| --- | --- |
| `app/create` | 本地已精确接入 |
| `app/current` | 本地已精确接入 |
| `app/show` | 本地已精确接入 |

### auth (4)

| 接口 | 本地状态 |
| --- | --- |
| `auth/accept` | 本地已精确接入 |
| `auth/session/generate` | 本地已精确接入 |
| `auth/session/show` | 本地已精确接入 |
| `auth/session/userkey` | 本地已精确接入 |

### blocking (3)

| 接口 | 本地状态 |
| --- | --- |
| `blocking/create` | 本地已精确接入 |
| `blocking/delete` | 本地已精确接入 |
| `blocking/list` | 本地已精确接入 |

### bubble-game (2)

| 接口 | 本地状态 |
| --- | --- |
| `bubble-game/ranking` | 本地未接入 |
| `bubble-game/register` | 本地未接入 |

### channels (13)

| 接口 | 本地状态 |
| --- | --- |
| `channels/create` | 本地动态/同模块调用：channels |
| `channels/favorite` | 本地动态/同模块调用：channels |
| `channels/featured` | 本地动态/同模块调用：channels |
| `channels/follow` | 本地动态/同模块调用：channels |
| `channels/followed` | 本地动态/同模块调用：channels |
| `channels/my-favorites` | 本地动态/同模块调用：channels |
| `channels/owned` | 本地动态/同模块调用：channels |
| `channels/search` | 本地动态/同模块调用：channels |
| `channels/show` | 本地动态/同模块调用：channels |
| `channels/timeline` | 本地已精确接入 |
| `channels/unfavorite` | 本地动态/同模块调用：channels |
| `channels/unfollow` | 本地动态/同模块调用：channels |
| `channels/update` | 本地动态/同模块调用：channels |

### charts (12)

| 接口 | 本地状态 |
| --- | --- |
| `charts/active-users` | 本地已精确接入 |
| `charts/ap-request` | 本地已精确接入 |
| `charts/drive` | 本地已精确接入 |
| `charts/federation` | 本地已精确接入 |
| `charts/instance` | 本地已精确接入 |
| `charts/notes` | 本地已精确接入 |
| `charts/user/drive` | 本地已精确接入 |
| `charts/user/following` | 本地已精确接入 |
| `charts/user/notes` | 本地已精确接入 |
| `charts/user/pv` | 本地已精确接入 |
| `charts/user/reactions` | 本地已精确接入 |
| `charts/users` | 本地已精确接入 |

### chat (24)

| 接口 | 本地状态 |
| --- | --- |
| `chat/history` | 本地已精确接入 |
| `chat/messages/create-to-room` | 本地已精确接入 |
| `chat/messages/create-to-user` | 本地已精确接入 |
| `chat/messages/delete` | 本地已精确接入 |
| `chat/messages/react` | 本地动态/同模块调用：chat/messages |
| `chat/messages/room-timeline` | 本地已精确接入 |
| `chat/messages/search` | 本地已精确接入 |
| `chat/messages/show` | 本地动态/同模块调用：chat/messages |
| `chat/messages/unreact` | 本地动态/同模块调用：chat/messages |
| `chat/messages/user-timeline` | 本地已精确接入 |
| `chat/rooms/create` | 本地已精确接入 |
| `chat/rooms/delete` | 本地已精确接入 |
| `chat/rooms/invitations/create` | 本地已精确接入 |
| `chat/rooms/invitations/ignore` | 本地已精确接入 |
| `chat/rooms/invitations/inbox` | 本地已精确接入 |
| `chat/rooms/invitations/outbox` | 本地已精确接入 |
| `chat/rooms/join` | 本地已精确接入 |
| `chat/rooms/joining` | 本地已精确接入 |
| `chat/rooms/leave` | 本地已精确接入 |
| `chat/rooms/members` | 本地已精确接入 |
| `chat/rooms/mute` | 本地已精确接入 |
| `chat/rooms/owned` | 本地已精确接入 |
| `chat/rooms/show` | 本地已精确接入 |
| `chat/rooms/update` | 本地已精确接入 |

### clips (11)

| 接口 | 本地状态 |
| --- | --- |
| `clips/add-note` | 本地动态/同模块调用：clips |
| `clips/create` | 本地已精确接入 |
| `clips/delete` | 本地动态/同模块调用：clips |
| `clips/favorite` | 本地动态/同模块调用：clips |
| `clips/list` | 本地动态/同模块调用：clips |
| `clips/my-favorites` | 本地动态/同模块调用：clips |
| `clips/notes` | 本地已精确接入 |
| `clips/remove-note` | 本地动态/同模块调用：clips |
| `clips/show` | 本地动态/同模块调用：clips |
| `clips/unfavorite` | 本地动态/同模块调用：clips |
| `clips/update` | 本地已精确接入 |

### drive (18)

| 接口 | 本地状态 |
| --- | --- |
| `drive` | 本地已精确接入 |
| `drive/files` | 本地已精确接入 |
| `drive/files/attached-notes` | 本地已精确接入 |
| `drive/files/check-existence` | 本地动态/同模块调用：drive/files |
| `drive/files/create` | 本地已精确接入 |
| `drive/files/delete` | 本地已精确接入 |
| `drive/files/find` | 本地动态/同模块调用：drive/files |
| `drive/files/find-by-hash` | 本地动态/同模块调用：drive/files |
| `drive/files/show` | 本地已精确接入 |
| `drive/files/update` | 本地已精确接入 |
| `drive/files/upload-from-url` | 本地动态/同模块调用：drive/files |
| `drive/folders` | 本地已精确接入 |
| `drive/folders/create` | 本地已精确接入 |
| `drive/folders/delete` | 本地已精确接入 |
| `drive/folders/find` | 本地动态/同模块调用：drive/folders |
| `drive/folders/show` | 本地动态/同模块调用：drive/folders |
| `drive/folders/update` | 本地已精确接入 |
| `drive/stream` | 本地已精确接入 |

### email-address (1)

| 接口 | 本地状态 |
| --- | --- |
| `email-address/available` | 本地已精确接入 |

### emoji (1)

| 接口 | 本地状态 |
| --- | --- |
| `emoji` | 本地已精确接入 |

### emojis (1)

| 接口 | 本地状态 |
| --- | --- |
| `emojis` | 本地已精确接入 |

### endpoint (1)

| 接口 | 本地状态 |
| --- | --- |
| `endpoint` | 本地已精确接入 |

### endpoints (1)

| 接口 | 本地状态 |
| --- | --- |
| `endpoints` | 本地已精确接入 |

### export-custom-emojis (1)

| 接口 | 本地状态 |
| --- | --- |
| `export-custom-emojis` | 本地未接入 |

### federation (7)

| 接口 | 本地状态 |
| --- | --- |
| `federation/followers` | 本地已精确接入 |
| `federation/following` | 本地已精确接入 |
| `federation/instances` | 本地已精确接入 |
| `federation/show-instance` | 本地已精确接入 |
| `federation/stats` | 本地已精确接入 |
| `federation/update-remote-user` | 本地已精确接入 |
| `federation/users` | 本地已精确接入 |

### fetch-external-resources (1)

| 接口 | 本地状态 |
| --- | --- |
| `fetch-external-resources` | 本地已精确接入 |

### fetch-rss (1)

| 接口 | 本地状态 |
| --- | --- |
| `fetch-rss` | 本地已精确接入 |

### flash (9)

| 接口 | 本地状态 |
| --- | --- |
| `flash/create` | 本地动态/同模块调用：flash |
| `flash/delete` | 本地动态/同模块调用：flash |
| `flash/featured` | 本地动态/同模块调用：flash |
| `flash/like` | 本地动态/同模块调用：flash |
| `flash/my` | 本地动态/同模块调用：flash |
| `flash/my-likes` | 本地动态/同模块调用：flash |
| `flash/show` | 本地已精确接入 |
| `flash/unlike` | 本地动态/同模块调用：flash |
| `flash/update` | 本地动态/同模块调用：flash |

### following (10)

| 接口 | 本地状态 |
| --- | --- |
| `following/create` | 本地已精确接入 |
| `following/delete` | 本地已精确接入 |
| `following/invalidate` | 本地已精确接入 |
| `following/requests/accept` | 本地已精确接入 |
| `following/requests/cancel` | 本地已精确接入 |
| `following/requests/list` | 本地已精确接入 |
| `following/requests/reject` | 本地已精确接入 |
| `following/requests/sent` | 本地已精确接入 |
| `following/update` | 本地已精确接入 |
| `following/update-all` | 本地已精确接入 |

### gallery (9)

| 接口 | 本地状态 |
| --- | --- |
| `gallery/featured` | 本地已精确接入 |
| `gallery/popular` | 本地已精确接入 |
| `gallery/posts` | 本地已精确接入 |
| `gallery/posts/create` | 本地已精确接入 |
| `gallery/posts/delete` | 本地动态/同模块调用：gallery/posts |
| `gallery/posts/like` | 本地动态/同模块调用：gallery/posts |
| `gallery/posts/show` | 本地已精确接入 |
| `gallery/posts/unlike` | 本地动态/同模块调用：gallery/posts |
| `gallery/posts/update` | 本地已精确接入 |

### get-avatar-decorations (1)

| 接口 | 本地状态 |
| --- | --- |
| `get-avatar-decorations` | 本地已精确接入 |

### get-online-users-count (1)

| 接口 | 本地状态 |
| --- | --- |
| `get-online-users-count` | 本地已精确接入 |

### hashtags (5)

| 接口 | 本地状态 |
| --- | --- |
| `hashtags/list` | 本地已精确接入 |
| `hashtags/search` | 本地已精确接入 |
| `hashtags/show` | 本地已精确接入 |
| `hashtags/trend` | 本地已精确接入 |
| `hashtags/users` | 本地已精确接入 |

### i (62)

| 接口 | 本地状态 |
| --- | --- |
| `i` | 本地已精确接入 |
| `i/2fa/done` | 本地动态/同模块调用：i |
| `i/2fa/key-done` | 本地动态/同模块调用：i |
| `i/2fa/password-less` | 本地动态/同模块调用：i |
| `i/2fa/register` | 本地动态/同模块调用：i |
| `i/2fa/register-key` | 本地动态/同模块调用：i |
| `i/2fa/remove-key` | 本地动态/同模块调用：i |
| `i/2fa/unregister` | 本地动态/同模块调用：i |
| `i/2fa/update-key` | 本地动态/同模块调用：i |
| `i/apps` | 本地已精确接入 |
| `i/authorized-apps` | 本地已精确接入 |
| `i/change-password` | 本地动态/同模块调用：i |
| `i/claim-achievement` | 本地已精确接入 |
| `i/delete-account` | 本地动态/同模块调用：i |
| `i/export-antennas` | 本地动态/同模块调用：i |
| `i/export-blocking` | 本地动态/同模块调用：i |
| `i/export-clips` | 本地动态/同模块调用：i |
| `i/export-data` | 本地动态/同模块调用：i |
| `i/export-favorites` | 本地动态/同模块调用：i |
| `i/export-following` | 本地动态/同模块调用：i |
| `i/export-mute` | 本地动态/同模块调用：i |
| `i/export-notes` | 本地动态/同模块调用：i |
| `i/export-user-lists` | 本地动态/同模块调用：i |
| `i/favorites` | 本地已精确接入 |
| `i/gallery/likes` | 本地动态/同模块调用：i |
| `i/gallery/posts` | 本地动态/同模块调用：i |
| `i/import-antennas` | 本地动态/同模块调用：i |
| `i/import-blocking` | 本地动态/同模块调用：i |
| `i/import-following` | 本地动态/同模块调用：i |
| `i/import-muting` | 本地动态/同模块调用：i |
| `i/import-notes` | 本地动态/同模块调用：i |
| `i/import-user-lists` | 本地动态/同模块调用：i |
| `i/move` | 本地动态/同模块调用：i |
| `i/notifications` | 本地已精确接入 |
| `i/notifications-grouped` | 本地动态/同模块调用：i |
| `i/page-likes` | 本地动态/同模块调用：i |
| `i/pages` | 本地动态/同模块调用：i |
| `i/pin` | 本地动态/同模块调用：i |
| `i/read-announcement` | 本地已精确接入 |
| `i/regenerate-token` | 本地动态/同模块调用：i |
| `i/registry/get` | 本地动态/同模块调用：i |
| `i/registry/get-all` | 本地动态/同模块调用：i |
| `i/registry/get-detail` | 本地动态/同模块调用：i |
| `i/registry/get-unsecure` | 本地动态/同模块调用：i |
| `i/registry/keys` | 本地动态/同模块调用：i |
| `i/registry/keys-with-type` | 本地动态/同模块调用：i |
| `i/registry/remove` | 本地动态/同模块调用：i |
| `i/registry/scopes-with-domain` | 本地动态/同模块调用：i |
| `i/registry/set` | 本地动态/同模块调用：i |
| `i/revoke-token` | 本地已精确接入 |
| `i/shared-access/list` | 本地已精确接入 |
| `i/shared-access/login` | 本地动态/同模块调用：i |
| `i/signin-history` | 本地已精确接入 |
| `i/unpin` | 本地动态/同模块调用：i |
| `i/update` | 本地已精确接入 |
| `i/update-email` | 本地动态/同模块调用：i |
| `i/webhooks/create` | 本地已精确接入 |
| `i/webhooks/delete` | 本地已精确接入 |
| `i/webhooks/list` | 本地已精确接入 |
| `i/webhooks/show` | 本地已精确接入 |
| `i/webhooks/test` | 本地已精确接入 |
| `i/webhooks/update` | 本地已精确接入 |

### invite (4)

| 接口 | 本地状态 |
| --- | --- |
| `invite/create` | 本地已精确接入 |
| `invite/delete` | 本地已精确接入 |
| `invite/limit` | 本地已精确接入 |
| `invite/list` | 本地已精确接入 |

### meta (1)

| 接口 | 本地状态 |
| --- | --- |
| `meta` | 本地已精确接入 |

### miauth (1)

| 接口 | 本地状态 |
| --- | --- |
| `miauth/gen-token` | 本地已精确接入 |

### mute (3)

| 接口 | 本地状态 |
| --- | --- |
| `mute/create` | 本地已精确接入 |
| `mute/delete` | 本地已精确接入 |
| `mute/list` | 本地已精确接入 |

### my (1)

| 接口 | 本地状态 |
| --- | --- |
| `my/apps` | 本地已精确接入 |

### notes (39)

| 接口 | 本地状态 |
| --- | --- |
| `notes` | 本地已精确接入 |
| `notes/bubble-timeline` | 本地已精确接入 |
| `notes/children` | 本地已精确接入 |
| `notes/clips` | 本地已精确接入 |
| `notes/conversation` | 本地已精确接入 |
| `notes/create` | 本地已精确接入 |
| `notes/delete` | 本地已精确接入 |
| `notes/edit` | 本地已精确接入 |
| `notes/favorites/create` | 本地已精确接入 |
| `notes/favorites/delete` | 本地已精确接入 |
| `notes/featured` | 本地已精确接入 |
| `notes/following` | 本地已精确接入 |
| `notes/global-timeline` | 本地已精确接入 |
| `notes/hybrid-timeline` | 本地已精确接入 |
| `notes/like` | 本地已精确接入 |
| `notes/local-timeline` | 本地已精确接入 |
| `notes/mentions` | 本地已精确接入 |
| `notes/polls/recommendation` | 本地已精确接入 |
| `notes/polls/refresh` | 本地已精确接入 |
| `notes/polls/vote` | 本地已精确接入 |
| `notes/reactions` | 本地已精确接入 |
| `notes/reactions/create` | 本地已精确接入 |
| `notes/reactions/delete` | 本地已精确接入 |
| `notes/renotes` | 本地已精确接入 |
| `notes/replies` | 本地已精确接入 |
| `notes/schedule/create` | 本地已精确接入 |
| `notes/schedule/delete` | 本地已精确接入 |
| `notes/schedule/list` | 本地已精确接入 |
| `notes/search` | 本地已精确接入 |
| `notes/search-by-tag` | 本地已精确接入 |
| `notes/show` | 本地已精确接入 |
| `notes/state` | 本地已精确接入 |
| `notes/thread-muting/create` | 本地已精确接入 |
| `notes/thread-muting/delete` | 本地已精确接入 |
| `notes/timeline` | 本地已精确接入 |
| `notes/translate` | 本地已精确接入 |
| `notes/unrenote` | 本地已精确接入 |
| `notes/user-list-timeline` | 本地已精确接入 |
| `notes/versions` | 本地已精确接入 |

### notifications (4)

| 接口 | 本地状态 |
| --- | --- |
| `notifications/create` | 本地已精确接入 |
| `notifications/flush` | 本地已精确接入 |
| `notifications/mark-all-as-read` | 本地已精确接入 |
| `notifications/test-notification` | 本地已精确接入 |

### page-push (1)

| 接口 | 本地状态 |
| --- | --- |
| `page-push` | 本地已精确接入 |

### pages (7)

| 接口 | 本地状态 |
| --- | --- |
| `pages/create` | 本地已精确接入 |
| `pages/delete` | 本地动态/同模块调用：pages |
| `pages/featured` | 本地动态/同模块调用：pages |
| `pages/like` | 本地动态/同模块调用：pages |
| `pages/show` | 本地已精确接入 |
| `pages/unlike` | 本地动态/同模块调用：pages |
| `pages/update` | 本地已精确接入 |

### ping (1)

| 接口 | 本地状态 |
| --- | --- |
| `ping` | 本地已精确接入 |

### pinned-users (1)

| 接口 | 本地状态 |
| --- | --- |
| `pinned-users` | 本地已精确接入 |

### promo (1)

| 接口 | 本地状态 |
| --- | --- |
| `promo/read` | 本地已精确接入 |

### renote-mute (3)

| 接口 | 本地状态 |
| --- | --- |
| `renote-mute/create` | 本地已精确接入 |
| `renote-mute/delete` | 本地已精确接入 |
| `renote-mute/list` | 本地已精确接入 |

### request-reset-password (1)

| 接口 | 本地状态 |
| --- | --- |
| `request-reset-password` | 本地已精确接入 |

### reset-db (1)

| 接口 | 本地状态 |
| --- | --- |
| `reset-db` | 本地未接入 |

### reset-password (1)

| 接口 | 本地状态 |
| --- | --- |
| `reset-password` | 本地已精确接入 |

### retention (1)

| 接口 | 本地状态 |
| --- | --- |
| `retention` | 本地已精确接入 |

### reversi (7)

| 接口 | 本地状态 |
| --- | --- |
| `reversi/cancel-match` | 本地未接入 |
| `reversi/games` | 本地未接入 |
| `reversi/invitations` | 本地未接入 |
| `reversi/match` | 本地未接入 |
| `reversi/show-game` | 本地未接入 |
| `reversi/surrender` | 本地未接入 |
| `reversi/verify` | 本地未接入 |

### roles (4)

| 接口 | 本地状态 |
| --- | --- |
| `roles/list` | 本地已精确接入 |
| `roles/notes` | 本地已精确接入 |
| `roles/show` | 本地已精确接入 |
| `roles/users` | 本地已精确接入 |

### server-info (1)

| 接口 | 本地状态 |
| --- | --- |
| `server-info` | 本地已精确接入 |

### sponsors (1)

| 接口 | 本地状态 |
| --- | --- |
| `sponsors` | 本地已精确接入 |

### stats (1)

| 接口 | 本地状态 |
| --- | --- |
| `stats` | 本地已精确接入 |

### sw (4)

| 接口 | 本地状态 |
| --- | --- |
| `sw/register` | 本地已精确接入 |
| `sw/show-registration` | 本地已精确接入 |
| `sw/unregister` | 本地已精确接入 |
| `sw/update-registration` | 本地已精确接入 |

### test (1)

| 接口 | 本地状态 |
| --- | --- |
| `test` | 本地未接入 |

### username (1)

| 接口 | 本地状态 |
| --- | --- |
| `username/available` | 本地已精确接入 |

### users (31)

| 接口 | 本地状态 |
| --- | --- |
| `users` | 本地已精确接入 |
| `users/achievements` | 本地已精确接入 |
| `users/clips` | 本地动态/同模块调用：users |
| `users/featured-notes` | 本地动态/同模块调用：users |
| `users/flashs` | 本地动态/同模块调用：users |
| `users/followers` | 本地动态/同模块调用：users |
| `users/following` | 本地动态/同模块调用：users |
| `users/gallery/posts` | 本地动态/同模块调用：users |
| `users/get-frequently-replied-users` | 本地动态/同模块调用：users |
| `users/lists/create` | 本地动态/同模块调用：users |
| `users/lists/create-from-public` | 本地动态/同模块调用：users |
| `users/lists/delete` | 本地动态/同模块调用：users |
| `users/lists/favorite` | 本地动态/同模块调用：users |
| `users/lists/get-memberships` | 本地动态/同模块调用：users |
| `users/lists/list` | 本地已精确接入 |
| `users/lists/pull` | 本地动态/同模块调用：users |
| `users/lists/push` | 本地动态/同模块调用：users |
| `users/lists/show` | 本地动态/同模块调用：users |
| `users/lists/unfavorite` | 本地动态/同模块调用：users |
| `users/lists/update` | 本地动态/同模块调用：users |
| `users/lists/update-membership` | 本地动态/同模块调用：users |
| `users/notes` | 本地已精确接入 |
| `users/pages` | 本地动态/同模块调用：users |
| `users/reactions` | 本地动态/同模块调用：users |
| `users/recommendation` | 本地动态/同模块调用：users |
| `users/relation` | 本地已精确接入 |
| `users/report-abuse` | 本地已精确接入 |
| `users/search` | 本地已精确接入 |
| `users/search-by-username-and-host` | 本地动态/同模块调用：users |
| `users/show` | 本地已精确接入 |
| `users/update-memo` | 本地动态/同模块调用：users |

### v2 (1)

| 接口 | 本地状态 |
| --- | --- |
| `v2/admin/emoji/list` | 本地未接入 |

## 本地静态提取接口

- `admin/abuse-user-reports`
- `admin/announcements`
- `admin/announcements/create`
- `admin/announcements/delete`
- `admin/announcements/list`
- `admin/announcements/update`
- `admin/federation/update-instance`
- `admin/meta`
- `admin/resolve-abuse-user-report`
- `admin/roles/list`
- `admin/roles/users`
- `admin/show-users`
- `announcements`
- `announcements/show`
- `antennas`
- `antennas/delete`
- `antennas/list`
- `antennas/notes`
- `blocking/create`
- `blocking/delete`
- `blocking/list`
- `channels`
- `channels/timeline`
- `chat/history`
- `chat/messages`
- `chat/messages/create-to-room`
- `chat/messages/create-to-user`
- `chat/messages/delete`
- `chat/messages/room-timeline`
- `chat/messages/search`
- `chat/messages/user-timeline`
- `chat/rooms/create`
- `chat/rooms/delete`
- `chat/rooms/invitations/create`
- `chat/rooms/join`
- `chat/rooms/joining`
- `chat/rooms/leave`
- `chat/rooms/members`
- `chat/rooms/mute`
- `chat/rooms/show`
- `chat/rooms/update`
- `clips`
- `clips/create`
- `clips/notes`
- `clips/update`
- `drive/files`
- `drive/files/attached-notes`
- `drive/files/create`
- `drive/files/delete`
- `drive/files/show`
- `drive/files/update`
- `drive/folders`
- `drive/folders/create`
- `drive/folders/delete`
- `drive/folders/update`
- `emojis`
- `federation/instances`
- `federation/show-instance`
- `flash`
- `flash/show`
- `following/create`
- `following/delete`
- `following/requests/list`
- `gallery/posts`
- `gallery/posts/create`
- `gallery/posts/show`
- `gallery/posts/update`
- `hashtags/trend`
- `i`
- `i/apps`
- `i/authorized-apps`
- `i/claim-achievement`
- `i/favorites`
- `i/notifications`
- `i/read-announcement`
- `i/revoke-token`
- `i/shared-access/list`
- `i/signin-history`
- `i/update`
- `i/webhooks/create`
- `i/webhooks/delete`
- `i/webhooks/list`
- `i/webhooks/show`
- `i/webhooks/test`
- `i/webhooks/update`
- `meta`
- `miauth/check`
- `mute/create`
- `mute/delete`
- `mute/list`
- `notes/children`
- `notes/clips`
- `notes/create`
- `notes/delete`
- `notes/edit`
- `notes/favorites/create`
- `notes/favorites/delete`
- `notes/polls/vote`
- `notes/reactions/create`
- `notes/reactions/delete`
- `notes/replies`
- `notes/schedule/create`
- `notes/schedule/delete`
- `notes/schedule/list`
- `notes/search`
- `notes/search-by-tag`
- `notes/show`
- `notes/thread-muting/create`
- `notes/user-list-timeline`
- `notifications/mark-all-as-read`
- `pages`
- `pages/create`
- `pages/show`
- `pages/update`
- `signin-flow`
- `users`
- `users/achievements`
- `users/lists`
- `users/lists/list`
- `users/notes`
- `users/relation`
- `users/report-abuse`
- `users/search`
- `users/show`
