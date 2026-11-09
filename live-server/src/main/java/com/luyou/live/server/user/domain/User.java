package com.luyou.live.server.user.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "lp_user", indexes = @Index(name = "uk_username", columnList = "username", unique = true))
public class User {

    @Id
    private String id;

    @Column(nullable = false, length = 64)
    private String username;

    @Column(nullable = false, length = 128)
    private String passwordHash;

    @Column(nullable = false, length = 64)
    private String nickname;

    @Column(length = 512)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected User() {}

    public static User register(String id, String username, String passwordHash, String nickname) {
        User u = new User();
        u.id = id;
        u.username = username;
        u.passwordHash = passwordHash;
        u.nickname = nickname == null || nickname.isBlank() ? username : nickname;
        return u;
    }

    public enum UserStatus { ACTIVE, DISABLED }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getNickname() { return nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public UserStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
