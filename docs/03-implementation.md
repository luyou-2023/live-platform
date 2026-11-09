# live-platform 实现文档

> 版本：v1（第一期） · 2026-11-09 · 配套《结构文档》《设计文档》
> 目标读者：写第一行代码前必读；所有代码以本文档为准绳。

## 1. 技术栈与版本基线

| 件 | 版本 | 备注 |
| --- | --- | --- |
| JDK | 17 | 语言特性上限：record、sealed、pattern matching 可用 |
| Spring Boot | 3.3.x | BOM 由 spring-boot-starter-parent 管 |
| livekit-server SDK | 0.5.x | 仅 media 域使用 |
| MySQL | 8.4 | InnoDB，utf8mb4 |
| Redis | 7.x | 在线集合/计数/幂等/读模型 |
| Node | 20+ | 前端 Vite 构建 |

## 2. live-server 包结构

```
com.luyou.live.server/
├── common/                  # ApiException、错误码、Page、Json 配置、时钟 Bean
├── user/
│   ├── api/                 # UserController（注册/登录）
│   ├── application/         # UserService
│   ├── domain/              # User(聚合)、UserRegistered、UserRepository(接口)
│   └── infrastructure/      # UserJpaRepository(实现)、JwtIssuer
├── relation/                # 结构同上：Follow 聚合 + 事件
├── live/
│   ├── api/                 # RoomController
│   ├── application/         # RoomService、TokenAppService
│   ├── domain/              # LiveRoom、RoomStatus、LivekitToken(VO)、Room*事件、LiveRoomRepository
│   └── infrastructure/      # LiveRoomJpaRepository、LiveKitTokenIssuer、LivekitJanitor(@EventListener)
├── interaction/
│   ├── api/                 # InteractionWsEndpoint（/ws/v1/rooms/{roomId}）
│   ├── application/         # CommentService、MuteService、LikeAppService、PresenceAppService
│   ├── domain/              # Comment、MuteRecord、LikeCounter、PresenceTracker、Interaction*事件
│   └── infrastructure/      # WsSessionRegistry、RedisLikeCounter、RedisPresenceTracker、
│                            # InteractionRelay（事件→WS 广播）、ReadModelUpdater
└── LiveServerApplication
```

前端各端 `src/api/` 只放 live-protocol 的实现客户端，禁止自造协议字段。

## 3. 关键机制骨架

### 3.1 领域事件基类与记录

```java
public abstract class DomainEvent {
    private final String eventId = UUID.randomUUID().toString();
    private final Instant occurredAt = Instant.now();
    // getter
}

// 聚合根基类：领域方法内 record()，Repository save 时随聚合带出
public abstract class AggregateRoot {
    private final transient List<DomainEvent> domainEvents = new ArrayList<>();
    protected void record(DomainEvent event) { domainEvents.add(event); }
    public List<DomainEvent> pullEvents() { var e = List.copyOf(domainEvents); domainEvents.clear(); return e; }
}
```

### 3.2 事务性派发

```java
// Application Service 模板
@Transactional
public RoomStartResult start(String roomId, String operatorId) {
    var room = roomRepository.load(roomId).orElseThrow(() -> new ApiException(40404, "room.not_found"));
    room.start(operatorId);                    // 聚合内断言身份与状态迁移
    roomRepository.save(room);                 // 事件随聚合一起落库
    room.pullEvents().forEach(eventPublisher::publishEvent); // 事务内暂存
    return ...;
}

// 订阅者：提交后异步执行
@Component
public class InteractionRelay {
    @Async("eventExecutor")
    @TransactionalEventListener(phase = AFTER_COMMIT)
    public void on(RoomStarted event) { wsRegistry.broadcast(event.roomId(), "room.started", event); }
}
```

### 3.3 LiveKit token 签发（media）

```java
@Component
public class LiveKitTokenIssuer implements TokenIssuer {
    private final AccessToken token = new AccessToken(apiKey, apiSecret);
    public LivekitToken issuePublisher(String roomName, String identity) {
        token.setName(identity).setIdentity(identity).setRoomAdmin(true);
        token.addGrant(new RoomJoin(true), new RoomPublish(true), new RoomSubscribe(true));
        return new LivekitToken(roomName, identity, token.toJwt(), Instant.now().plus(2, HOURS));
    }
}
```

### 3.4 WebSocket 端点与首帧鉴权

```java
@Component
public class InteractionWsEndpoint extends TextWebSocketHandler {
    // 连接建立：仅注册会话，标记未鉴权
    // 首帧必须为 {type:"auth", payload:{token}}：验 JWT、校验房间存在且 LIVING
    // 通过：SADD presence:{roomId} -> 发布 ViewerJoined -> 返回 auth.ok
    // 5 秒未鉴权 / 校验失败：发 auth.fail 并关闭
    // 之后按协议分发 comment.send / like.send；连接关闭：SREM + 发布 ViewerLeft
}
```

广播规则：InteractionRelay 只依赖 `WsSessionRegistry`（roomId → sessions 映射）；定向消息（comment.muted）按 userId 索引会话。

### 3.5 Redis key 规范

| key | 类型 | 说明 |
| --- | --- | --- |
| `presence:{roomId}` | set | 在线 userId 集合，成员带 TTL 兜底 |
| `like:total:{roomId}` | string | 房间累计赞数（INCR） |
| `like:window:{roomId}` | string | 1 秒聚合窗口 delta |
| `muted:{roomId}:{userId}` | string | 禁言截止戳 |
| `event:handled:{eventId}` | string | 订阅者幂等标记，24h |
| `rm:follower_count:{userId}` | string | 粉丝数读模型 |
| `rm:live_list` | zset | 直播列表读模型（score=startedAt） |

### 3.6 MySQL 表草案（核心列）

- `lp_user`(id, username_uk, password_hash, nickname, avatar_url, status, created_at)
- `lp_follow`(id, follower_id, followee_id, created_at, uk(follower_id, followee_id))
- `lp_live_room`(id, anchor_id, title, cover_url, status, session_seq, started_at, ended_at, version)
- 部分唯一索引：`(anchor_id, status)` 过滤 `status='LIVING'`，保证单主播单直播间
- `lp_comment`(id, room_id, session_seq, user_id, nickname, content, sent_at, pinned, blocked)
- `lp_mute`(id, room_id, user_id, until, created_at)
- `lp_domain_event`(id, type, payload_json, occurred_at) — 审计/排查用，非 outbox

## 4. API ↔ 协议 ↔ 实现映射

| 协议（live-protocol） | Controller | Application | Repository/适配 |
| --- | --- | --- | --- |
| 1.1 注册 | UserController | UserService.register | UserRepository |
| 1.2 登录 | UserController | UserService.login | UserRepository + JwtIssuer |
| 2.1 创建房间 | RoomController | RoomService.create | LiveRoomRepository |
| 2.2 开播 | RoomController | RoomService.start | LiveRoomRepository + TokenIssuer |
| 2.3 下播 | RoomController | RoomService.stop | LiveRoomRepository |
| 2.4 直播列表 | RoomController | RoomQueryService | rm:live_list 读模型 |
| 2.5 进房 | RoomController | RoomService.enter | LiveRoomRepository + TokenIssuer + PresenceTracker |
| 3.1 关注/取关 | FollowController | FollowService | FollowRepository |
| 4.x WS 互动 | InteractionWsEndpoint | CommentService / LikeAppService / PresenceAppService | Redis 适配器 + InteractionRelay |

## 5. 编码规约

- ID：对外全用 String（UUID/ULID），DB 内部 bigint 自增；禁止把 DB 自增 id 直接暴露给前端。
- 时间：全链路 UTC Instant，前端自行转本地时区。
- 错误：抛 `ApiException(code, message)`，全局 `@RestControllerAdvice` 转协议错误信封；禁止在 controller 返回裸异常信息。
- 事务：只在 application 层开 `@Transactional`；controller 与事件监听不直接写库（监听可用 REQUIRES_NEW 做读模型）。
- 日志：结构化关键字段（roomId/userId/eventId），严禁打印 token/密码/JWT。
- 配置：所有密钥走环境变量；application.yml 只放默认值与本地开发值。

## 6. 测试策略

| 层 | 手段 | 覆盖目标 |
| --- | --- | --- |
| 聚合/领域服务 | 纯 JUnit（无 Spring） | 状态机迁移、不变量、事件记录 |
| application | @SpringBootTest + H2/Testcontainers MySQL | 事务边界、事件派发、幂等 |
| api | @WebMvcTest / WS 集成测试 | 协议字段、错误码、鉴权 |
| 契约 | live-protocol 为准的人工评审 + 前端联调清单 | 双端一致 |

LiveKit 相关在测试中全部走 `TokenIssuer` 接口 mock；Redis 用 Testcontainers。

## 7. 演进路线（现在不做，预留接口）

1. 多实例部署：事件总线抽象为 `EventBus` 接口，进程内实现替换为 Redis Stream 实现；WS 广播改 Redis pub/sub + 本实例会话映射。
2. 直播列表/粉丝数等读模型独立成查询侧（CQRS 拆分），现在 `rm:` key 即为预留。
3. 回放/录制：LiveKit Egress 接入 media 域，协议升 v2。
4. 礼物/连麦/PK：新增限界上下文（Gift、CoHost），禁止塞进互动上下文。

## 8. 落地顺序（建议）

1. user 上下文（注册/登录/JWT）→ 2. live 上下文建房/开播/下播 + TokenIssuer → 3. 前端两端拉通直播链路（LiveKit JS SDK）→ 4. interaction 上下文 WS + 评论 → 5. 点赞/在线/关注/禁言 → 6. 读模型与列表优化。
