# live-deployment

本地开发部署：MySQL 8 + Redis 7 + LiveKit Server + LiveKit Ingress（OBS 推流）。

## 启动

```bash
docker compose up -d

# LiveKit 健康检查
curl http://localhost:7880
```

## 服务与端口

| 服务        | 端口                    | 说明                     |
| ----------- | ----------------------- | ------------------------ |
| MySQL       | 13306 → 3306            | root / root，库 live_platform |
| Redis       | 16379 → 6379            | 无密码（仅本地）         |
| LiveKit     | 7880(HTTP) 7881(WS)     | devkey / secret          |
|             | 7882/udp                | WebRTC 媒体（node_ip=127.0.0.1） |
| Ingress     | 19350 → 1935            | RTMP 入口（OBS 推流）    |

## OBS 推流

1. 主播端开播后，控制台显示「OBS 推流」的服务器与串流密钥
2. OBS 设置 → 直播 → 服务选「自定义」：
   - 服务器：`rtmp://127.0.0.1:19350/live`
   - 串流密钥：控制台显示的密钥
3. 点开始推流，观众端即可看到 OBS 画面（与摄像头开播二选一）

> 宿主 1935 端口被本机其他进程占用，故映射到 19350；对外地址由
> `livekit.rtmp-public-url` 配置（见 live-server application.yml）。

## live-server 连接配置

`live-server/src/main/resources/application.yml` 默认连 `ws://localhost:7880`（devkey/secret），与 compose 默认值一致。

> API Gateway 第一期用 nginx 兜底，需要时在此目录追加配置。
