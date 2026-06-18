# HHHL 新增接口差异报告

生成时间：2026-06-18
实例：`https://dc.hhhl.cc`
旧快照：`docs/hhhl-api-endpoints-2026-05-28.md`
当前来源：`POST /api/endpoints` + `GET /api.json`

## 摘要

| 项 | 数量 |
| --- | ---: |
| 旧快照接口数 | 452 |
| 当前接口数 | 548 |
| 新增接口数 | 96 |
| 删除接口数 | 0 |

说明：OpenAPI 对这些接口大多只给出 `No description provided.`，下面的“说明”按接口路径、权限字段和请求参数归纳。参数名后带 `*` 表示 OpenAPI 标记为必填。

## admin

| 接口 | 方法 | 权限/登录 | 主要参数 | 说明 |
| --- | --- | --- | --- | --- |
| `admin/ai/providers/create` | POST | write:admin:meta | name*, baseUrl*, apiKey*, isEnabled, models, defaultModel, allowedModels, timeoutMs | 管理员新增 AI 服务商配置。 |
| `admin/ai/providers/delete` | POST | write:admin:meta | id* | 管理员删除 AI 服务商配置。 |
| `admin/ai/providers/fetch-models` | POST | write:admin:meta | id* | 从指定 AI 服务商拉取可用模型列表。 |
| `admin/ai/providers/list` | POST | read:admin:meta | - | 管理员查看 AI 服务商列表。 |
| `admin/ai/providers/test` | POST | read:admin:meta | id* | 测试 AI 服务商配置是否可用。 |
| `admin/ai/providers/update` | POST | write:admin:meta | id*, name, baseUrl, apiKey, isEnabled, models, defaultModel, allowedModels | 管理员更新 AI 服务商配置。 |
| `admin/ai/settings/show` | POST | read:admin:meta | - | 查看实例级 AI 功能设置。 |
| `admin/ai/settings/update` | POST | write:admin:meta | enableAi, showAiInNavbar, aiDefaultProviderId, aiMaxContextMessages | 更新实例级 AI 功能设置。 |
| `admin/api/access-requests/approve` | POST | write:admin:api | id*, reviewNote | 批准 API 接入申请。 |
| `admin/api/access-requests/list` | POST | read:admin:api | status, query, userId, withTotal, limit, offset, sort | 查看 API 接入申请列表。 |
| `admin/api/access-requests/reject` | POST | write:admin:api | id*, reviewNote | 拒绝 API 接入申请。 |
| `admin/api/access-requests/suspend` | POST | write:admin:api | id*, reviewNote | 暂停 API 接入申请或申请方。 |
| `admin/api/apps/approve` | POST | write:admin:api | appId*, reviewNote | 批准第三方 API 应用。 |
| `admin/api/apps/delete` | POST | write:admin:api | appId* | 删除第三方 API 应用。 |
| `admin/api/apps/delete-bulk` | POST | write:admin:api | appIds, ownerless | 批量删除第三方 API 应用。 |
| `admin/api/apps/list` | POST | read:admin:api | status, query, userId, ownerless, withTotal, limit, offset | 管理员查看第三方 API 应用列表。 |
| `admin/api/apps/recover-owners` | POST | write:admin:api | - | 恢复或修正 API 应用拥有者关系。 |
| `admin/api/apps/reject` | POST | write:admin:api | appId*, reviewNote | 拒绝第三方 API 应用。 |
| `admin/api/apps/set-owner` | POST | write:admin:api | appId*, userId* | 设置 API 应用拥有者。 |
| `admin/api/apps/show` | POST | read:admin:api | appId* | 查看某个 API 应用详情。 |
| `admin/api/apps/suspend` | POST | write:admin:api | appId*, reviewNote | 暂停第三方 API 应用。 |
| `admin/api/apps/unsuspend` | POST | write:admin:api | appId*, reviewNote | 恢复已暂停的 API 应用。 |
| `admin/api/apps/update` | POST | write:admin:api | appId*, name, description, permission, callbackUrls, rateLimitPerMinute, reviewNote | 更新第三方 API 应用配置。 |
| `admin/api/settings/show` | POST | read:admin:api | - | 查看实例 API 接入相关设置。 |
| `admin/api/settings/update` | POST | write:admin:api | mode, oauthEnabled, oidcEnabled, requireAppApproval, publicPermissions, noApprovalPermissions, allowDeveloperTokens, defaultTokenRateLimit | 更新实例 API 接入相关设置。 |
| `admin/api/tokens/list` | POST | read:admin:api | status, query, userId, withTotal, limit, offset | 管理员查看 API token 列表。 |
| `admin/api/tokens/revoke` | POST | write:admin:api | tokenId* | 吊销单个 API token。 |
| `admin/api/tokens/revoke-bulk` | POST | write:admin:api | tokenIds, name, userId, onlyDeveloperTokens | 批量吊销 API token。 |
| `admin/api/tokens/suspend` | POST | write:admin:api | tokenId* | 暂停 API token。 |
| `admin/api/tokens/update` | POST | write:admin:api | tokenId*, permission, rank, rateLimitPerMinute, name | 更新 API token 状态或配置。 |
| `admin/api/usage/summary` | POST | read:admin:api | - | 查看 API 使用量汇总。 |
| `admin/chat/purge-keyword` | POST | write:admin:meta | keywords | 按关键词清理聊天内容。 |
| `admin/drive/set-user-capacity` | POST | write:admin:drive | userId*, overrideMb | 管理员设置用户网盘容量。 |
| `admin/drive/user-capacity` | POST | read:admin:drive | userId* | 查看用户网盘容量配置。 |
| `admin/fingerprint-clusters` | POST | read:admin:user-ips | by, minAccounts, limit, offset | 查看设备/浏览器指纹聚类结果。 |
| `admin/get-user-fingerprints` | POST | read:admin:user-ips | userId* | 查看指定用户关联的指纹信息。 |
| `admin/notes/archive-purge` | POST | write:admin:note | ids, all | 清理已归档帖子。 |
| `admin/notes/archived-list` | POST | read:admin:note | limit, offset, username, query, deletedById, sinceDate, untilDate | 查看已归档帖子列表。 |
| `admin/notes/delete-bulk` | POST | write:admin:note | noteIds, filter, reason | 批量删除帖子。 |
| `admin/notes/emergency-ban` | POST | write:admin:note | ip, fingerprint, userIds, deleteNotes, reason | 对帖子执行紧急封禁/处置。 |
| `admin/notes/list` | POST | read:admin:note | limit, offset, sort, search, query, userId, username, visibility | 管理员查看帖子列表。 |
| `admin/notes/related-accounts` | POST | read:admin:note | ip, fingerprint | 查看帖子相关账号关系。 |
| `admin/notes/restore` | POST | write:admin:note | id* | 恢复已删除或已归档帖子。 |
| `admin/recommendation/backfill-sentiment` | POST | write:admin:recommendation | days, limit | 回填推荐系统情绪/语义数据。 |
| `admin/recommendation/get-config` | POST | read:admin:recommendation | - | 读取推荐系统配置。 |
| `admin/recommendation/pinned-list` | POST | read:admin:recommendation | - | 查看推荐系统置顶内容列表。 |
| `admin/recommendation/show` | POST | read:admin:recommendation | noteId* | 查看某条推荐记录详情。 |
| `admin/recommendation/update` | POST | write:admin:recommendation | noteId*, pinned, scoreBoost | 更新推荐记录或推荐状态。 |
| `admin/recommendation/update-config` | POST | write:admin:recommendation | enabled, rules, channelBoost, excludeThreshold, sentiment | 更新推荐系统配置。 |
| `admin/search-fingerprints` | POST | read:admin:user-ips | fingerprint, componentKey, componentValue, limit | 按条件搜索指纹信息。 |
| `admin/search-trends/hide` | POST | write:admin:meta | term* | 隐藏搜索趋势条目。 |
| `admin/url-preview/proxy/test` | POST | read:admin:meta | proxyId, proxy, testUrl | 测试 URL 预览代理。 |
| `admin/users-search` | POST | read:admin:show-user | limit, offset, sort, state, origin, username, hostname, email | 管理员搜索用户。 |

## ai

| 接口 | 方法 | 权限/登录 | 主要参数 | 说明 |
| --- | --- | --- | --- | --- |
| `ai/chat` | POST | write:account | conversationId, providerId, model, content, fileIds, systemPrompt | 调用站内 AI 聊天。 |
| `ai/conversations/create` | POST | write:account | providerId, model, title, systemPrompt | 创建 AI 对话。 |
| `ai/conversations/delete` | POST | write:account | conversationId* | 删除 AI 对话。 |
| `ai/conversations/list` | POST | read:account | - | 列出 AI 对话。 |
| `ai/conversations/show` | POST | read:account | conversationId* | 查看 AI 对话详情。 |
| `ai/conversations/update` | POST | write:account | conversationId*, title, systemPrompt | 更新 AI 对话信息。 |
| `ai/messages/delete` | POST | write:account | messageId* | 删除 AI 对话消息。 |
| `ai/messages/list` | POST | read:account | conversationId* | 列出 AI 对话消息。 |
| `ai/status` | POST | read:account | - | 查看 AI 服务可用状态。 |

## api

| 接口 | 方法 | 权限/登录 | 主要参数 | 说明 |
| --- | --- | --- | --- | --- |
| `api/access/request` | POST | write:account | reason*, permissions | 用户提交 API 接入申请。 |
| `api/access/status` | POST | read:account | - | 查询 API 接入申请或接入状态。 |
| `api/apps/create` | POST | write:account | name*, description, permission*, callbackUrl, callbackUrls, websiteUrl, iconUrl | 创建自己的 API 应用。 |
| `api/apps/delete` | POST | write:account | appId* | 删除自己的 API 应用。 |
| `api/apps/list` | POST | read:account | limit, offset | 列出自己的 API 应用。 |
| `api/apps/rotate-secret` | POST | write:account | appId* | 轮换 API 应用密钥。 |
| `api/apps/show` | POST | read:account | appId* | 查看自己的 API 应用详情。 |
| `api/apps/update` | POST | write:account | appId*, name, description, permission, callbackUrl, callbackUrls, websiteUrl, iconUrl | 更新自己的 API 应用。 |
| `api/tokens/create` | POST | 需要登录 | name, description, iconUrl, permission*, rank, rateLimitPerMinute | 创建 API token。 |
| `api/tokens/list` | POST | read:account | limit, offset | 列出自己的 API token。 |
| `api/tokens/revoke` | POST | write:account | tokenId* | 吊销自己的 API token。 |

## channels

| 接口 | 方法 | 权限/登录 | 主要参数 | 说明 |
| --- | --- | --- | --- | --- |
| `channels/by-category` | POST | 无需登录 | category, uncategorized, limit, offset | 按分类获取频道列表。 |
| `channels/categories` | POST | 无需登录 | - | 获取频道分类列表。 |

## chat

| 接口 | 方法 | 权限/登录 | 主要参数 | 说明 |
| --- | --- | --- | --- | --- |
| `chat/messages/context` | POST | read:chat | messageId*, limitBefore, limitAfter | 获取聊天消息上下文。 |
| `chat/rooms/bans/delete` | POST | write:chat | roomId*, userId* | 解除聊天室封禁。 |
| `chat/rooms/bans/list` | POST | read:chat | roomId*, limit, sinceId, untilId | 查看聊天室封禁列表。 |
| `chat/rooms/clear-mute-log` | POST | write:chat | roomId* | 清理聊天室禁言日志。 |
| `chat/rooms/kick` | POST | write:chat | roomId*, userId*, ban | 将成员踢出聊天室。 |
| `chat/rooms/manage/delete-all-messages` | POST | write:chat | roomId*, password* | 管理端删除聊天室全部消息。 |
| `chat/rooms/manage/delete-user-messages` | POST | write:chat | roomId*, userId* | 管理端删除某用户在聊天室内的消息。 |
| `chat/rooms/manage/stats` | POST | read:chat | roomId*, days | 查看聊天室管理统计。 |
| `chat/rooms/manage/update` | POST | write:chat | roomId*, messageRetentionDays* | 管理端更新聊天室设置。 |
| `chat/rooms/mute-log` | POST | read:chat | roomId*, limit | 查看聊天室禁言日志。 |
| `chat/rooms/mute-member` | POST | write:chat | roomId*, userId*, expiresAt | 禁言聊天室成员。 |
| `chat/rooms/unmute-member` | POST | write:chat | roomId*, userId* | 解除聊天室成员禁言。 |
| `chat/rooms/user-mutes/create` | POST | write:chat | roomId*, userId* | 创建用户级聊天室禁言。 |
| `chat/rooms/user-mutes/delete` | POST | write:chat | roomId*, userId* | 删除用户级聊天室禁言。 |
| `chat/rooms/user-mutes/list` | POST | read:chat | roomId*, limit, sinceId, untilId | 查看用户级聊天室禁言列表。 |

## i

| 接口 | 方法 | 权限/登录 | 主要参数 | 说明 |
| --- | --- | --- | --- | --- |
| `i/update-fingerprint` | POST | write:account | fingerprint*, components | 更新当前账号的设备/浏览器指纹。 |

## notes

| 接口 | 方法 | 权限/登录 | 主要参数 | 说明 |
| --- | --- | --- | --- | --- |
| `notes/discovery-sections` | POST | 无需登录 | limit | 获取发现页帖子分区。 |
| `notes/recommendation-feedback` | POST | write:notes | noteId*, event*, dwellMs | 提交推荐内容反馈。 |
| `notes/recommended-timeline` | POST | 无需登录 | scope, surface, category, sort, rankMode, withFiles, withRenotes, withBots | 获取推荐时间线。 |
| `notes/search-trends` | POST | 无需登录 | limit | 获取帖子搜索趋势。 |

## users

| 接口 | 方法 | 权限/登录 | 主要参数 | 说明 |
| --- | --- | --- | --- | --- |
| `users/note-channels` | POST | 无需登录 | userId*, limit | 查询用户发帖关联的频道。 |

