package com.luyou.live.server.media;

import com.luyou.live.server.live.domain.LivekitToken;
import com.luyou.live.server.media.LiveKitTokenIssuer.ObsPublish;

public interface TokenIssuer {
    /** 主播发布 token */
    LivekitToken issuePublisher(String roomName, String identity);
    /** 观众订阅 token */
    LivekitToken issueSubscriber(String roomName, String identity);
    /** 关闭媒体房间 */
    void closeRoom(String roomName);
    /** 为房间确保 OBS 推流入口（幂等，按协议各建一个） */
    ObsPublish ensureObsPublish(String roomName, ObsProtocol protocol);
}
