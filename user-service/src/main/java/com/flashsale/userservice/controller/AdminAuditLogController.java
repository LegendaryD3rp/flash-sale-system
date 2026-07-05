package com.flashsale.userservice.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.flashsale.common.annotation.AuditLog;
import com.flashsale.common.result.Result;
import com.flashsale.userservice.mapper.AuditLogMapper;
import org.springframework.web.bind.annotation.*;

/**
 * 审计日志查询接口（仅 ADMIN）
 */
@RestController
@RequestMapping("/api/admin/audit-log")
public class AdminAuditLogController {

    private final AuditLogMapper auditLogMapper;

    public AdminAuditLogController(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * GET /api/admin/audit-log — 分页查询审计日志
     */
    @GetMapping
    @AuditLog(module = "审计日志", action = "查询", description = "查询审计日志列表")
    public Result<IPage<com.flashsale.userservice.entity.AuditLog>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String username) {
        LambdaQueryWrapper<com.flashsale.userservice.entity.AuditLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(com.flashsale.userservice.entity.AuditLog::getId);
        if (module != null && !module.isEmpty()) {
            wrapper.eq(com.flashsale.userservice.entity.AuditLog::getModule, module);
        }
        if (action != null && !action.isEmpty()) {
            wrapper.eq(com.flashsale.userservice.entity.AuditLog::getAction, action);
        }
        if (username != null && !username.isEmpty()) {
            wrapper.like(com.flashsale.userservice.entity.AuditLog::getUsername, username);
        }
        return Result.success(auditLogMapper.selectPage(new Page<>(page, size), wrapper));
    }
}
