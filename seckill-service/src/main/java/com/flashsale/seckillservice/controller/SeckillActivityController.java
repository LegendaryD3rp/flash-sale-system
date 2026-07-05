package com.flashsale.seckillservice.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.flashsale.common.result.Result;
import com.flashsale.seckillservice.service.SeckillActivityService;
import com.flashsale.seckillservice.vo.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seckill/activity")
public class SeckillActivityController {

    private final SeckillActivityService activityService;

    public SeckillActivityController(SeckillActivityService activityService) {
        this.activityService = activityService;
    }

    /**
     * GET /api/seckill/activity/list — 活动列表
     * ADMIN sees all, CUSTOMER sees only ACTIVE
     */
    @GetMapping("/list")
    public Result<IPage<SeckillActivityVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-User-Role", defaultValue = "CUSTOMER") String role) {
        boolean isAdmin = "ADMIN".equals(role);
        return Result.success(activityService.listActivities(page, size, isAdmin));
    }

    /**
     * GET /api/seckill/activity/{id} — 活动详情
     */
    @GetMapping("/{id}")
    public Result<SeckillActivityVO> getById(@PathVariable Long id) {
        return Result.success(activityService.getActivityById(id));
    }

    /**
     * POST /api/seckill/activity — 创建活动（ADMIN）
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody SeckillActivityCreateReq req,
                               @RequestHeader("X-User-Role") String role) {
        checkAdmin(role);
        return Result.success(activityService.createActivity(req));
    }

    /**
     * PUT /api/seckill/activity/{id} — 编辑活动（ADMIN，仅DRAFT状态）
     */
    @PutMapping("/{id}")
    public Result<Long> update(@PathVariable Long id,
                               @RequestBody SeckillActivityUpdateReq req,
                               @RequestHeader("X-User-Role") String role) {
        checkAdmin(role);
        return Result.success(activityService.updateActivity(id, req));
    }

    /**
     * PATCH /api/seckill/activity/{id}/status — 变更活动状态（ADMIN）
     * Body: { "status": "PENDING" | "ACTIVE" | "ENDED" | "CANCELLED" }
     */
    @PatchMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id,
                                     @RequestBody StatusChangeReq req,
                                     @RequestHeader("X-User-Role") String role) {
        checkAdmin(role);
        activityService.changeStatus(id, req.getStatus());
        return Result.success();
    }

    /**
     * POST /api/seckill/activity/{id}/warm-up — 手动预热（ADMIN）
     */
    @PostMapping("/{id}/warm-up")
    public Result<Void> warmUp(@PathVariable Long id,
                               @RequestHeader("X-User-Role") String role) {
        checkAdmin(role);
        activityService.warmUp(id);
        return Result.success();
    }

    private void checkAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new com.flashsale.common.exception.BusinessException(
                    com.flashsale.common.constant.ErrorCode.FORBIDDEN, "无权操作");
        }
    }

    /** Inner class for status change request body */
    public static class StatusChangeReq {
        private String status;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
