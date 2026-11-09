package com.luyou.live.server.interaction.domain;

import java.time.Instant;
import java.util.UUID;

public final class InteractionEvents {
    private InteractionEvents() {}

    public record CommentPosted(String eventId, String commentId, String roomId, int sessionSeq,
                                String userId, String nickname, String content, Instant occurredAt) {
        public static CommentPosted of(Comment c) {
            return new CommentPosted(UUID.randomUUID().toString(), c.getId(), c.getRoomId(),
                    c.getSessionSeq(), c.getUserId(), c.getNickname(), c.getContent(), Instant.now());
        }
    }

    public record UserMuted(String eventId, String roomId, String userId, Instant until, Instant occurredAt) {
        public static UserMuted of(String roomId, String userId, Instant until) {
            return new UserMuted(UUID.randomUUID().toString(), roomId, userId, until, Instant.now());
        }
    }

    public record LikeReceived(String eventId, String roomId, long total, long delta, Instant occurredAt) {
        public static LikeReceived of(String roomId, long total, long delta) {
            return new LikeReceived(UUID.randomUUID().toString(), roomId, total, delta, Instant.now());
        }
    }

    public record ViewerJoined(String eventId, String roomId, String userId, long onlineCount, Instant occurredAt) {
        public static ViewerJoined of(String roomId, String userId, long onlineCount) {
            return new ViewerJoined(UUID.randomUUID().toString(), roomId, userId, onlineCount, Instant.now());
        }
    }

    public record ViewerLeft(String eventId, String roomId, String userId, long onlineCount, Instant occurredAt) {
        public static ViewerLeft of(String roomId, String userId, long onlineCount) {
            return new ViewerLeft(UUID.randomUUID().toString(), roomId, userId, onlineCount, Instant.now());
        }
    }
}
