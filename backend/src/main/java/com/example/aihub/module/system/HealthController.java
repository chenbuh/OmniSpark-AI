package com.example.aihub.module.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/admin")
@SaCheckLogin
@SaCheckRole("admin")
public class HealthController {
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @GetMapping("/health")
    public ApiResult<Map<String, Object>> health() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "UP");
        info.put("timestamp", LocalDateTime.now().toString());

        // 数据库检查:异常细节仅记日志,对外只暴露 DOWN 状态
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            info.put("database", "UP");
        } catch (Exception e) {
            log.error("健康检查:数据库连接异常", e);
            info.put("database", "DOWN");
            info.put("status", "DEGRADED");
        }

        // Redis 检查:同上,不回显原始异常
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            info.put("redis", "UP");
        } catch (Exception e) {
            log.error("健康检查:Redis 连接异常", e);
            info.put("redis", "DOWN");
            info.put("status", "DEGRADED");
        }

        info.put("version", appVersion);
        info.put("uptime", System.currentTimeMillis());
        return ApiResult.ok(info);
    }
}
