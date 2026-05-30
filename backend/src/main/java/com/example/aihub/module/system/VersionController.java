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
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var node = mapper.readTree(response.body());
                String latest = node.get("tag_name").asText().replace("v", "");
                result.put("latestVersion", latest);
                result.put("hasUpdate", !currentVersion.equals(latest));
                result.put("releaseUrl", node.get("html_url").asText());
                result.put("releaseNotes", node.get("body").asText().substring(0, Math.min(500, node.get("body").asText().length())));
            } else {
                result.put("latestVersion", "查询失败");
                result.put("hasUpdate", false);
                result.put("error", "GitHub API 返回 " + response.statusCode());
            }
        } catch (Exception e) {
            result.put("latestVersion", "查询失败");
            result.put("hasUpdate", false);
            result.put("error", "无法连接更新服务器: " + e.getMessage());
        }

        return ApiResult.ok(result);
    }
}
