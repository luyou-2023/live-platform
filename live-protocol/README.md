# live-protocol v1

主播端（live-studio）、观众端（live-audience）与 live-server 之间的全部协议定义。
任何协议变更必须先改本文档再改代码。

## 通用约定

- 传输：HTTPS / WSS；本地开发用 HTTP / WS。
- 编码：JSON（UTF-8）。
- 鉴权：HTTP 接口用 `Authorization: Bearer <token>`；WebSocket 用首帧鉴权（见 4.2）。
- 错误格式：

```json
{ "code": 40001, "message": "room.not_found", "detail": {} }
```

| code 区间 | 含义             |
| --------- | ---------------- |
| 400xx     | 参数或状态错误   |
| 401xx     | 未认证 / 认证过期 |
| 403xx     | 无权限           |
| 404xx     | 资源不存在       |
| 409xx     | 状态冲突         |
| 500xx     | 服务端错误       |

## 1. 用户

### 1.1 注册
`POST /api/v1/users/register`
```json
{ "username": "string", "password": "string", "nickname": "string" }
```
→ `{ "userId": "string", "token": "string" }`

### 1.2 登录
`POST /api/v1/users/login`
```json
{ "username": "string", "password": "string" }
```
→ `{ "userId": "string", "nickname": "string", "token": "string" }`

## 2. 直播间

### 2.1 创建直播间（主播）
`POST /api/v1/rooms`（需主播身份）
```json
{ "title": "string", "coverUrl": "string?" }
```
→ `{ "roomId": "string", "status": "CREATED" }`

### 2.2 开播
`POST /api/v1/rooms/{roomId}/start`
→ `{ "roomId": "string", "status": "LIVING", "livekit": { "url": "wss://...", "token": "publisher-token" } }`

### 2.3 下播
`POST /api/v1/rooms/{roomId}/stop`
→ `{ "roomId": "string", "status": "ENDED" }`

### 2.4 直播列表（观众发现页）
`GET /api/v1/rooms?cursor=<string>&limit=20`
→ `{ "items": [ { "roomId": "string", "title": "string", "coverUrl": "string", "anchorId": "string", "anchorNickname": "string", "onlineCount": 0, "status": "LIVING" } ], "nextCursor": "string?" }`

### 2.5 房间详情 / 进入房间
`GET /api/v1/rooms/{roomId}`
→ `{ "roomId": "string", "title": "string", "anchor": {...}, "onlineCount": 0, "status": "LIVING", "livekit": { "url": "wss://...", "token": "subscriber-token" } }`

## 3. 关注

### 3.1 关注 / 取关
`POST /api/v1/users/{userId}/follow` → `{ "following": true }`
`DELETE /api/v1/users/{userId}/follow` → `{ "following": false }`

## 4. 实时互动（WebSocket）

### 4.1 连接
`WS /ws/v1/rooms/{roomId}?token=<jwt>`

### 4.2 首帧鉴权（连接建立后 5 秒内必须发送）
客户端 → 服务端：
```json
{ "type": "auth", "payload": { "token": "string" } }
```
服务端 → 客户端：
```json
{ "type": "auth.ok", "payload": { "userId": "string", "nickname": "string" } }
```
失败：`{ "type": "auth.fail", "payload": { "reason": "..." } }` 并断开。

### 4.3 评论
客户端 → 服务端：
```json
{ "type": "comment.send", "payload": { "content": "string(≤500)" }, "seq": 1 }
```
服务端 → 房间内所有人：
```json
{ "type": "comment.new", "payload": { "commentId": "string", "userId": "string", "nickname": "string", "content": "string", "sentAt": 1730000000 } }
```

### 4.4 点赞
客户端 → 服务端：
```json
{ "type": "like.send", "payload": { "count": 1 }, "seq": 2 }
```
服务端 → 房间内所有人（聚合心跳，1 秒内合并）：
```json
{ "type": "like.batch", "payload": { "roomId": "string", "total": 12345, "delta": 42 } }
```

### 4.5 在线人数
服务端 → 房间内所有人（5 秒心跳）：
```json
{ "type": "presence.heartbeat", "payload": { "roomId": "string", "onlineCount": 128 } }
```

### 4.6 主播管控（禁言）
主播客户端 → 服务端：
```json
{ "type": "comment.mute", "payload": { "userId": "string", "seconds": 300 }, "seq": 3 }
```
服务端 → 被禁言用户：
```json
{ "type": "comment.muted", "payload": { "until": 1730000300 } }
```

### 4.7 房间结束
服务端 → 房间内所有人：
```json
{ "type": "room.ended", "payload": { "roomId": "string" } }
```

## 5. LiveKit token 规则

- token 由 live-server 用 LiveKit Server 的 API（io.livekit:livekit-server）签发。
- 身份：`identity=<userId>`；主播房间加 `roomAdmin=true`。
- 权限：发布者（主播）canPublish=true、canSubscribe=true；观众 canPublish=false、canSubscribe=true。
- 有效期：开播/进房时签发，2 小时，过期由客户端刷新（重调 2.2/2.5）。
