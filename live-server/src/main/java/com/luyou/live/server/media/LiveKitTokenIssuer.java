package com.luyou.live.server.media;

import com.luyou.live.server.common.ApiException;
import com.luyou.live.server.live.domain.LivekitToken;
import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanSubscribe;
import io.livekit.server.RoomAdmin;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import io.livekit.server.RoomServiceClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class LiveKitTokenIssuer implements TokenIssuer {

    private final LivekitConfig config;
    private final RoomServiceClient roomService;

    public LiveKitTokenIssuer(LivekitConfig config) {
        this.config = config;
        // livekit SDK http 客户端直连，不走系统代理
        String httpBase = config.url().replace("ws://", "http://").replace("wss://", "https://");
        this.roomService = RoomServiceClient.Companion.create(httpBase, config.apiKey(), config.apiSecret());
    }

    @Override
    public LivekitToken issuePublisher(String roomName, String identity) {
        AccessToken token = baseToken(identity);
        token.addGrants(new RoomJoin(true), new RoomName(roomName),
                new CanPublish(true), new CanSubscribe(true), new RoomAdmin(true));
        return toVo(roomName, identity, token);
    }

    @Override
    public LivekitToken issueSubscriber(String roomName, String identity) {
        AccessToken token = baseToken(identity);
        token.addGrants(new RoomJoin(true), new RoomName(roomName), new CanSubscribe(true));
        return toVo(roomName, identity, token);
    }

    @Override
    public void closeRoom(String roomName) {
        try {
            roomService.deleteRoom(roomName).execute();
        } catch (Exception e) {
            // 房间可能已不存在，媒体面清理失败不阻断业务面
        }
    }

    private AccessToken baseToken(String identity) {
        AccessToken token = new AccessToken(config.apiKey(), config.apiSecret());
        token.setName(identity);
        token.setIdentity(identity);
        return token;
    }

    private LivekitToken toVo(String roomName, String identity, AccessToken token) {
        try {
            return new LivekitToken(config.url(), roomName, identity, token.toJwt(),
                    Instant.now().plus(2, ChronoUnit.HOURS));
        } catch (Exception e) {
            throw new ApiException(50200, "livekit token issue failed");
        }
    }
}
