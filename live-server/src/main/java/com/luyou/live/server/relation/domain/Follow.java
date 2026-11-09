package com.luyou.live.server.relation.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "lp_follow",
        uniqueConstraints = @UniqueConstraint(name = "uk_follow_pair", columnNames = {"followerId", "followeeId"}))
public class Follow {

    @Id
    private String id;

    @Column(nullable = false, length = 64)
    private String followerId;

    @Column(nullable = false, length = 64)
    private String followeeId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Follow() {}

    public static Follow of(String id, String followerId, String followeeId) {
        if (followerId.equals(followeeId)) throw new IllegalArgumentException("cannot follow self");
        Follow f = new Follow();
        f.id = id; f.followerId = followerId; f.followeeId = followeeId;
        return f;
    }

    public String getId() { return id; }
    public String getFollowerId() { return followerId; }
    public String getFolloweeId() { return followeeId; }
    public Instant getCreatedAt() { return createdAt; }
}
