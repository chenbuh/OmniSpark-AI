package com.example.aihub.common.config;

import cn.dev33.satoken.stp.StpUtil;
import com.example.aihub.common.security.ClientIpResolver;
import com.example.aihub.common.security.SecurityRequestAttributes;
import com.example.aihub.infrastructure.service.AntiCrawlerRiskService;
import com.example.aihub.infrastructure.service.CaptchaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AntiCrawlerRiskInterceptor implements HandlerInterceptor {
    private final ClientIpResolver clientIpResolver;
    private final AntiCrawlerRiskService antiCrawlerRiskService;
    private final CaptchaService captchaService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!shouldGuard(request)) {
            return true;
        }
        String clientIp = clientIpResolver.resolve(request);
        String reason = antiCrawlerRiskService.currentRiskReason(request, clientIp);
        if (reason != null && !reason.isBlank()) {
            String ticket = request.getHeader("X-Risk-Captcha-Ticket");
            if (ticket != null && captchaService.consumeTicket(ticket)) {
                antiCrawlerRiskService.clearRisk(request, clientIp);
            } else {
                request.setAttribute(SecurityRequestAttributes.RISK_REASON, reason);
                writeRiskResponse(response);
                return false;
            }
        }

        String immediateReason = antiCrawlerRiskService.inspectExportRequest(request, clientIp);
        if (immediateReason == null || immediateReason.isBlank()) {
            immediateReason = antiCrawlerRiskService.inspectPublicContentRequest(request, clientIp);
        }
        if (immediateReason == null || immediateReason.isBlank()) {
            return true;
        }
        request.setAttribute(SecurityRequestAttributes.RISK_REASON, immediateReason);
        writeRiskResponse(response);
        return false;
    }

    private boolean shouldGuard(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/")) {
            return false;
        }
        // 已手动登录的站内用户操作，不再走这层通用防爬拦截，避免正常浏览器交互被误伤。
        try {
            if (StpUtil.isLogin()) {
                return false;
            }
        } catch (Exception ignored) {
        }
        // 认证链路本身已经叠加了签名、滑块验证码和接口级限流，避免再被通用风险标记卡住登录/注册。
        return !(path.startsWith("/api/auth/")
                || path.equals("/api/auth/sign/challenge")
                || path.equals("/api/auth/public-key"));
    }

    private void writeRiskResponse(HttpServletResponse response) throws Exception {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":429,\"message\":\"检测到异常访问模式，已临时降速，请稍后再试\",\"data\":null}");
        response.getWriter().flush();
    }
}
