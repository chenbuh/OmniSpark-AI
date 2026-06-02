package com.example.aihub.common.config;

import cn.dev33.satoken.stp.StpUtil;
import com.example.aihub.common.annotation.RateLimit;
import com.example.aihub.common.security.ClientIpResolver;
import com.example.aihub.common.security.SecurityRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    private static final RateLimitRule DEFAULT_READ_IP_LIMIT = new RateLimitRule(
            180,
            60,
            RateLimit.Dimension.IP,
            "读取请求过于频繁，请稍后再试",
            new String[]{"GET", "HEAD"}
    );
    private static final RateLimitRule DEFAULT_READ_USER_LIMIT = new RateLimitRule(
            90,
            60,
            RateLimit.Dimension.USER_API,
            "读取请求过于频繁，请稍后再试",
            new String[]{"GET", "HEAD"}
    );

    private final StringRedisTemplate redisTemplate;
    private final ClientIpResolver clientIpResolver;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        List<RateLimitRule> limits = resolveLimits(handlerMethod, request);

        String handlerKey = handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName();
        for (RateLimitRule limit : limits) {
            if (!matchesMethod(limit.methods(), request.getMethod())) {
                continue;
            }
            String subject = resolveSubject(limit.dimension(), request);
            if (subject == null) {
                // 该维度无法确定主体（如 USER 维度但当前未登录）：交给后续鉴权拦截器处理，本规则跳过
                continue;
            }
            String key = KEY_PREFIX + limit.dimension().name().toLowerCase(Locale.ROOT)
                    + ":" + subject + ":" + handlerKey + ":" + limit.seconds();
            if (isOverLimit(key, limit)) {
                request.setAttribute(SecurityRequestAttributes.RATE_LIMIT_HIT, Boolean.TRUE);
                writeTooManyRequests(response, limit.message());
                return false;
            }
        }
        return true;
    }

    private List<RateLimitRule> resolveLimits(HandlerMethod handlerMethod, HttpServletRequest request) {
        List<RateLimitRule> limits = new ArrayList<>();
        limits.addAll(toRules(handlerMethod.getBeanType().getAnnotationsByType(RateLimit.class)));
        Method method = handlerMethod.getMethod();
        limits.addAll(toRules(method.getAnnotationsByType(RateLimit.class)));
        if (isDefaultReadLimited(request)) {
            if (!hasMatchingDimension(limits, RateLimit.Dimension.IP, request.getMethod())) {
                limits.add(DEFAULT_READ_IP_LIMIT);
            }
            if (!hasMatchingDimension(limits, RateLimit.Dimension.USER_API, request.getMethod())) {
                limits.add(DEFAULT_READ_USER_LIMIT);
            }
        }
        return limits;
    }

    private boolean hasMatchingDimension(List<RateLimitRule> limits, RateLimit.Dimension dimension, String method) {
        for (RateLimitRule limit : limits) {
            if (limit.dimension() == dimension && matchesMethod(limit.methods(), method)) {
                return true;
            }
        }
        return false;
    }

    private List<RateLimitRule> toRules(RateLimit[] annotations) {
        if (annotations == null || annotations.length == 0) {
            return List.of();
        }
        return Arrays.stream(annotations)
                .map(item -> new RateLimitRule(item.count(), item.seconds(), item.dimension(), item.message(), item.methods()))
                .toList();
    }

    private boolean isDefaultReadLimited(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            return false;
        }
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/")) {
            return false;
        }
        return !(path.startsWith("/api/auth/captcha/")
                || path.equals("/api/auth/public-key")
                || path.equals("/api/auth/sign/challenge")
                || path.equals("/api/admin/health"));
    }

    private boolean matchesMethod(String[] methods, String requestMethod) {
        if (methods == null || methods.length == 0) {
            return true;
        }
        if (requestMethod == null || requestMethod.isBlank()) {
            return false;
        }
        for (String method : methods) {
            if (requestMethod.equalsIgnoreCase(method)) {
                return true;
            }
        }
        return false;
    }

    /** 对单条规则计数并判断是否超限。计数失败（如 Redis 不可用）时放行，避免限流组件自身成为单点故障。 */
    private boolean isOverLimit(String key, RateLimitRule limit) {
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
            case IP -> clientIpResolver.resolve(request);
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

    private void writeTooManyRequests(HttpServletResponse response, String message) throws Exception {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        String safeMessage = message == null ? "操作过于频繁，请稍后再试" : message.replace("\"", "'");
        response.getWriter().write("{\"code\":429,\"message\":\"" + safeMessage + "\",\"data\":null}");
        response.getWriter().flush();
    }

    private record RateLimitRule(int count, int seconds, RateLimit.Dimension dimension, String message, String[] methods) {
    }
}
