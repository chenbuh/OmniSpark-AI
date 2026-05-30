package com.example.aihub.common.config;

import cn.dev33.satoken.stp.StpUtil;
import com.example.aihub.infrastructure.service.ApiKeyService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
@RequiredArgsConstructor
public class ApiKeyFilter implements Filter {
    private final ApiKeyService apiKeyService;

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
            Long userId = apiKeyService.authenticate(apiKey);
            if (userId != null) {
                // 用 API Key 对应的用户身份登录
                StpUtil.login(userId);
            }
        }

        chain.doFilter(request, response);
    }
}
