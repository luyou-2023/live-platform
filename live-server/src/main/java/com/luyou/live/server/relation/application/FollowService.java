package com.luyou.live.server.relation.application;

import com.luyou.live.server.common.ApiException;
import com.luyou.live.server.relation.domain.Follow;
import com.luyou.live.server.relation.domain.FollowEvents.UserFollowed;
import com.luyou.live.server.relation.domain.FollowEvents.UserUnfollowed;
import com.luyou.live.server.relation.domain.FollowRepository;
import com.luyou.live.server.user.domain.User;
import com.luyou.live.server.user.domain.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redis;
    private final ApplicationEventPublisher eventPublisher;

    public FollowService(FollowRepository followRepository, UserRepository userRepository,
                         StringRedisTemplate redis, ApplicationEventPublisher eventPublisher) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.redis = redis;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void follow(String followerId, String followeeId) {
        if (followerId.equals(followeeId)) throw new ApiException(40002, "cannot follow self");
        User target = userRepository.findById(followeeId)
                .orElseThrow(() -> new ApiException(40401, "user not found"));
        if (target.getStatus() != User.UserStatus.ACTIVE) throw new ApiException(40301, "user disabled");
        followRepository.findByFollowerIdAndFolloweeId(followerId, followeeId).ifPresent(f -> {
            throw new ApiException(40902, "already followed");
        });
        followRepository.save(Follow.of(UUID.randomUUID().toString(), followerId, followeeId));
        redis.opsForValue().increment("rm:follower_count:" + followeeId);
        redis.opsForValue().increment("rm:following_count:" + followerId);
        eventPublisher.publishEvent(UserFollowed.of(followerId, followeeId));
    }

    @Transactional
    public void unfollow(String followerId, String followeeId) {
        Follow f = followRepository.findByFollowerIdAndFolloweeId(followerId, followeeId)
                .orElseThrow(() -> new ApiException(40402, "not followed"));
        followRepository.delete(f);
        redis.opsForValue().decrement("rm:follower_count:" + followeeId);
        redis.opsForValue().decrement("rm:following_count:" + followerId);
        eventPublisher.publishEvent(UserUnfollowed.of(followerId, followeeId));
    }

    @Transactional(readOnly = true)
    public Counts counts(String userId, String viewerId) {
        long followers = longOrZero(redis.opsForValue().get("rm:follower_count:" + userId));
        long following = longOrZero(redis.opsForValue().get("rm:following_count:" + userId));
        boolean followed = viewerId != null
                && followRepository.findByFollowerIdAndFolloweeId(viewerId, userId).isPresent();
        return new Counts(followers, following, followed);
    }

    private long longOrZero(String v) { return v == null ? 0 : Long.parseLong(v); }

    public record Counts(long followers, long following, boolean followed) {}
}
