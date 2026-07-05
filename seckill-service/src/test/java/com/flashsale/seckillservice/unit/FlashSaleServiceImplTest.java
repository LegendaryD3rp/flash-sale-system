package com.flashsale.seckillservice.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.seckillservice.service.impl.FlashSaleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FlashSaleServiceImplTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private SetOperations<String, String> setOps;
    private RabbitTemplate rabbitTemplate;
    private ObjectMapper objectMapper;
    private FlashSaleServiceImpl flashSaleService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        setOps = mock(SetOperations.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        objectMapper = new ObjectMapper();

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(valueOps.get("seckill:activity:1"))
                .thenReturn("{\"id\":1,\"status\":\"ACTIVE\"}");
        // User has not bought before
        when(setOps.isMember("seckill:bought:1", "1")).thenReturn(false);

        flashSaleService = new FlashSaleServiceImpl(redisTemplate, rabbitTemplate, objectMapper);
    }

    @Test
    void flash_ShouldReturnQueued_WhenStockAvailable() {
        when(valueOps.decrement("seckill:stock:1")).thenReturn(9L);

        String result = flashSaleService.flash(1L, 1L);
        assertThat(result).contains("排队");
        verify(rabbitTemplate).convertAndSend(nullable(String.class), nullable(String.class), (Object) any());
    }

    @Test
    void flash_ShouldFail_WhenStockExhausted() {
        when(valueOps.decrement("seckill:stock:1")).thenReturn(-1L);

        assertThatThrownBy(() -> flashSaleService.flash(1L, 1L))
                .isInstanceOf(com.flashsale.common.exception.BusinessException.class)
                .hasMessageContaining("库存不足");
        verify(rabbitTemplate, never()).convertAndSend(nullable(String.class), nullable(String.class), (Object) any());
    }

    @Test
    void flash_ShouldThrow_WhenNotWarmedUp() {
        StringRedisTemplate emptyRedis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> emptyOps = mock(ValueOperations.class);
        when(emptyRedis.opsForValue()).thenReturn(emptyOps);
        when(emptyOps.get("seckill:activity:1")).thenReturn(null);

        FlashSaleServiceImpl svc = new FlashSaleServiceImpl(emptyRedis, rabbitTemplate, objectMapper);

        assertThatThrownBy(() -> svc.flash(1L, 1L))
                .isInstanceOf(com.flashsale.common.exception.BusinessException.class)
                .hasMessageContaining("预热");
    }

    /** ⭐ P0-③ 同一用户重复抢购 → 409 幂等拒绝 */
    @Test
    void flash_ShouldThrow_WhenDuplicatePurchase() {
        when(setOps.isMember("seckill:bought:1", "1")).thenReturn(true);

        assertThatThrownBy(() -> flashSaleService.flash(1L, 1L))
                .isInstanceOf(com.flashsale.common.exception.BusinessException.class)
                .hasMessageContaining("重复");
        verify(valueOps, never()).decrement(anyString());
    }
}
