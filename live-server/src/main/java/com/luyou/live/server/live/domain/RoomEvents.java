package com.luyou.live.server.live.domain;

import java.time.Instant;
import java.util.UUID;

public final class RoomEvents {
    private RoomEvents() {}

    public record RoomCreated(String eventId, String roomId, String anchorId, String title, Instant occurredAt) {
        public static RoomCreated of(LiveRoom r) {
            return new RoomCreated(UUID.randomUUID().toString(), r.getId(), r.getAnchorId(), r.getTitle(), Instant.now());
        }
    }

    public record RoomStarted(String eventId, String roomId, String anchorId, int sessionSeq, Instant occurredAt) {
        public static RoomStarted of(LiveRoom r) {
            return new RoomStarted(UUID.randomUUID().toString(), r.getId(), r.getAnchorId(), r.getSessionSeq(), Instant.now());
        }
    }

    public record RoomEnded(String eventId, String roomId, String anchorId, int sessionSeq, Instant occurredAt) {
        public static RoomEnded of(LiveRoom r) {
            return new RoomEnded(UUID.randomUUID().toString(), r.getId(), r.getAnchorId(), r.getSessionSeq(), Instant.now());
        }
    }
}
