package com.example.aihub.common.config;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Component
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {
    static final String LOGIN_ID_ATTRIBUTE = "websocketLoginId";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   org.springframework.http.server.ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = resolveToken(request);
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            Object loginId = StpUtil.getLoginIdByToken(token);
            if (loginId == null) {
                return false;
            }
            attributes.put(LOGIN_ID_ATTRIBUTE, String.valueOf(loginId));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               org.springframework.http.server.ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }

    private String resolveToken(ServerHttpRequest request) {
        List<String> headerValues = request.getHeaders().get("satoken");
        if (headerValues != null) {
            for (String value : headerValues) {
                if (value != null && !value.isBlank()) {
                    return value.trim();
                }
            }
        }

        if (request instanceof ServletServerHttpRequest servletRequest) {
            String paramToken = servletRequest.getServletRequest().getParameter("satoken");
            if (paramToken != null && !paramToken.isBlank()) {
                return paramToken.trim();
            }
        }

        URI uri = request.getURI();
        if (uri.getRawQuery() == null || uri.getRawQuery().isBlank()) {
            return null;
        }
        for (String part : uri.getRawQuery().split("&")) {
            int index = part.indexOf('=');
            String key = index >= 0 ? part.substring(0, index) : part;
            if (!"satoken".equals(key)) {
                continue;
            }
            String value = index >= 0 ? part.substring(index + 1) : "";
            String decoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
            if (!decoded.isBlank()) {
                return decoded.trim();
            }
        }
        return null;
    }
}
