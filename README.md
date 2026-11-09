# live-platform

独立直播产品（与 LiveShop 无任何依赖关系，独立产品线）。

## 应用命名

| 类型   | 产品名称     | 工程名称       | 职责                       |
| ------ | ------------ | -------------- | -------------------------- |
| 主播端 | LiveStudio   | live-studio    | 主播开播、直播控制         |
| 观众端 | LiveAudience | live-audience  | 发现直播、观看、互动       |
| 后端   | LiveServer   | live-server    | 直播业务 API、用户、互动   |
| 音视频 | LiveKit      | 独立服务       | WebRTC、SFU 媒体传输       |

## 技术底座

Java 21（当前以 17 编译运行）+ Spring Boot / React / LiveKit / MySQL / Redis / WebSocket

> 技术底座原为 Go，2026-11-09 拍板改为 Java。

## 架构

```
              独立直播产品
                    │
         ┌──────────┴──────────┐
         │                     │
         ▼                     ▼
    LiveStudio            LiveAudience
      主播端                  观众端
         │                     │
         └──────────┬──────────┘
                    │
                    ▼
               API Gateway
                    │
                    ▼
                LiveServer
                    │
          ┌─────────┼─────────┐
          │         │         │
          ▼         ▼         ▼
        MySQL     Redis    WebSocket
                    │
                    ▼
                  LiveKit
                    │
                    ▼
                 WebRTC
```

## 仓库结构

```
live-platform/
├── live-studio/             # 主播端（React）
├── live-audience/           # 观众端（React）
├── live-server/             # 后端业务（Java / Spring Boot）
├── live-protocol/           # API / WebSocket 协议
├── live-deployment/         # 部署配置
└── docs/                    # 技术设计文档
```

## 第一期范围（轻量版抖音直播）

- 主播：创建直播间、开播、下播
- 观众：浏览直播、进入直播间、实时观看音视频
- 互动：评论、点赞、关注主播
- 主播侧：在线人数、评论管理

第一期不引入：礼物、连麦、PK、录制、回放。
