# live-server

LivePlatform 后端（Java / Spring Boot 3，JDK 17+）。

## 运行

```bash
# 编译 + 单测
mvn verify

# 本地起服务
mvn spring-boot:run

# 健康检查
curl http://localhost:8080/healthz
```

## 包结构（按 docs/architecture.md）

```
com.luyou.live.server/
├── user/        # 注册登录、用户资料（待建）
├── room/        # 直播间创建、开播、下播、列表（待建）
├── relation/    # 关注（待建）
├── interaction/ # 评论、点赞、在线人数 WebSocket（待建）
└── media/       # LiveKit token 签发、房间状态查询（待建）
```

当前为第一期脚手架：仅健康检查接口 + LiveKit SDK 依赖已就位。
