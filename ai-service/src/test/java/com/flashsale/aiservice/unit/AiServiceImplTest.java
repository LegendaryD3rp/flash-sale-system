package com.flashsale.aiservice.unit;

import com.flashsale.aiservice.service.impl.AiServiceImpl;
import com.flashsale.aiservice.vo.ChatReq;
import com.flashsale.aiservice.vo.ChatResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiServiceImplTest {

    private AiServiceImpl aiService;
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        aiService = new AiServiceImpl();
        restTemplate = mock(RestTemplate.class);
        ReflectionTestUtils.setField(aiService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(aiService, "apiKey", "test-key");
        ReflectionTestUtils.setField(aiService, "baseUrl", "https://api.deepseek.com/v1");
        ReflectionTestUtils.setField(aiService, "model", "deepseek-chat");
        ReflectionTestUtils.setField(aiService, "maxTokens", 2048);
        ReflectionTestUtils.setField(aiService, "temperature", 0.7);
    }

    @Test
    void chat_ShouldReturnReply_WhenApiSucceeds() {
        Map<String, Object> choiceBody = Map.of(
                "choices", List.of(
                        Map.of("message", Map.of("content", "你好！我是AI助手。"))
                )
        );
        when(restTemplate.exchange(
                anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(choiceBody, HttpStatus.OK));

        ChatReq req = new ChatReq();
        req.setMessage("你好");
        ChatResp resp = aiService.chat(req);
        assertThat(resp.getReply()).isEqualTo("你好！我是AI助手。");
    }

    @Test
    void chat_ShouldReturnFallback_WhenApiFails() {
        when(restTemplate.exchange(
                anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        ChatReq req = new ChatReq();
        req.setMessage("你好");
        ChatResp resp = aiService.chat(req);
        assertThat(resp.getReply()).contains("抱歉");
    }

    @Test
    void chat_ShouldHandleNullMessage() {
        Map<String, Object> choiceBody = Map.of(
                "choices", List.of(
                        Map.of("message", Map.of("content", "你好"))
                )
        );
        when(restTemplate.exchange(
                anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(choiceBody, HttpStatus.OK));

        ChatReq req = new ChatReq();
        req.setMessage(null);
        ChatResp resp = aiService.chat(req);
        assertThat(resp.getReply()).isEqualTo("你好");
    }

    @Test
    void chat_ShouldHandleEmptyHistory() {
        Map<String, Object> choiceBody = Map.of(
                "choices", List.of(
                        Map.of("message", Map.of("content", "回复"))
                )
        );
        when(restTemplate.exchange(
                anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(choiceBody, HttpStatus.OK));

        ChatReq req = new ChatReq();
        req.setMessage("测试");
        req.setHistory(null);
        ChatResp resp = aiService.chat(req);
        assertThat(resp.getReply()).isEqualTo("回复");
    }

    @Test
    void chat_ShouldHandleNonOkStatus() {
        when(restTemplate.exchange(
                anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR));

        ChatReq req = new ChatReq();
        req.setMessage("ping");
        ChatResp resp = aiService.chat(req);
        assertThat(resp.getReply()).contains("抱歉");
    }
}
