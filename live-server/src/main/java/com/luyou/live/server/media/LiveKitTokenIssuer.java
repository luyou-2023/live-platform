package com.luyou.live.server.media;

import com.luyou.live.server.common.ApiException;
import com.luyou.live.server.live.domain.LivekitToken;
import io.livekit.server.AccessToken;
import io.livekit.server.IngressServiceClient;
import livekit.LivekitIngress;

import java.util.List;
import java.util.UUID;
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
    private final IngressServiceClient ingressService;

    public LiveKitTokenIssuer(LivekitConfig config) {
        this.config = config;
        // livekit SDK http 客户端直连，不走系统代理
        String httpBase = config.url().replace("ws://", "http://").replace("wss://", "https://");
        this.roomService = RoomServiceClient.Companion.create(httpBase, config.apiKey(), config.apiSecret());
        this.ingressService = IngressServiceClient.Companion.create(httpBase, config.apiKey(), config.apiSecret());
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
        // 观众身份必须唯一：与主播同身份进房会互踢（观众刷新一次就把主播推流踢断）。
        // LiveKit 身份仅用于媒体面，业务身份走我们自有 WS，加随机后缀不影响任何业务逻辑。
        String subIdentity = identity + "-sub-" + UUID.randomUUID().toString().substring(0, 8);
        AccessToken token = baseToken(subIdentity);
        token.addGrants(new RoomJoin(true), new RoomName(roomName), new CanSubscribe(true));
        return toVo(roomName, subIdentity, token);
    }

    @Override
    public void closeRoom(String roomName) {
        try {
            roomService.deleteRoom(roomName).execute();
        } catch (Exception e) {
            // 房间可能已不存在，媒体面清理失败不阻断业务面
        }
    }

    @Override
    public ObsPublish ensureObsPublish(String roomName, ObsProtocol protocol) {
        String ingressName = protocol == ObsProtocol.WHIP ? "obs-" + roomName + "-whip" : "obs-" + roomName;
        try {
            List<LivekitIngress.IngressInfo> existing = null;
            try {
                existing = ingressService.listIngress(null, roomName).execute().body();
            } catch (Exception ignored) {
                // 查询失败不阻断创建
            }
            if (existing != null) {
                for (LivekitIngress.IngressInfo info : existing) {
                    if (ingressName.equals(info.getName()) && info.getStreamKey() != null
                            && !info.getStreamKey().isBlank()) {
                        return toObsPublish(protocol, info);
                    }
                }
            }
            LivekitIngress.IngressInput inputType = protocol == ObsProtocol.WHIP
                    ? LivekitIngress.IngressInput.WHIP_INPUT : LivekitIngress.IngressInput.RTMP_INPUT;
            LivekitIngress.IngressInfo created = ingressService
                    .createIngress(ingressName, roomName, ingressName, null, inputType)
                    .execute().body();
            if (created == null || created.getStreamKey() == null) {
                throw new ApiException(50201, "ingress create failed");
            }
            return toObsPublish(protocol, created);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(50201, "ingress unavailable: " + e.getMessage());
        }
    }

    /** 对外展示地址：RTMP 用固定配置；WHIP 用配置基础地址 + API 返回的路径 */
    private ObsPublish toObsPublish(ObsProtocol protocol, LivekitIngress.IngressInfo info) {
        String url;
        if (protocol == ObsProtocol.WHIP) {
            String path = "";
            try {
                String apiUrl = info.getUrl();
                if (apiUrl != null) {
                    int schemeEnd = apiUrl.indexOf("://");
                    int slash = apiUrl.indexOf('/', schemeEnd < 0 ? 0 : schemeEnd + 3);
                    path = slash >= 0 ? apiUrl.substring(slash) : "";
                }
            } catch (Exception ignored) {
                // 解析失败退化为无路径
            }
            url = config.whipPublicUrl().replaceAll("/+$", "") + path;
        } else {
            url = config.rtmpPublicUrl();
        }
        return new ObsPublish(protocol.name(), url, info.getStreamKey());
    }

    /** OBS 推流信息 */
    public record ObsPublish(String protocol, String url, String streamKey) {}

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
