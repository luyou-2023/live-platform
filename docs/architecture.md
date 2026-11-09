# 架构设计（第一期）

## 总则

- 本产品线完全独立，不复用 LiveShop 任何代码、服务与数据。
- 单体起步：`live-server` 一个 Spring Boot 应用承载全部业务 API；未来按业务域拆分。
- LiveKit 只负责媒体面（WebRTC/SFU），业务面只与 LiveKit Server 的控制 API 交互（签发 join token、查询房间状态）。

## 进程视图

| 进程            | 技术                    | 职责                                   |
| --------------- | ----------------------- | -------------------------------------- |
| live-studio     | React (Vite + TS)       | 主播端：开播控制、评论管理、在线人数   |
| live-audience   | React (Vite + TS)       | 观众端：直播发现、观看、评论/点赞/关注 |
| live-server     | Java / Spring Boot      | 业务 API、WebSocket 网关、LiveKit token 签发 |
| api-gateway     | 独立组件（部署期定）    | 入口转发、限流（第一期可用 nginx 兜底） |
| LiveKit Server  | 官方开源服务            | WebRTC SFU，独立部署                    |
| MySQL           | 8.x                     | 业务数据：用户、房间、关注、评论记录   |
| Redis           | 7.x                     | 在线状态、点赞计数、房间热数据         |

## 数据流

1. 主播在 LiveStudio 创建房间 → live-server 写 MySQL → 返回 LiveKit join token（主播身份）。
2. 主播推流：LiveStudio 通过 LiveKit JS SDK 直接连 LiveKit Server 发布音视频。
3. 观众在 LiveAudience 浏览房间列表 → 进入房间 → live-server 签发观众 token → LiveKit 拉流。
4. 互动（评论/点赞/在线人数）走 WebSocket：客户端 ⇄ live-server（Redis 做房间级广播与计数）。
5. 主播的管控动作（禁言/关播）经 live-server 校验后广播。

## 模块划分（live-server 内部）

```
com.luyou.live.server/
├── user/        # 注册登录、用户资料
├── room/        # 直播间创建、开播、下播、列表
├── relation/    # 关注
├── interaction/ # 评论、点赞、在线人数（WebSocket）
└── media/       # LiveKit token 签发、房间状态查询
```

## 关键决策

| 决策点           | 结论                                       |
| ---------------- | ------------------------------------------ |
| 后端语言         | Java（Spring Boot），原 Go 设计作废        |
| 实时互动通道     | WebSocket（由 live-server 自建，不经 LiveKit data channel） |
| 点赞/在线数      | Redis 计数，定时/事件式落 MySQL            |
| 媒体与业务分离   | LiveKit 只碰音视频；业务永不进入媒体面     |
| 前后端协议       | 统一在 live-protocol/ 维护，版本化 v1      |

## 第一期明确不做

礼物、连麦、PK、录制、回放、多房间合流、CDN 转推。
