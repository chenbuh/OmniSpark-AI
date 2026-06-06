package com.example.aihub.common.config;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.result.ApiResult;
import com.example.aihub.infrastructure.entity.SystemConfig;
import com.example.aihub.infrastructure.mapper.SystemConfigMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MaintenanceInterceptor implements HandlerInterceptor {
    private final SystemConfigMapper configMapper;
    private final ObjectMapper objectMapper;

    private static final String DEFAULT_MESSAGE = "系统维护中，请稍后再试";
    private static final String MAINTENANCE_MODE_KEY = "maintenance_mode";
    private static final String MAINTENANCE_MESSAGE_KEY = "maintenance_message";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 只拦截 /api/ 开头的请求
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) return true;

        // 管理员放行
        try {
            if (StpUtil.isLogin()) {
                String role = StpUtil.getRoleList().stream().findFirst().orElse("");
                if ("admin".equals(role)) return true;
            }
        } catch (Exception ignored) {}

        // 检查维护模式
        List<SystemConfig> configs = configMapper.selectList(new LambdaQueryWrapper<SystemConfig>()
                .in(SystemConfig::getConfigKey, MAINTENANCE_MODE_KEY, MAINTENANCE_MESSAGE_KEY));
        boolean enabled = false;
        String message = DEFAULT_MESSAGE;
        for (SystemConfig config : configs) {
            if (config == null) {
                continue;
            }
            String key = config.getConfigKey();
            if (MAINTENANCE_MODE_KEY.equals(key)) {
                enabled = "true".equalsIgnoreCase(config.getConfigValue());
            } else if (MAINTENANCE_MESSAGE_KEY.equals(key)) {
                String configuredMessage = config.getConfigValue();
                if (configuredMessage != null && !configuredMessage.isBlank()) {
                    message = configuredMessage;
                }
            }
        }

        if (enabled) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(503);
            response.getWriter().write(objectMapper.writeValueAsString(ApiResult.fail(503, message)));
            return false;
        }

        return true;
    }
}
