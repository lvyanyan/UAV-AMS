package com.uav.system.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uav.common.base.R;
import com.uav.system.annotation.AuditLog;
import com.uav.system.dto.LoginResponse;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 审计切面：登录成功 + 所有标注 @AuditLog 的写方法 → 异步写入 sys_audit_log
 * 审计失败不影响主流程；参数序列化时对 password/secret 类字段做掩码
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    private static final int DETAIL_MAX_LEN = 500;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 单线程异步写库：削峰且保证顺序，进程退出随守护线程终止 */
    private final java.util.concurrent.ExecutorService auditExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "audit-writer");
                t.setDaemon(true);
                return t;
            });

    public AuditAspect(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 登录成功审计（登录失败不记，避免暴力尝试刷库） */
    @AfterReturning(pointcut = "execution(* com.uav.system.controller.AuthController.login(..))", returning = "result")
    public void afterLogin(JoinPoint jp, Object result) {
        if (result instanceof R<?> r && r.getCode() == 200 && r.getData() instanceof LoginResponse resp) {
            saveAsync(resp.getUsername(), "登录系统", resp.getUsername(),
                    resp.isMilitaryLogin() ? "军事登录（MFA）" : "账号密码登录");
        }
    }

    /** @AuditLog 标注的写方法审计 */
    @AfterReturning(pointcut = "@annotation(auditLog)", returning = "result")
    public void afterAudited(JoinPoint jp, AuditLog auditLog, Object result) {
        saveAsync(currentUsername(), auditLog.action(), auditLog.target(), detail(jp));
    }

    // ===== 内部工具 =====

    private void saveAsync(String username, String action, String target, String detail) {
        final Long userId = resolveUserId(username);
        final String ip = currentIp();
        final String user = username == null ? "anonymous" : username;
        auditExecutor.execute(() -> {
            try {
                jdbc.update("INSERT INTO sys_audit_log (user_id, username, action, target, detail, ip_address, "
                        + "created_at, create_time) VALUES (?,?,?,?,?,?, now(), now())",
                        userId, user, action, target, detail, ip);
            } catch (Exception e) {
                log.warn("审计日志写入失败: action={}, target={}, user={}", action, target, user, e);
            }
        });
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }

    private Long resolveUserId(String username) {
        if (username == null || username.isBlank() || "anonymous".equals(username)) return null;
        try {
            return jdbc.queryForObject("SELECT id FROM sys_user WHERE username = ?", Long.class, username);
        } catch (Exception e) {
            return null;
        }
    }

    private String currentIp() {
        try {
            var attrs = RequestContextHolder.currentRequestAttributes();
            HttpServletRequest req = ((ServletRequestAttributes) attrs).getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            return req.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }

    /** 方法入参 JSON 序列化 + 敏感字段掩码 + 截断 */
    private String detail(JoinPoint jp) {
        try {
            Object[] args = jp.getArgs();
            if (args == null || args.length == 0) return jp.getSignature().toShortString();
            StringBuilder sb = new StringBuilder();
            for (Object a : args) {
                if (a == null || a instanceof ServletRequest || a instanceof ServletResponse) continue;
                sb.append(mask(objectMapper.writeValueAsString(a))).append("; ");
            }
            String s = sb.toString();
            if (s.length() > DETAIL_MAX_LEN) s = s.substring(0, DETAIL_MAX_LEN) + "...";
            return s;
        } catch (Exception e) {
            return jp.getSignature().toShortString();
        }
    }

    private String mask(String json) {
        if (json == null) return null;
        return json.replaceAll(
                "(\"(?:password|oldPassword|newPassword|pwd|mfaSecret|mfa_secret|secret)\"\\s*:\\s*\")[^\"]*(\")",
                "$1***$2");
    }
}
