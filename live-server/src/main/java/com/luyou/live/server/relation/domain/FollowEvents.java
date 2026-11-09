package com.luyou.live.server.relation.domain;

import java.time.Instant;
import java.util.UUID;

public final class FollowEvents {
    private FollowEvents() {}

    public record UserFollowed(String eventId, String followerId, String followeeId, Instant occurredAt) {
        public static UserFollowed of(String followerId, String followeeId) {
            return new UserFollowed(UUID.randomUUID().toString(), followerId, followeeId, Instant.now());
        }
    }

    public record UserUnfollowed(String eventId, String followerId, String followeeId, Instant occurredAt) {
        public static UserUnfollowed of(String followerId, String followeeId) {
            return new UserUnfollowed(UUID.randomUUID().toString(), followerId, followeeId, Instant.now());
        }
    }
}
