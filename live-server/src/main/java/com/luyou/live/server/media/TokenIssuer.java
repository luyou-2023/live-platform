package com.luyou.live.server.media;

import com.luyou.live.server.live.domain.LivekitToken;

public interface TokenIssuer {
    /** 主播发布 token */
    LivekitToken issuePublisher(String roomName, String identity);
    /** 观众订阅 token */
    LivekitToken issueSubscriber(String roomName, String identity);
    /** 关闭媒体房间 */
    void closeRoom(String roomName);
}
