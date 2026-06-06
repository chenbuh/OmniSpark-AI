package com.example.aihub.module.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.meta.BuildMetadataService;
import com.example.aihub.common.result.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/admin")
@SaCheckLogin
@SaCheckRole("admin")
public class HealthController {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final BuildMetadataService buildMetadataService;

    @GetMapping("/health")
    public ApiResult<Map<String, Object>> health() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "UP");
        info.put("timestamp", LocalDateTime.now().toString());

        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        long uptimeMillis = runtimeMXBean.getUptime();
        info.put("uptime", uptimeMillis);
        info.put("uptimeReadable", formatUptime(uptimeMillis));
        info.put("startedAt", Instant.ofEpochMilli(runtimeMXBean.getStartTime())
                .atZone(ZoneId.systemDefault())
                .format(DATE_TIME_FORMATTER));

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

        info.put("version", buildMetadataService.currentVersionForDisplay());
        return ApiResult.ok(info);
    }

    private String formatUptime(long uptimeMillis) {
        long totalSeconds = Math.max(0L, uptimeMillis / 1000);
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append("天 ");
        }
        if (hours > 0 || builder.length() > 0) {
            builder.append(hours).append("小时 ");
        }
        if (minutes > 0 || builder.length() > 0) {
            builder.append(minutes).append("分钟 ");
        }
        builder.append(seconds).append("秒");
        return builder.toString().trim();
    }
}
