package com.flashsale.seckillservice.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.common.constant.ErrorCode;
import com.flashsale.common.exception.BusinessException;
import com.flashsale.seckillservice.entity.SeckillActivity;
import com.flashsale.seckillservice.mapper.SeckillActivityMapper;
import com.flashsale.seckillservice.service.SeckillActivityService;
import com.flashsale.seckillservice.vo.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

@Service
public class SeckillActivityServiceImpl implements SeckillActivityService {

    private final SeckillActivityMapper activityMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public SeckillActivityServiceImpl(SeckillActivityMapper activityMapper,
                                      StringRedisTemplate redisTemplate,
                                      ObjectMapper objectMapper,
                                      JdbcTemplate jdbcTemplate) {
        this.activityMapper = activityMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public IPage<SeckillActivityVO> listActivities(int page, int size, boolean isAdmin) {
        LambdaQueryWrapper<SeckillActivity> wrapper = new LambdaQueryWrapper<>();

        if (!isAdmin) {
            // 用户只看 ACTIVE 状态的活动
            wrapper.eq(SeckillActivity::getStatus, "ACTIVE")
                   .le(SeckillActivity::getStartTime, LocalDateTime.now())
                   .ge(SeckillActivity::getEndTime, LocalDateTime.now());
        }

        wrapper.orderByDesc(SeckillActivity::getCreatedAt);

        Page<SeckillActivity> activityPage =
                activityMapper.selectPage(new Page<>(page, size), wrapper);

        return activityPage.convert(this::toVO);
    }

    @Override
    public SeckillActivityVO getActivityById(Long id) {
        // Try Redis cache first
        String cacheKey = "seckill:activity:" + id;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cached) && !"null".equals(cached)) {
            try {
                SeckillActivityVO cachedVO = objectMapper.readValue(cached, SeckillActivityVO.class);
                // Fill real-time remaining stock from Redis
                String stockStr = redisTemplate.opsForValue().get("seckill:stock:" + id);
                if (stockStr != null) {
                    int realtimeStock = Integer.parseInt(stockStr);
                    cachedVO.setRemainingStock(realtimeStock);
                    cachedVO.setAvailableStock(realtimeStock);
                }
                return cachedVO;
            } catch (JsonProcessingException ignored) {}
        }

        // Cache miss — query DB
        SeckillActivity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "秒杀活动不存在");
        }

        SeckillActivityVO vo = toVO(activity);

        // Fill remaining stock from Redis
        String stockStr = redisTemplate.opsForValue().get("seckill:stock:" + id);
        if (stockStr != null) {
            int realtimeStock = Integer.parseInt(stockStr);
            vo.setRemainingStock(realtimeStock);
            vo.setAvailableStock(realtimeStock);
        }

        // Cache the VO (without remaining stock — that's real-time)
        try {
            SeckillActivityVO cacheClone = new SeckillActivityVO();
            cacheClone.setId(vo.getId());
            cacheClone.setProductId(vo.getProductId());
            cacheClone.setProductName(vo.getProductName());
            cacheClone.setProductImageUrl(vo.getProductImageUrl());
            cacheClone.setOriginalPrice(vo.getOriginalPrice());
            cacheClone.setSeckillPrice(vo.getSeckillPrice());
            cacheClone.setTotalStock(vo.getTotalStock());
            cacheClone.setAvailableStock(vo.getAvailableStock());
            cacheClone.setStartTime(vo.getStartTime());
            cacheClone.setEndTime(vo.getEndTime());
            cacheClone.setStatus(vo.getStatus());
            cacheClone.setCreatedAt(vo.getCreatedAt());
            redisTemplate.opsForValue().set(cacheKey,
                    objectMapper.writeValueAsString(cacheClone),
                    5, TimeUnit.MINUTES);
        } catch (JsonProcessingException ignored) {}

        return vo;
    }

    @Override
    @Transactional
    public Long createActivity(SeckillActivityCreateReq req) {
        // Validate time range
        if (!req.getEndTime().isAfter(req.getStartTime())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束时间必须在开始时间之后");
        }

        SeckillActivity activity = new SeckillActivity();
        activity.setProductId(req.getProductId());
        activity.setSeckillPrice(req.getSeckillPrice());
        activity.setTotalStock(req.getTotalStock());
        activity.setAvailableStock(req.getTotalStock()); // initially equals total
        activity.setStartTime(req.getStartTime());
        activity.setEndTime(req.getEndTime());
        activity.setStatus("DRAFT");

        activityMapper.insert(activity);
        return activity.getId();
    }

    @Override
    @Transactional
    public Long updateActivity(Long id, SeckillActivityUpdateReq req) {
        SeckillActivity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "秒杀活动不存在");
        }

        // Only DRAFT activities can be edited
        if (!"DRAFT".equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅草稿状态的活动可编辑");
        }

        if (req.getSeckillPrice() != null) {
            activity.setSeckillPrice(req.getSeckillPrice());
        }
        if (req.getTotalStock() != null) {
            activity.setTotalStock(req.getTotalStock());
            activity.setAvailableStock(req.getTotalStock());
        }
        if (req.getStartTime() != null) {
            activity.setStartTime(req.getStartTime());
        }
        if (req.getEndTime() != null) {
            activity.setEndTime(req.getEndTime());
        }

        activityMapper.updateById(activity);
        return activity.getId();
    }

    @Override
    @Transactional
    public void changeStatus(Long id, String newStatus) {
        SeckillActivity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "秒杀活动不存在");
        }

        String oldStatus = activity.getStatus();

        // Validate status transitions
        switch (newStatus) {
            case "PENDING":
                // DRAFT → PENDING (上架/待预热)
                if (!"DRAFT".equals(oldStatus)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "仅草稿状态可上架");
                }
                break;
            case "ACTIVE":
                // PENDING → ACTIVE (开始)
                if (!"PENDING".equals(oldStatus)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "仅预热状态可激活");
                }
                break;
            case "ENDED":
                // ACTIVE → ENDED
                if (!"ACTIVE".equals(oldStatus)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "仅进行中状态可结束");
                }
                break;
            case "CANCELLED":
                // Any non-final state → CANCELLED
                if ("ENDED".equals(oldStatus)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "已结束的活动不可取消");
                }
                break;
            default:
                throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的状态值: " + newStatus);
        }

        activity.setStatus(newStatus);
        activityMapper.updateById(activity);

        // 清除所有相关Redis缓存（防止statusKey/timeKey残留）
        redisTemplate.delete("seckill:activity:" + id);
        redisTemplate.delete("seckill:activity:status:" + id);
        redisTemplate.delete("seckill:activity:time:" + id);
        redisTemplate.delete("seckill:activity:productId:" + id);
        redisTemplate.delete("seckill:activity:price:" + id);
    }

    @Override
    @Transactional
    public void warmUp(Long id) {
        SeckillActivity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "秒杀活动不存在");
        }
        if (!"PENDING".equals(activity.getStatus()) && !"ACTIVE".equals(activity.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅预热中或进行中的活动可预热");
        }

        String stockKey = "seckill:stock:" + id;
        String boughtSetKey = "seckill:bought:" + id + ":";
        String activityCacheKey = "seckill:activity:" + id;

        // Load stock into Redis (idempotent — only set if not exists)
        Boolean alreadySet = redisTemplate.opsForValue().setIfAbsent(stockKey, String.valueOf(activity.getTotalStock()));
        if (Boolean.TRUE.equals(alreadySet)) {
            // Reset available stock in DB
            activity.setAvailableStock(activity.getTotalStock());
            activityMapper.updateById(activity);
        }

        // Set status key for fast lookup
        long nowEpoch = System.currentTimeMillis();
        long startEpoch = activity.getStartTime()
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endEpoch = activity.getEndTime()
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        // 缓存有效期 = 活动剩余时间，至少2小时兜底
        long ttlMillis = Math.max(endEpoch - Math.max(nowEpoch, startEpoch), 2 * 60 * 60 * 1000L);
        long ttlSeconds = ttlMillis / 1000;

        redisTemplate.opsForValue().set("seckill:activity:status:" + id,
                "ACTIVE", ttlSeconds, TimeUnit.SECONDS);

        // Set time range key for fast lookup (millis)
        redisTemplate.opsForValue().set("seckill:activity:time:" + id,
                startEpoch + "," + endEpoch, ttlSeconds, TimeUnit.SECONDS);

        // Cache activity object
        try {
            SeckillActivityVO vo = toVO(activity);
            redisTemplate.opsForValue().set(activityCacheKey,
                    objectMapper.writeValueAsString(vo),
                    ttlSeconds, TimeUnit.SECONDS);
        } catch (JsonProcessingException ignored) {}

        // 预热时额外存 productId 和 seckillPrice 到Redis（供FlashSaleService快速读取）
        redisTemplate.opsForValue().set("seckill:activity:productId:" + id,
                String.valueOf(activity.getProductId()), ttlSeconds, TimeUnit.SECONDS);
        redisTemplate.opsForValue().set("seckill:activity:price:" + id,
                String.valueOf(activity.getSeckillPrice()), ttlSeconds, TimeUnit.SECONDS);

        // Auto-transition to ACTIVE if PENDING
        if ("PENDING".equals(activity.getStatus())) {
            activity.setStatus("ACTIVE");
            activityMapper.updateById(activity);
        }
    }

    private SeckillActivityVO toVO(SeckillActivity activity) {
        SeckillActivityVO vo = new SeckillActivityVO();
        vo.setId(activity.getId());
        vo.setProductId(activity.getProductId());
        // 查询商品名称
        String name = jdbcTemplate.queryForObject(
                "SELECT name FROM product WHERE id = ?", String.class, activity.getProductId());
        vo.setProductName(name);
        vo.setSeckillPrice(activity.getSeckillPrice());
        vo.setTotalStock(activity.getTotalStock());
        // Read real-time stock from Redis
        String stockStr = redisTemplate.opsForValue().get("seckill:stock:" + activity.getId());
        if (stockStr != null) {
            int realtimeStock = Integer.parseInt(stockStr);
            vo.setAvailableStock(realtimeStock);
            vo.setRemainingStock(realtimeStock);
        } else {
            vo.setAvailableStock(activity.getAvailableStock());
        }
        vo.setStartTime(activity.getStartTime());
        vo.setEndTime(activity.getEndTime());
        vo.setStatus(activity.getStatus());
        vo.setCreatedAt(activity.getCreatedAt());
        return vo;
    }
}
