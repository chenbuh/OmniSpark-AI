package com.example.aihub.common.config;

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
        if (reason == null || reason.isBlank()) {
            return true;
        }

        String ticket = request.getHeader("X-Risk-Captcha-Ticket");
        if (ticket != null && captchaService.consumeTicket(ticket)) {
            antiCrawlerRiskService.clearRisk(request, clientIp);
            return true;
        }

        request.setAttribute(SecurityRequestAttributes.RISK_REASON, reason);
        writeRiskResponse(response);
        return false;
    }

    private boolean shouldGuard(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/")) {
            return false;
        }
        // 认证链路本身已经叠加了签名、滑块验证码和接口级限流，避免再被通用风险标记卡住登录/注册。
        return !(path.startsWith("/api/auth/")
                || path.equals("/api/auth/sign/challenge")
                || path.equals("/api/auth/public-key"));
    }

    private void writeRiskResponse(HttpServletResponse response) throws Exception {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":429,\"message\":\"检测到异常访问模式，已临时降速，请稍后再试或完成二次验证码\",\"data\":null}");
        response.getWriter().flush();
    }
}
