package com.example.aihub.common.config;

import cn.dev33.satoken.stp.StpUtil;
import com.example.aihub.common.security.ClientIpResolver;
import com.example.aihub.common.security.SecurityRequestAttributes;
import com.example.aihub.infrastructure.service.ApiKeyService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
@RequiredArgsConstructor
public class ApiKeyFilter implements Filter {
    private final ApiKeyService apiKeyService;
    private final ClientIpResolver clientIpResolver;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String path = req.getRequestURI();

        // 只处理 /api/ 请求
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        // 如果已经登录（有 session token），不需要 API Key
        if (StpUtil.isLogin()) {
            chain.doFilter(request, response);
            return;
        }

        // 尝试从 X-Api-Key 头获取 API Key
        String apiKey = req.getHeader("X-Api-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            String clientIp = clientIpResolver.resolve(req);
            String userAgent = req.getHeader("User-Agent");
            ApiKeyService.AuthenticationResult result = apiKeyService.authenticate(
                    apiKey,
                    req.getMethod(),
                    path,
                    clientIp,
                    userAgent
            );
            if (!result.success()) {
                writeAuthError((HttpServletResponse) response, result.status(), result.message());
                return;
            }
            ApiKeyService.AuthenticatedApiKey principal = result.principal();
            if (principal != null) {
                // 用 API Key 对应的用户身份登录,并保留 API Key 上下文供审计/限流/风控使用。
                StpUtil.login(principal.userId());
                req.setAttribute(SecurityRequestAttributes.API_KEY_AUTHENTICATED, Boolean.TRUE);
                req.setAttribute(SecurityRequestAttributes.API_KEY_ID, principal.apiKeyId());
                req.setAttribute(SecurityRequestAttributes.API_KEY_PREFIX, principal.keyPrefix());
                req.setAttribute(SecurityRequestAttributes.API_KEY_SCOPE, principal.scope());
            }
        }

        chain.doFilter(request, response);
    }

    private void writeAuthError(HttpServletResponse response, int status, String message) throws IOException {
        int safeStatus = status <= 0 ? 401 : status;
        response.setStatus(safeStatus);
        response.setContentType("application/json;charset=UTF-8");
        String safeMessage = message == null ? "API Key 认证失败" : message.replace("\"", "'");
        response.getWriter().write("{\"code\":" + safeStatus + ",\"message\":\"" + safeMessage + "\",\"data\":null}");
        response.getWriter().flush();
    }
}
