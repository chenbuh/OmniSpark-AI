package com.example.aihub.common.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@Order(-1)
public class CorsFilter implements Filter {

    /** 可信来源白名单，仅这些来源允许携带凭证跨域。 */
    @Value("${app.cors.allowed-origins:}")
    private List<String> allowedOrigins;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;
        HttpServletRequest request = (HttpServletRequest) req;

        String origin = request.getHeader("Origin");
        boolean allowed = origin != null && !origin.isBlank()
                && allowedOrigins != null && allowedOrigins.contains(origin);

        if (allowed) {
            // 仅对白名单来源回显具体 Origin 并允许携带凭证，避免任意来源凭证跨域
            response.setHeader("Access-Control-Allow-Origin", origin);
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Vary", "Origin");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH");
            response.setHeader("Access-Control-Allow-Headers",
                    "Content-Type, Authorization, satoken, X-Api-Key, " +
                    "X-Timestamp, X-Nonce, X-Sign, X-Challenge-Id, " +
                    "X-Risk-Captcha-Ticket, X-No-Cache, X-Requested-With, " +
                    "Cache-Control, Pragma");
            response.setHeader("Access-Control-Max-Age", "3600");
        }

        // 预检请求直接返回：白名单命中返回 200，否则 403
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(allowed ? HttpServletResponse.SC_OK : HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        chain.doFilter(req, res);
    }
}
