package com.example.aihub.common.config;

import cn.dev33.satoken.stp.StpUtil;
import com.example.aihub.common.security.ClientIpResolver;
import com.example.aihub.common.security.SecurityRequestAttributes;
import com.example.aihub.infrastructure.entity.AccessLog;
import com.example.aihub.infrastructure.service.AccessLogService;
import com.example.aihub.infrastructure.service.AntiCrawlerRiskService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(0)
@RequiredArgsConstructor
public class AccessLogFilter implements Filter {
    private final ClientIpResolver clientIpResolver;
    private final AccessLogService accessLogService;
    private final AntiCrawlerRiskService antiCrawlerRiskService;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getRequestURI();
        if (!shouldLog(path)) {
            chain.doFilter(request, response);
            return;
        }

        long startedAt = System.currentTimeMillis();
        String clientIp = clientIpResolver.resolve(req);
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startedAt;
            int status = resp.getStatus();
            record(req, status, durationMs, clientIp);
            antiCrawlerRiskService.recordAccess(req, status, clientIp);
        }
    }

    private void record(HttpServletRequest request, int status, long durationMs, String clientIp) {
        try {
            AccessLog log = new AccessLog();
            log.setUserId(currentUserId(request));
            log.setApiKeyId(asLong(request.getAttribute(SecurityRequestAttributes.API_KEY_ID)));
            log.setClientIp(clientIp);
            log.setUserAgent(truncate(request.getHeader("User-Agent"), 500));
            log.setMethod(truncate(request.getMethod(), 16));
            log.setPath(truncate(request.getRequestURI(), 512));
            log.setQueryString(truncate(request.getQueryString(), 1024));
            log.setStatusCode(status);
            log.setDurationMs(durationMs);
            log.setRateLimited(Boolean.TRUE.equals(request.getAttribute(SecurityRequestAttributes.RATE_LIMIT_HIT)) ? 1 : 0);
            Object riskReason = request.getAttribute(SecurityRequestAttributes.RISK_REASON);
            log.setRiskReason(riskReason instanceof String value ? truncate(value, 255) : null);
            accessLogService.record(log);
        } catch (Exception ignored) {
            // 访问日志不应影响请求
        }
    }

    private boolean shouldLog(String path) {
        return path != null && (path.startsWith("/api/") || path.startsWith("/uploads/"));
    }

    private Long currentUserId(HttpServletRequest request) {
        Long apiKeyUserId = asLong(request.getAttribute(SecurityRequestAttributes.API_KEY_USER_ID));
        if (apiKeyUserId != null) {
            return apiKeyUserId;
        }
        try {
            if (StpUtil.isLogin()) {
                return Long.valueOf(StpUtil.getLoginIdAsString());
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Long.valueOf(string);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
