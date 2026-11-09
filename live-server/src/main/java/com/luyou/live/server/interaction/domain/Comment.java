package com.luyou.live.server.interaction.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "lp_comment", indexes = @Index(name = "idx_comment_room", columnList = "roomId, sentAt"))
public class Comment {

    @Id
    private String id;

    @Column(nullable = false, length = 64)
    private String roomId;

    @Column(nullable = false)
    private int sessionSeq;

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 64)
    private String nickname;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant sentAt = Instant.now();

    @Column(nullable = false)
    private boolean pinned = false;

    @Column(nullable = false)
    private boolean blocked = false;

    protected Comment() {}

    public static Comment post(String id, String roomId, int sessionSeq, String userId,
                               String nickname, String content) {
        Comment c = new Comment();
        c.id = id; c.roomId = roomId; c.sessionSeq = sessionSeq;
        c.userId = userId; c.nickname = nickname; c.content = content;
        return c;
    }

    public String getId() { return id; }
    public String getRoomId() { return roomId; }
    public int getSessionSeq() { return sessionSeq; }
    public String getUserId() { return userId; }
    public String getNickname() { return nickname; }
    public String getContent() { return content; }
    public Instant getSentAt() { return sentAt; }
    public boolean isPinned() { return pinned; }
    public boolean isBlocked() { return blocked; }
}
