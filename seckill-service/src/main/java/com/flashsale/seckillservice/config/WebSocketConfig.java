package com.flashsale.seckillservice.config;

import com.flashsale.seckillservice.websocket.SeckillWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

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
