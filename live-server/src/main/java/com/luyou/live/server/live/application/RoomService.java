package com.luyou.live.server.live.application;

import com.luyou.live.server.common.ApiException;
import com.luyou.live.server.live.domain.LiveRoom;
import com.luyou.live.server.live.domain.LiveRoomRepository;
import com.luyou.live.server.live.domain.LivekitToken;
import com.luyou.live.server.live.domain.RoomEvents.RoomCreated;
import com.luyou.live.server.live.domain.RoomEvents.RoomEnded;
import com.luyou.live.server.live.domain.RoomEvents.RoomStarted;
import com.luyou.live.server.media.LiveKitTokenIssuer.ObsPublish;
import com.luyou.live.server.media.TokenIssuer;
import com.luyou.live.server.user.domain.User;
import com.luyou.live.server.user.domain.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RoomService {

    private final LiveRoomRepository roomRepository;
    private final UserRepository userRepository;
    private final TokenIssuer tokenIssuer;
    private final ApplicationEventPublisher eventPublisher;

    public RoomService(LiveRoomRepository roomRepository, UserRepository userRepository,
                       TokenIssuer tokenIssuer, ApplicationEventPublisher eventPublisher) {
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.tokenIssuer = tokenIssuer;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public RoomView create(String anchorId, String title, String coverUrl) {
        long living = roomRepository.countByAnchorIdAndStatus(anchorId, LiveRoom.RoomStatus.LIVING);
        if (living > 0) throw new ApiException(40905, "anchor already has a living room");
        LiveRoom room = roomRepository.save(
                LiveRoom.create(UUID.randomUUID().toString(), anchorId, title, coverUrl));
        eventPublisher.publishEvent(RoomCreated.of(room));
        return toView(room, anchorNickname(anchorId));
    }

    @Transactional
    public StartResult start(String roomId, String operatorId) {
        LiveRoom room = load(roomId);
        room.assertAnchor(operatorId);
        // 媒体面先备好，再切状态
        tokenIssuer.closeRoom(room.getId()); // 幂等：清理可能残留的同名房间
        room.start();
        roomRepository.save(room);
        LivekitToken token = tokenIssuer.issuePublisher(room.getId(), operatorId);
        eventPublisher.publishEvent(RoomStarted.of(room));
        return new StartResult(toView(room, anchorNickname(operatorId)), token, safeObs(room.getId()));
    }

    /** 直播中补签发布 token（页面刷新后重新推流，不改变房间状态） */
    @Transactional(readOnly = true)
    public StartResult republish(String roomId, String operatorId) {
        LiveRoom room = load(roomId);
        room.assertAnchor(operatorId);
        if (room.getStatus() != LiveRoom.RoomStatus.LIVING) throw new ApiException(40906, "room not living");
        LivekitToken token = tokenIssuer.issuePublisher(room.getId(), operatorId);
        return new StartResult(toView(room, anchorNickname(operatorId)), token, safeObs(room.getId()));
    }

    /** OBS 入口故障不阻断开播（摄像头路径不受影响） */
    private ObsPublish safeObs(String roomId) {
        try { return tokenIssuer.ensureObsPublish(roomId); }
        catch (Exception e) { return null; }
    }

    /** 主播查询 OBS 推流信息（幂等） */
    @Transactional(readOnly = true)
    public ObsPublish obsInfo(String roomId, String operatorId) {
        LiveRoom room = load(roomId);
        room.assertAnchor(operatorId);
        return tokenIssuer.ensureObsPublish(room.getId());
    }

    @Transactional
    public RoomView stop(String roomId, String operatorId) {
        LiveRoom room = load(roomId);
        room.assertAnchor(operatorId);
        room.stop();
        roomRepository.save(room);
        eventPublisher.publishEvent(RoomEnded.of(room));
        return toView(room, anchorNickname(operatorId));
    }

    @Transactional(readOnly = true)
    public EnterResult enter(String roomId, String userId) {
        LiveRoom room = load(roomId);
        if (room.getStatus() != LiveRoom.RoomStatus.LIVING) throw new ApiException(40906, "room not living");
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(40401, "user not found"));
        LivekitToken token = tokenIssuer.issueSubscriber(room.getId(), userId);
        return new EnterResult(toView(room, anchorNickname(room.getAnchorId())), token,
                user.getNickname());
    }

    @Transactional(readOnly = true)
    public List<RoomView> liveList() {
        return roomRepository.findByStatusOrderByStartedAtDesc(LiveRoom.RoomStatus.LIVING).stream()
                .map(r -> toView(r, anchorNickname(r.getAnchorId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<RoomView> myRooms(String anchorId) {
        return roomRepository.findByAnchorIdOrderByCreatedAtDesc(anchorId).stream()
                .map(r -> toView(r, anchorNickname(anchorId))).toList();
    }

    @Transactional(readOnly = true)
    public RoomView detail(String roomId) {
        LiveRoom room = load(roomId);
        return toView(room, anchorNickname(room.getAnchorId()));
    }

    private LiveRoom load(String roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ApiException(40404, "room not found"));
    }

    private String anchorNickname(String anchorId) {
        return userRepository.findById(anchorId).map(User::getNickname).orElse("unknown");
    }

    private RoomView toView(LiveRoom r, String anchorNickname) {
        return new RoomView(r.getId(), r.getAnchorId(), anchorNickname, r.getTitle(), r.getCoverUrl(),
                r.getStatus().name(), r.getSessionSeq(),
                r.getStartedAt() == null ? null : r.getStartedAt().toString(),
                r.getEndedAt() == null ? null : r.getEndedAt().toString());
    }

    public record RoomView(String roomId, String anchorId, String anchorNickname, String title,
                           String coverUrl, String status, int sessionSeq,
                           String startedAt, String endedAt) {}

    public record StartResult(RoomView room, LivekitToken livekit, ObsPublish obs) {}
    public record EnterResult(RoomView room, LivekitToken livekit, String nickname) {}
}
