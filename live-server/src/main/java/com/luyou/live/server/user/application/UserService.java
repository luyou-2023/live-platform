package com.luyou.live.server.user.application;

import com.luyou.live.server.common.ApiException;
import com.luyou.live.server.common.JwtService;
import com.luyou.live.server.user.domain.User;
import com.luyou.live.server.user.domain.UserEvents.UserRegistered;
import com.luyou.live.server.user.domain.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final ApplicationEventPublisher eventPublisher;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public UserService(UserRepository userRepository, JwtService jwtService,
                       ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public LoginResult register(String username, String password, String nickname) {
        userRepository.findByUsername(username).ifPresent(u -> {
            throw new ApiException(40901, "username already taken");
        });
        User user = User.register(UUID.randomUUID().toString(), username,
                encoder.encode(password), nickname);
        userRepository.save(user);
        eventPublisher.publishEvent(UserRegistered.of(user.getId(), user.getNickname()));
        return new LoginResult(jwtService.issue(user.getId()), toProfile(user));
    }

    @Transactional(readOnly = true)
    public LoginResult login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(40401, "user not found"));
        if (user.getStatus() != User.UserStatus.ACTIVE) throw new ApiException(40301, "user disabled");
        if (!encoder.matches(password, user.getPasswordHash())) throw new ApiException(40102, "wrong password");
        return new LoginResult(jwtService.issue(user.getId()), toProfile(user));
    }

    @Transactional(readOnly = true)
    public UserProfile me(String userId) {
        return userRepository.findById(userId).map(this::toProfile)
                .orElseThrow(() -> new ApiException(40401, "user not found"));
    }

    private UserProfile toProfile(User u) {
        return new UserProfile(u.getId(), u.getUsername(), u.getNickname(), u.getAvatarUrl());
    }

    public record LoginResult(String token, UserProfile user) {}
    public record UserProfile(String userId, String username, String nickname, String avatarUrl) {}
}
