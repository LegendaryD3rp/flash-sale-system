package com.flashsale.seckillservice.controller;

import com.flashsale.common.result.Result;
import com.flashsale.seckillservice.service.FlashSaleService;
import com.flashsale.seckillservice.vo.SeckillRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    private final FlashSaleService flashSaleService;

    public SeckillController(FlashSaleService flashSaleService) {
        this.flashSaleService = flashSaleService;
    }

    /**
     * POST /api/seckill/flash — 秒杀抢购入口
     * userId 从请求头 X-User-Id 获取（由 gateway 透传）
     */
    @PostMapping("/flash")
    public Result<String> flash(@Valid @RequestBody SeckillRequest req,
                                @RequestHeader("X-User-Id") Long userId) {
        String result = flashSaleService.flash(req.getActivityId(), userId);
        return Result.success("秒杀请求已受理", result);
    }

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}
