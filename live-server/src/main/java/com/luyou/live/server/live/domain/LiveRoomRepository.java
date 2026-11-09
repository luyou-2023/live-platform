package com.luyou.live.server.live.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LiveRoomRepository extends JpaRepository<LiveRoom, String> {
    List<LiveRoom> findByStatusOrderByStartedAtDesc(LiveRoom.RoomStatus status);
    List<LiveRoom> findByAnchorIdOrderByCreatedAtDesc(String anchorId);
    long countByAnchorIdAndStatus(String anchorId, LiveRoom.RoomStatus status);
}
