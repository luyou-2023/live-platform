package com.luyou.live.server.live.domain;

import java.time.Instant;

public record LivekitToken(String serverUrl, String roomName, String identity, String token, Instant expiresAt) {}
