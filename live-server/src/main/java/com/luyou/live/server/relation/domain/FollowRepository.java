package com.luyou.live.server.relation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FollowRepository extends JpaRepository<Follow, String> {
    Optional<Follow> findByFollowerIdAndFolloweeId(String followerId, String followeeId);
    long countByFolloweeId(String followeeId);
    long countByFollowerId(String followerId);
}
