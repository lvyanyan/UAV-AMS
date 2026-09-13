package com.uav.system.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解：标注在写接口方法上，由 AuditAspect 拦截并异步入库 sys_audit_log
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /** 动作描述，如：创建用户 */
    String action();

    /** 操作对象/模块，如：用户管理 */
    String target() default "";
}
