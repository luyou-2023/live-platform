package com.luyou.live.server.live.domain;

import com.luyou.live.server.common.ApiException;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "lp_live_room", indexes = {
        @Index(name = "idx_room_status", columnList = "status"),
        @Index(name = "idx_room_anchor", columnList = "anchorId")})
public class LiveRoom {

    @Id
    private String id;

    @Column(nullable = false, length = 64)
    private String anchorId;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(length = 512)
    private String coverUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RoomStatus status = RoomStatus.CREATED;

    @Column(nullable = false)
    private int sessionSeq = 0;

    private Instant startedAt;
    private Instant endedAt;

    @Version
    private long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected LiveRoom() {}

    public static LiveRoom create(String id, String anchorId, String title, String coverUrl) {
        LiveRoom r = new LiveRoom();
        r.id = id; r.anchorId = anchorId;
        r.title = title; r.coverUrl = coverUrl;
        return r;
    }

    public void assertAnchor(String operatorId) {
        if (!anchorId.equals(operatorId)) throw new ApiException(40303, "only anchor can operate");
    }

    public void start() {
        if (status != RoomStatus.CREATED) throw new ApiException(40903, "room not in creatable state");
        status = RoomStatus.LIVING;
        sessionSeq += 1;
        startedAt = Instant.now();
    }

    public void stop() {
        if (status != RoomStatus.LIVING) throw new ApiException(40904, "room not living");
        status = RoomStatus.ENDED;
        endedAt = Instant.now();
    }

    public enum RoomStatus { CREATED, LIVING, ENDED }

    public String getId() { return id; }
    public String getAnchorId() { return anchorId; }
    public String getTitle() { return title; }
    public String getCoverUrl() { return coverUrl; }
    public RoomStatus getStatus() { return status; }
    public int getSessionSeq() { return sessionSeq; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
