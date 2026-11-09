package com.luyou.live.server.interaction.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MuteRepository extends JpaRepository<Mute, String> {
    java.util.Optional<Mute> findByRoomIdAndUserId(String roomId, String userId);
}
