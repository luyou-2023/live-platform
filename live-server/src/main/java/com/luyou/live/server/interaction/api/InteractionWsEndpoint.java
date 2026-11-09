package com.luyou.live.server.interaction.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luyou.live.server.common.JwtService;
import com.luyou.live.server.interaction.application.InteractionService;
import com.luyou.live.server.interaction.infrastructure.WsSessionRegistry;
import com.luyou.live.server.live.domain.LiveRoom;
import com.luyou.live.server.live.domain.LiveRoomRepository;
import com.luyou.live.server.user.domain.User;
import com.luyou.live.server.user.domain.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class InteractionWsEndpoint extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final JwtService jwtService;
    private final InteractionService interactionService;
    private final WsSessionRegistry registry;
    private final LiveRoomRepository roomRepository;
    private final UserRepository userRepository;

    public InteractionWsEndpoint(ObjectMapper objectMapper, JwtService jwtService,
                                 InteractionService interactionService, WsSessionRegistry registry,
                                 LiveRoomRepository roomRepository, UserRepository userRepository) {
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
        this.interactionService = interactionService;
        this.registry = registry;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = root.path("type").asText("");
        JsonNode payload = root.path("payload");
        try {
            switch (type) {
                case "auth" -> handleAuth(session, payload.path("token").asText(""));
                case "comment.send" -> {
                    requireAuthed(session);
                    interactionService.postComment(roomId(session), uid(session),
                            nickname(session), payload.path("content").asText(""));
                }
                case "like.send" -> {
                    requireAuthed(session);
                    interactionService.like(roomId(session));
                }
                default -> send(session, "error", Map.of("message", "unknown type"));
            }
        } catch (RuntimeException e) {
            send(session, "error", Map.of("message", e.getMessage()));
        }
    }

    private void handleAuth(WebSocketSession session, String token) throws Exception {
        String uid = token == null || token.isBlank() ? null : jwtService.verify(token);
        if (uid == null) {
            send(session, "auth.fail", Map.of("message", "invalid token"));
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        String roomId = roomId(session);
        LiveRoom room = roomRepository.findById(roomId).orElse(null);
        if (room == null || room.getStatus() != LiveRoom.RoomStatus.LIVING) {
            send(session, "auth.fail", Map.of("message", "room not living"));
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        String nickname = userRepository.findById(uid).map(User::getNickname).orElse(uid);
        session.getAttributes().put("uid", uid);
        session.getAttributes().put("nickname", nickname);
        registry.register(roomId, uid, session);
        interactionService.join(roomId, uid);
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("roomId", roomId);
        ok.put("nickname", nickname);
        ok.put("online", interactionService.onlineCount(roomId));
        send(session, "auth.ok", ok);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        if (session.getAttributes().get("uid") != null) {
            registry.unregister(roomId(session), uid(session), session);
            interactionService.leave(roomId(session), uid(session));
        }
    }

    private void requireAuthed(WebSocketSession session) {
        if (session.getAttributes().get("uid") == null) {
            throw new IllegalStateException("not authed, send auth first");
        }
    }

    private String roomId(WebSocketSession session) {
        Object cached = session.getAttributes().get("roomId");
        if (cached != null) return cached.toString();
        String path = session.getUri() == null ? "" : session.getUri().getPath();
        // /ws/v1/rooms/{roomId}
        int idx = path.indexOf("/ws/v1/rooms/");
        if (idx < 0) return "";
        String roomId = path.substring(idx + "/ws/v1/rooms/".length());
        session.getAttributes().put("roomId", roomId);
        return roomId;
    }

    private String uid(WebSocketSession session) { return session.getAttributes().get("uid").toString(); }
    private String nickname(WebSocketSession session) { return session.getAttributes().get("nickname").toString(); }

    private void send(WebSocketSession session, String type, Map<String, Object> payload) throws Exception {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", type);
        frame.put("payload", payload);
        registry.sendToSession(session, objectMapper.writeValueAsString(frame));
    }
}
