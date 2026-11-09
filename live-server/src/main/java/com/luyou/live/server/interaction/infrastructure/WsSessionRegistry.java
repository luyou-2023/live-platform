package com.luyou.live.server.interaction.infrastructure;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WsSessionRegistry {

    private final Map<String, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    private final Map<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    public void register(String roomId, String userId, WebSocketSession session) {
        roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(session);
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(String roomId, String userId, WebSocketSession session) {
        Set<WebSocketSession> rs = roomSessions.get(roomId);
        if (rs != null) { rs.remove(session); if (rs.isEmpty()) roomSessions.remove(roomId, rs); }
        Set<WebSocketSession> us = userSessions.get(userId);
        if (us != null) { us.remove(session); if (us.isEmpty()) userSessions.remove(userId, us); }
    }

    /** 房间内广播；返回送达会话数 */
    public int broadcast(String roomId, String json) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null) return 0;
        int n = 0;
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) { sendQuietly(s, json); n++; }
        }
        return n;
    }

    /** 定向发送（如禁言通知） */
    public int sendToUser(String userId, String json) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null) return 0;
        int n = 0;
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) { sendQuietly(s, json); n++; }
        }
        return n;
    }

    private void sendQuietly(WebSocketSession s, String json) {
        try { s.sendMessage(new TextMessage(json)); }
        catch (IOException ignored) { }
    }
}
