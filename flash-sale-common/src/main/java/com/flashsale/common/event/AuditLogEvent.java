package com.flashsale.common.event;

import org.springframework.context.ApplicationEvent;

/**
 * 审计日志事件，由 AuditLogAspect 发布，监听器负责写入数据库
 */
public class AuditLogEvent extends ApplicationEvent {

    private final Long userId;
    private final String username;
    private final String role;
    private final String module;
    private final String action;
    private final String description;
    private final String requestMethod;
    private final String requestUrl;
    private final String ip;
    private final Boolean success;

    public AuditLogEvent(Object source, Long userId, String username, String role,
                         String module, String action, String description,
                         String requestMethod, String requestUrl, String ip, Boolean success) {
        super(source);
        this.userId = userId;
        this.username = username;
        this.role = role;
        this.module = module;
        this.action = action;
        this.description = description;
        this.requestMethod = requestMethod;
        this.requestUrl = requestUrl;
        this.ip = ip;
        this.success = success;
    }

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public String getModule() { return module; }
    public String getAction() { return action; }
    public String getDescription() { return description; }
    public String getRequestMethod() { return requestMethod; }
    public String getRequestUrl() { return requestUrl; }
    public String getIp() { return ip; }
    public Boolean getSuccess() { return success; }
}
