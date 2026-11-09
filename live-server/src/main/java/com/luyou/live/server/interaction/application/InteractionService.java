package com.luyou.live.server.interaction.application;

import com.luyou.live.server.common.ApiException;
import com.luyou.live.server.interaction.domain.*;
import com.luyou.live.server.interaction.domain.InteractionEvents.*;
import com.luyou.live.server.live.domain.LiveRoom;
import com.luyou.live.server.live.domain.LiveRoomRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
public class InteractionService {

    private final CommentRepository commentRepository;
    private final MuteRepository muteRepository;
    private final LiveRoomRepository roomRepository;
    private final StringRedisTemplate redis;
    private final ApplicationEventPublisher eventPublisher;
    private final Executor likeExecutor;

    public InteractionService(CommentRepository commentRepository, MuteRepository muteRepository,
                              LiveRoomRepository roomRepository, StringRedisTemplate redis,
                              ApplicationEventPublisher eventPublisher,
                              @Qualifier("likeExecutor") Executor likeExecutor) {
        this.commentRepository = commentRepository;
        this.muteRepository = muteRepository;
        this.roomRepository = roomRepository;
        this.redis = redis;
        this.eventPublisher = eventPublisher;
        this.likeExecutor = likeExecutor;
    }

    @Transactional
    public CommentView postComment(String roomId, String userId, String nickname, String content) {
        if (content == null || content.isBlank() || content.length() > 500) {
            throw new ApiException(40003, "comment length 1..500");
        }
        LiveRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ApiException(40404, "room not found"));
        if (room.getStatus() != LiveRoom.RoomStatus.LIVING) throw new ApiException(40906, "room not living");
        if (isMuted(roomId, userId)) throw new ApiException(40304, "you are muted");
        Comment comment = commentRepository.save(Comment.post(UUID.randomUUID().toString(), roomId,
                room.getSessionSeq(), userId, nickname, content.trim()));
        eventPublisher.publishEvent(CommentPosted.of(comment));
        return toView(comment);
    }

    public void like(String roomId) {
        redis.opsForValue().increment("like:total:" + roomId);
        redis.opsForValue().increment("like:window:" + roomId);
        // 窗口首赞负责 1 秒后聚合广播
        Boolean first = redis.opsForValue()
                .setIfAbsent("like:window:first:" + roomId, "1", Duration.ofSeconds(1));
        if (Boolean.TRUE.equals(first)) {
            likeExecutor.execute(() -> {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) { }
                String deltaStr = redis.opsForValue().getAndSet("like:window:" + roomId, "0");
                long delta = deltaStr == null ? 0 : Long.parseLong(deltaStr);
                String totalStr = redis.opsForValue().get("like:total:" + roomId);
                long total = totalStr == null ? 0 : Long.parseLong(totalStr);
                if (delta > 0) eventPublisher.publishEvent(LikeReceived.of(roomId, total, delta));
            });
        }
    }

    public void join(String roomId, String userId) {
        redis.opsForSet().add("presence:" + roomId, userId);
        redis.expire("presence:" + roomId, Duration.ofHours(2));
        long online = redis.opsForSet().size("presence:" + roomId);
        eventPublisher.publishEvent(ViewerJoined.of(roomId, userId, online));
    }

    public void leave(String roomId, String userId) {
        redis.opsForSet().remove("presence:" + roomId, userId);
        long online = redis.opsForSet().size("presence:" + roomId);
        eventPublisher.publishEvent(ViewerLeft.of(roomId, userId, online));
    }

    public long onlineCount(String roomId) {
        Long n = redis.opsForSet().size("presence:" + roomId);
        return n == null ? 0 : n;
    }

    public boolean isMuted(String roomId, String userId) {
        String until = redis.opsForValue().get("muted:" + roomId + ":" + userId);
        if (until == null) return false;
        return Instant.ofEpochMilli(Long.parseLong(until)).isAfter(Instant.now());
    }

    @Transactional
    public void mute(String roomId, String operatorId, String targetUserId, long minutes) {
        LiveRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ApiException(40404, "room not found"));
        room.assertAnchor(operatorId);
        Instant until = Instant.now().plus(Duration.ofMinutes(minutes));
        muteRepository.findByRoomIdAndUserId(roomId, targetUserId)
                .ifPresentOrElse(m -> {
                    m = Mute.of(m.getId(), roomId, targetUserId, until);
                    muteRepository.save(m);
                }, () -> muteRepository.save(
                        Mute.of(UUID.randomUUID().toString(), roomId, targetUserId, until)));
        redis.opsForValue().set("muted:" + roomId + ":" + targetUserId,
                String.valueOf(until.toEpochMilli()), Duration.ofMinutes(minutes));
        eventPublisher.publishEvent(UserMuted.of(roomId, targetUserId, until));
    }

    @Transactional
    public void unmute(String roomId, String operatorId, String targetUserId) {
        LiveRoom room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ApiException(40404, "room not found"));
        room.assertAnchor(operatorId);
        muteRepository.findByRoomIdAndUserId(roomId, targetUserId)
                .ifPresent(muteRepository::delete);
        redis.delete("muted:" + roomId + ":" + targetUserId);
    }

    private CommentView toView(Comment c) {
        return new CommentView(c.getId(), c.getRoomId(), c.getUserId(), c.getNickname(),
                c.getContent(), c.getSentAt().toString(), c.isPinned());
    }

    public record CommentView(String commentId, String roomId, String userId, String nickname,
                              String content, String sentAt, boolean pinned) {}
}
