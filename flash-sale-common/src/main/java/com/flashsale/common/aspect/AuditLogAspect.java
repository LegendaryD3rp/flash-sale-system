package com.flashsale.common.aspect;

import com.flashsale.common.annotation.AuditLog;
import com.flashsale.common.event.AuditLogEvent;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/**
 * 审计日志 AOP 切面
 * 拦截所有 @AuditLog 注解的方法，发布 AuditLogEvent
 */
@Aspect
@Component
public class AuditLogAspect {

    private final ApplicationEventPublisher eventPublisher;

    public AuditLogAspect(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Around("@annotation(com.flashsale.common.annotation.AuditLog)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取注解信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        AuditLog auditLog = method.getAnnotation(AuditLog.class);

        // 构建描述（替换占位符）
        String description = buildDescription(auditLog.description(), joinPoint.getArgs());

        // 获取请求上下文
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();

        // 从请求头获取用户信息
        Long userId = null;
        String username = "";
        String role = "";
        try {
            userId = Long.valueOf(request.getHeader("X-User-Id"));
        } catch (Exception ignored) {}
        username = request.getHeader("X-User-Name") != null ? request.getHeader("X-User-Name") : "";
        role = request.getHeader("X-User-Role") != null ? request.getHeader("X-User-Role") : "";

        String ip = getClientIp(request);

        boolean success = true;
        try {
            Object result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            success = false;
            throw t;
        } finally {
            // 发布异步事件（由 @Async 监听器处理）
            AuditLogEvent event = new AuditLogEvent(
                    this, userId, username, role,
                    auditLog.module(), auditLog.action(), description,
                    request.getMethod(), request.getRequestURI(), ip, success
            );
            eventPublisher.publishEvent(event);
        }
    }

    private String buildDescription(String template, Object[] args) {
        if (template == null || template.isEmpty()) return template;
        String result = template;
        for (int i = 0; i < args.length; i++) {
            String placeholder = "{" + i + "}";
            if (result.contains(placeholder) && args[i] != null) {
                result = result.replace(placeholder, args[i].toString());
            }
        }
        return result;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
