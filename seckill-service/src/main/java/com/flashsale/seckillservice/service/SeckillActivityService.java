package com.flashsale.seckillservice.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.flashsale.seckillservice.vo.*;

public interface SeckillActivityService {

    /**
     * 分页查询活动列表
     * @param isAdmin true=全部, false=只查 ACTIVE
     */
    IPage<SeckillActivityVO> listActivities(int page, int size, boolean isAdmin);

    /**
     * 查询活动详情（含 Redis 实时库存）
     */
    SeckillActivityVO getActivityById(Long id);

    /**
     * 创建活动
     */
    Long createActivity(SeckillActivityCreateReq req);

    /**
     * 编辑活动
     */
    Long updateActivity(Long id, SeckillActivityUpdateReq req);

    /**
     * 变更活动状态：DRAFT → PENDING（上架）/ ACTIVE → ENDED / 任意 → CANCELLED
     */
    void changeStatus(Long id, String newStatus);

    /**
     * 预热：将活动库存加载到 Redis
     * Redis Keys:
     *   seckill:stock:{activityId}       = totalStock
     *   seckill:activity:{activityId}    = activity JSON
     */
    void warmUp(Long id);
}
