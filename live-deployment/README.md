# live-deployment

本地开发部署：MySQL 8 + Redis 7 + LiveKit Server。

## 启动

```bash
docker compose up -d

# LiveKit 健康检查
curl http://localhost:7880
```

## 服务与端口

| 服务        | 端口                  | 说明                     |
| ----------- | --------------------- | ------------------------ |
| MySQL       | 13306 → 3306          | root / root，库 live_platform |
| Redis       | 16379 → 6379          | 无密码（仅本地）         |
| LiveKit     | 7880(HTTP) 7881(WS)   | devkey / secret          |

## live-server 连接配置

`live-server/src/main/resources/application.yml` 默认连 `ws://localhost:7880`（devkey/secret），与 compose 默认值一致。

> API Gateway 第一期用 nginx 兜底，需要时在此目录追加配置。
