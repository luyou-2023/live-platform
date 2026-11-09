package com.luyou.live.server.interaction.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, String> {

    /** 本场最近 N 条未屏蔽评论（时间正序） */
    List<Comment> findTop50ByRoomIdAndSessionSeqAndBlockedFalseOrderBySentAtDesc(
            String roomId, int sessionSeq);
}
