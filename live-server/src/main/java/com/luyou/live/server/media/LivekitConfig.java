package com.luyou.live.server.media;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "livekit")
public record LivekitConfig(
        String url,
        String apiKey,
        String apiSecret,
        /** OBS 推流对外地址（宿主端口映射后的 RTMP URL，路径须与 ingress 一致） */
        @DefaultValue("rtmp://127.0.0.1:19350/live") String rtmpPublicUrl) {}
