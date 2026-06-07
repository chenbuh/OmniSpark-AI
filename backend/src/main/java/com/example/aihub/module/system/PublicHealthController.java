package com.example.aihub.module.system;

import com.example.aihub.common.result.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公开展示的健康检查端点（无需登录），供负载均衡 / Kubernetes / 监控系统使用。
 * 仅返回数据库和 Redis 的 UP/DOWN 状态，不暴露 JVM / 版本等内部信息。
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/system")
public class PublicHealthController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    @GetMapping("/health")
    public ApiResult<Map<String, Object>> health() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "UP");
        info.put("checkedComponents", List.of("database", "redis"));

        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            info.put("database", "UP");
        } catch (Exception e) {
            log.warn("公开健康检查: 数据库连接异常", e);
            info.put("database", "DOWN");
            info.put("status", "DEGRADED");
        }

        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            info.put("redis", "UP");
        } catch (Exception e) {
            log.warn("公开健康检查: Redis 连接异常", e);
            info.put("redis", "DOWN");
            info.put("status", "DEGRADED");
        }

        return ApiResult.ok(info);
    }
}
