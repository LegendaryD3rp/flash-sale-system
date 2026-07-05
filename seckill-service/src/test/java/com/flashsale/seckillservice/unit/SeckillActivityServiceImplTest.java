package com.flashsale.seckillservice.unit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.seckillservice.entity.SeckillActivity;
import com.flashsale.seckillservice.mapper.SeckillActivityMapper;
import com.flashsale.seckillservice.service.impl.SeckillActivityServiceImpl;
import com.flashsale.seckillservice.vo.SeckillActivityCreateReq;
import com.flashsale.seckillservice.vo.SeckillActivityVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SeckillActivityServiceImplTest {

    private SeckillActivityMapper activityMapper;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private ObjectMapper objectMapper;
    private JdbcTemplate jdbcTemplate;
    private SeckillActivityServiceImpl seckillActivityService;

    @BeforeEach
    void setUp() {
        activityMapper = mock(SeckillActivityMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        objectMapper = new ObjectMapper();
        jdbcTemplate = mock(JdbcTemplate.class);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        seckillActivityService = new SeckillActivityServiceImpl(
                activityMapper, redisTemplate, objectMapper, jdbcTemplate);
    }

    @Test
    void listActivities_ShouldReturnPage_WhenAdmin() {
        SeckillActivity act = new SeckillActivity();
        act.setId(1L);
        when(activityMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(inv -> {
                    Page<SeckillActivity> pg = new Page<>(1, 10, 1);
                    pg.setRecords(List.of(act));
                    return pg;
                });

        IPage<SeckillActivityVO> result = seckillActivityService.listActivities(1, 10, true);
        assertThat(result.getRecords()).isNotEmpty();
    }

    @Test
    void getActivityById_ShouldThrow_WhenNotExist() {
        when(valueOps.get("seckill:activity:999")).thenReturn("null");
        when(activityMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> seckillActivityService.getActivityById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    void getActivityById_ShouldReturn_WhenDBHit() throws Exception {
        when(valueOps.get("seckill:activity:1")).thenReturn(null);

        SeckillActivity act = new SeckillActivity();
        act.setId(1L);
        act.setProductId(10L);
        act.setSeckillPrice(9900L);
        act.setTotalStock(100);
        act.setStartTime(LocalDateTime.now().plusHours(1));
        act.setEndTime(LocalDateTime.now().plusDays(1));
        act.setStatus("ACTIVE");
        when(activityMapper.selectById(1L)).thenReturn(act);

        SeckillActivityVO vo = seckillActivityService.getActivityById(1L);
        assertThat(vo.getProductId()).isEqualTo(10L);
    }

    @Test
    void createActivity_ShouldReturnId() {
        SeckillActivityCreateReq req = new SeckillActivityCreateReq();
        req.setProductId(10L);
        req.setSeckillPrice(9900L);
        req.setTotalStock(100);
        req.setStartTime(LocalDateTime.now().plusHours(1));
        req.setEndTime(LocalDateTime.now().plusDays(7));

        when(activityMapper.insert(any(SeckillActivity.class))).thenAnswer(inv -> {
            inv.getArgument(0, SeckillActivity.class).setId(55L);
            return 1;
        });

        Long id = seckillActivityService.createActivity(req);
        assertThat(id).isEqualTo(55L);
    }

    @Test
    void updateActivity_ShouldThrow_WhenNotDraft() {
        SeckillActivity act = new SeckillActivity();
        act.setId(1L);
        act.setStatus("ACTIVE");
        when(activityMapper.selectById(1L)).thenReturn(act);

        assertThatThrownBy(() -> seckillActivityService.updateActivity(1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("草稿");
    }

    @Test
    void changeStatus_ShouldThrow_WhenNotFound() {
        when(activityMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> seckillActivityService.changeStatus(999L, "PENDING"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    void changeStatus_ShouldThrow_OnInvalidTransition() {
        SeckillActivity act = new SeckillActivity();
        act.setId(1L);
        act.setStatus("ACTIVE");
        when(activityMapper.selectById(1L)).thenReturn(act);

        assertThatThrownBy(() -> seckillActivityService.changeStatus(1L, "PENDING"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅草稿");
    }

    @Test
    void warmUp_ShouldThrow_WhenNotPending() {
        SeckillActivity act = new SeckillActivity();
        act.setId(1L);
        act.setStatus("DRAFT");
        when(activityMapper.selectById(1L)).thenReturn(act);

        assertThatThrownBy(() -> seckillActivityService.warmUp(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅预热中");
    }

    @Test
    void warmUp_ShouldSetStock_WhenNotExists() {
        SeckillActivity act = new SeckillActivity();
        act.setId(1L);
        act.setTotalStock(100);
        act.setStatus("PENDING");
        act.setStartTime(LocalDateTime.now().plusHours(1));
        act.setEndTime(LocalDateTime.now().plusDays(1));
        when(activityMapper.selectById(1L)).thenReturn(act);
        when(redisTemplate.opsForValue().setIfAbsent("seckill:stock:1", "100")).thenReturn(Boolean.TRUE);

        seckillActivityService.warmUp(1L);
        verify(activityMapper, times(2)).updateById((SeckillActivity) any());
    }

    /** 期望状态码/业务码：创建活动成功 */
    @Test
    void createActivity_ShouldSucceed_WithValidData() {
        SeckillActivityCreateReq req = new SeckillActivityCreateReq();
        req.setProductId(10L);
        req.setSeckillPrice(9900L);
        req.setTotalStock(100);
        req.setStartTime(LocalDateTime.now().plusHours(1));
        req.setEndTime(LocalDateTime.now().plusDays(7));

        when(activityMapper.insert(any(SeckillActivity.class))).thenAnswer(inv -> {
            inv.getArgument(0, SeckillActivity.class).setId(66L);
            return 1;
        });

        Long id = seckillActivityService.createActivity(req);
        assertThat(id).isEqualTo(66L);
    }

    @Test
    void listActivities_ShouldReturn_WhenNonAdmin() {
        SeckillActivity act = new SeckillActivity();
        act.setId(1L);
        when(activityMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenAnswer(inv -> {
                    Page<SeckillActivity> pg = new Page<>(1, 10, 1);
                    pg.setRecords(List.of(act));
                    return pg;
                });

        IPage<SeckillActivityVO> result = seckillActivityService.listActivities(1, 10, false);
        assertThat(result.getRecords()).isNotEmpty();
    }
}
