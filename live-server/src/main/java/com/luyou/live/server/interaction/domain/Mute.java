package com.luyou.live.server.interaction.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "lp_mute",
        uniqueConstraints = @UniqueConstraint(name = "uk_mute_pair", columnNames = {"roomId", "userId"}))
public class Mute {

    @Id
    private String id;

    @Column(nullable = false, length = 64)
    private String roomId;

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(nullable = false)
    private Instant until;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Mute() {}

    public static Mute of(String id, String roomId, String userId, Instant until) {
        Mute m = new Mute();
        m.id = id; m.roomId = roomId; m.userId = userId; m.until = until;
        return m;
    }

    public String getId() { return id; }
    public String getRoomId() { return roomId; }
    public String getUserId() { return userId; }
    public Instant getUntil() { return until; }
}
