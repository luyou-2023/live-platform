package com.luyou.live.server.user.domain;

import java.time.Instant;
import java.util.UUID;

public final class UserEvents {
    private UserEvents() {}

    public record UserRegistered(String eventId, String userId, String nickname, Instant occurredAt) {
        public static UserRegistered of(String userId, String nickname) {
            return new UserRegistered(UUID.randomUUID().toString(), userId, nickname, Instant.now());
        }
    }
}
