package com.flashsale.userservice.listener;

import com.flashsale.common.event.AuditLogEvent;
import com.flashsale.userservice.entity.AuditLog;
import com.flashsale.userservice.mapper.AuditLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计日志事件监听器
 * 异步将审计日志写入数据库
 */
@Component
public class AuditLogEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditLogEventListener.class);

    private final AuditLogMapper auditLogMapper;

    public AuditLogEventListener(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Async
    @EventListener
    @Transactional
    public void handleAuditLogEvent(AuditLogEvent event) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(event.getUserId());
            auditLog.setUsername(event.getUsername());
            auditLog.setRole(event.getRole());
            auditLog.setModule(event.getModule());
            auditLog.setAction(event.getAction());
            auditLog.setDescription(event.getDescription());
            auditLog.setRequestMethod(event.getRequestMethod());
            auditLog.setRequestUrl(event.getRequestUrl());
            auditLog.setIp(event.getIp());
            auditLog.setSuccess(event.getSuccess());
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage(), e);
        }
    }
}
