package com.example.aihub.module.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api/admin")
@SaCheckLogin
@SaCheckRole("admin")
public class HealthController {
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    @GetMapping("/health")
    public ApiResult<Map<String, Object>> health() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "UP");
        info.put("timestamp", LocalDateTime.now().toString());

        // 数据库检查
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            info.put("database", "UP");
        } catch (Exception e) {
            info.put("database", "DOWN - " + e.getMessage());
            info.put("status", "DEGRADED");
        }

        // Redis 检查
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            info.put("redis", "UP");
        } catch (Exception e) {
            info.put("redis", "DOWN - " + e.getMessage());
            info.put("status", "DEGRADED");
        }

        info.put("version", "1.0.0");
        info.put("uptime", System.currentTimeMillis());
        return ApiResult.ok(info);
    }
}
