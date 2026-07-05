package com.flashsale.seckillservice.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 秒杀结果 WebSocket Handler
 *
 * 用户连接 WebSocket 后，将 session 注册到 userId 映射中。
 * 秒杀结果通过 MQ 消费者回调 pushToUser() 推送给指定用户。
 *
 * 连接方式：
 *   ws://localhost:8085/ws/seckill?userId={userId}&token={jwt}
 */
public class SeckillWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SeckillWebSocketHandler.class);

    /** userId → WebSocketSession 映射 */
    private static final Map<Long, WebSocketSession> USER_SESSION_MAP = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String query = session.getUri().getQuery();
        Long userId = extractUserId(query);
        if (userId != null) {
            USER_SESSION_MAP.put(userId, session);
            log.info("WebSocket connected — userId={}, sessionId={}", userId, session.getId());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Ping/pong or future commands
        log.debug("WebSocket message from {}: {}", session.getId(), message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        USER_SESSION_MAP.entrySet().removeIf(entry -> entry.getValue().getId().equals(session.getId()));
        log.info("WebSocket disconnected — sessionId={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket transport error — sessionId={}", session.getId(), exception);
        USER_SESSION_MAP.entrySet().removeIf(entry -> entry.getValue().getId().equals(session.getId()));
    }

    /**
     * 主动推送消息给指定用户
     */
    public static void pushToUser(Long userId, String messageJson) {
        WebSocketSession session = USER_SESSION_MAP.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(messageJson));
                log.debug("WebSocket pushed to userId={}: {}", userId, messageJson);
            } catch (IOException e) {
                log.error("WebSocket send failed — userId={}", userId, e);
            }
        } else {
            log.warn("WebSocket session not found for userId={}", userId);
        }
    }

    private Long extractUserId(String query) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if ("userId".equals(kv[0]) && kv.length > 1) {
                try {
                    return Long.parseLong(kv[1]);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
