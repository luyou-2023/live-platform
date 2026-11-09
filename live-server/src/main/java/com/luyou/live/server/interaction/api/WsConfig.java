package com.luyou.live.server.interaction.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WsConfig implements WebSocketConfigurer {

    private final InteractionWsEndpoint endpoint;

    public WsConfig(InteractionWsEndpoint endpoint) { this.endpoint = endpoint; }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(endpoint, "/ws/v1/rooms/{roomId}")
                .setAllowedOrigins("*");
    }
}
