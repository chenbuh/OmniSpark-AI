package com.example.aihub.common.config;

import cn.dev33.satoken.stp.StpUtil;
import com.example.aihub.common.annotation.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * 基于 Redis 固定窗口的接口限流拦截器。
 *
 * <p>对带 {@link RateLimit} 注解的接口，按维度（IP / USER / USER_API）统计单位时间内的请求次数，
 * 超过阈值返回 429。使用 Redis {@code INCR + EXPIRE}：窗口内第一次请求设置过期时间，
 * 后续累加，过期后自动归零，实现简单且无需额外中间件。
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {
    private static final String KEY_PREFIX = "rate:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RateLimit[] limits = handlerMethod.getMethod().getAnnotationsByType(RateLimit.class);
        if (limits.length == 0) {
            return true;
        }

        String handlerKey = handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName();
        for (RateLimit limit : limits) {
            String subject = resolveSubject(limit.dimension(), request);
            if (subject == null) {
                // 该维度无法确定主体（如 USER 维度但当前未登录）：交给后续鉴权拦截器处理，本规则跳过
                continue;
            }
            String key = KEY_PREFIX + limit.dimension().name().toLowerCase(Locale.ROOT)
                    + ":" + subject + ":" + handlerKey + ":" + limit.seconds();
            if (isOverLimit(key, limit)) {
                writeTooManyRequests(response, limit.message());
                return false;
            }
        }
        return true;
    }

    /** 对单条规则计数并判断是否超限。计数失败（如 Redis 不可用）时放行，避免限流组件自身成为单点故障。 */
    private boolean isOverLimit(String key, RateLimit limit) {
        try {
            Long current = redisTemplate.opsForValue().increment(key);
            if (current == null) {
                return false;
            }
            if (current == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(limit.seconds()));
            }
            return current > limit.count();
        } catch (Exception ex) {
            return false;
        }
    }

    /** 按维度取限流主体；无法确定时返回 null 表示跳过该规则。 */
    private String resolveSubject(RateLimit.Dimension dimension, HttpServletRequest request) {
        return switch (dimension) {
            case IP -> resolveClientIp(request);
            case USER, USER_API -> resolveUserId();
        };
    }

    private String resolveUserId() {
        try {
            if (StpUtil.isLogin()) {
                return String.valueOf(StpUtil.getLoginId());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 取客户端 IP。生产部署在 Nginx/网关后时，{@code getRemoteAddr()} 拿到的是网关 IP，
     * 需读 {@code X-Forwarded-For} 第一段。
     *
     * <p>安全提示：{@code X-Forwarded-For} 可被客户端伪造，必须在网关层强制覆写该头、
     * 只信任自己网关注入的值，否则攻击者可伪造此头绕过 IP 限流。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
            if (!first.isBlank()) {
                return first;
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }

    private void writeTooManyRequests(HttpServletResponse response, String message) throws Exception {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        String safeMessage = message == null ? "操作过于频繁，请稍后再试" : message.replace("\"", "'");
        response.getWriter().write("{\"code\":429,\"message\":\"" + safeMessage + "\",\"data\":null}");
        response.getWriter().flush();
    }
}
