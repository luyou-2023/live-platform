package com.luyou.live.server.media;

/** OBS 推流协议 */
public enum ObsProtocol {
    /** RTMP（OBS 自定义服务，兼容性最好） */
    RTMP,
    /** WHIP（OBS 30+ 的 WebRTC 推流，延迟更低） */
    WHIP;

    public static ObsProtocol parse(String raw) {
        if (raw == null || raw.isBlank()) return RTMP;
        return ObsProtocol.valueOf(raw.trim().toUpperCase());
    }
}
