package com.luyou.live.server.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "livekit")
public record LivekitConfig(String url, String apiKey, String apiSecret) {}
