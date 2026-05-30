package com.example.aihub.module.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/version")
@SaCheckLogin
@SaCheckRole("admin")
public class VersionController {

    @Value("${app.version:1.0.0}")
    private String currentVersion;

    @Value("${app.build-time:unknown}")
    private String buildTime;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();
    /** checkUpdate 结果缓存有效期,避免每次请求都阻塞外呼 GitHub。 */
    private static final long CHECK_CACHE_TTL_MS = 60 * 60 * 1000L;
    private volatile Map<String, Object> cachedCheckResult;
    private volatile long cachedCheckAt = 0L;

    @GetMapping
    public ApiResult<Map<String, Object>> version() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("currentVersion", currentVersion);
        info.put("buildTime", buildTime);
        info.put("serverTime", LocalDateTime.now().toString());
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("osName", System.getProperty("os.name"));
        info.put("osArch", System.getProperty("os.arch"));
        return ApiResult.ok(info);
    }

    @GetMapping("/check")
    public ApiResult<Map<String, Object>> checkUpdate() {
        // 命中缓存直接返回,减少对 GitHub 的阻塞调用
        if (cachedCheckResult != null && System.currentTimeMillis() - cachedCheckAt < CHECK_CACHE_TTL_MS) {
            return ApiResult.ok(cachedCheckResult);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentVersion", currentVersion);
        result.put("checkTime", LocalDateTime.now().toString());

        // 尝试从 GitHub 获取最新版本
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/omnispark/aihub/releases/latest"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var node = MAPPER.readTree(response.body());
                // 用 path() + asText(默认值) 做判空,避免字段缺失时 NPE
                String latest = node.path("tag_name").asText("").replace("v", "");
                String body = node.path("body").asText("");
                result.put("latestVersion", latest.isBlank() ? "未知" : latest);
                result.put("hasUpdate", !latest.isBlank() && !currentVersion.equals(latest));
                result.put("releaseUrl", node.path("html_url").asText(""));
                result.put("releaseNotes", body.substring(0, Math.min(500, body.length())));
            } else {
                result.put("latestVersion", "查询失败");
                result.put("hasUpdate", false);
                result.put("error", "GitHub API 返回 " + response.statusCode());
            }
        } catch (Exception e) {
            result.put("latestVersion", "查询失败");
            result.put("hasUpdate", false);
            result.put("error", "无法连接更新服务器");
        }

        cachedCheckResult = result;
        cachedCheckAt = System.currentTimeMillis();
        return ApiResult.ok(result);
    }
}
