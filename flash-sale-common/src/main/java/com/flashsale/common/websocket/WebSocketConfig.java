package com.flashsale.common.websocket;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置 — 共享于 seckill-service 和 order-service
 *
 * 注册 /ws/seckill 端点，由 SeckillWebSocketHandler 处理
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(seckillWebSocketHandler(), "/ws/seckill")
                .setAllowedOrigins("*");
    }

    @Bean
    public SeckillWebSocketHandler seckillWebSocketHandler() {
        return new SeckillWebSocketHandler();
    }
}
