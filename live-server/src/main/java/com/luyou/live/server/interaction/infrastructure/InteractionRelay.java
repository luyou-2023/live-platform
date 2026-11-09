package com.luyou.live.server.interaction.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.luyou.live.server.interaction.domain.InteractionEvents.*;
import com.luyou.live.server.live.domain.RoomEvents.RoomEnded;
import com.luyou.live.server.live.domain.RoomEvents.RoomStarted;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class InteractionRelay {

    private final WsSessionRegistry registry;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public InteractionRelay(WsSessionRegistry registry, StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.registry = registry;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRoomStarted(RoomStarted e) throws Exception {
        broadcast(e.roomId(), "room.started", Map.of("roomId", e.roomId()));
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRoomEnded(RoomEnded e) throws Exception {
        broadcast(e.roomId(), "room.ended", Map.of("roomId", e.roomId()));
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCommentPosted(CommentPosted e) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("commentId", e.commentId());
        payload.put("roomId", e.roomId());
        payload.put("userId", e.userId());
        payload.put("nickname", e.nickname());
        payload.put("content", e.content());
        payload.put("sentAt", e.occurredAt().toString());
        broadcast(e.roomId(), "comment.new", payload);
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLikeReceived(LikeReceived e) throws Exception {
        broadcast(e.roomId(), "like.batch", Map.of("total", e.total(), "delta", e.delta()));
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onViewerJoined(ViewerJoined e) throws Exception {
        broadcast(e.roomId(), "presence.heartbeat", Map.of("online", e.onlineCount()));
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onViewerLeft(ViewerLeft e) throws Exception {
        broadcast(e.roomId(), "presence.heartbeat", Map.of("online", e.onlineCount()));
    }

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserMuted(UserMuted e) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("roomId", e.roomId());
        payload.put("until", e.until().toString());
        registry.sendToUser(e.userId(), frame("comment.muted", payload));
    }

    private void broadcast(String roomId, String type, Map<String, Object> payload) throws Exception {
        registry.broadcast(roomId, frame(type, payload));
    }

    private String frame(String type, Map<String, Object> payload) throws Exception {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", type);
        frame.put("payload", payload);
        return objectMapper.writeValueAsString(frame);
    }
}
