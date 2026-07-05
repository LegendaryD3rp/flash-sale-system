package com.flashsale.common.annotation;

import java.lang.annotation.*;

/**
 * 操作日志审计注解
 * 标注在 Controller 方法上，自动记录操作日志
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /** 操作模块，如 "商品管理", "用户管理" */
    String module();

    /** 操作类型，如 "新增", "修改", "删除", "查询" */
    String action();

    /** 操作描述模板，支持 {0} {1} 占位符（对应方法参数） */
    String description() default "";
}
