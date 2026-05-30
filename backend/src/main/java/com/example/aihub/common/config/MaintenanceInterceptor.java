package com.example.aihub.common.config;

import cn.dev33.satoken.spring.SpringMVCUtil;
import cn.dev33.satoken.stp.StpUtil;
import com.example.aihub.infrastructure.mapper.SystemConfigMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class MaintenanceInterceptor implements HandlerInterceptor {
    private final SystemConfigMapper configMapper;

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
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.example.aihub.infrastructure.entity.SystemConfig>()
                .eq(com.example.aihub.infrastructure.entity.SystemConfig::getConfigKey, "maintenance_mode");
        com.example.aihub.infrastructure.entity.SystemConfig config = configMapper.selectOne(wrapper);

        if (config != null && "true".equals(config.getConfigValue())) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(503);
            String msg = "{\"code\":503,\"message\":\"系统维护中，请稍后再试\",\"data\":null}";
            response.getWriter().write(msg);
            return false;
        }

        return true;
    }
}
