package com.example.aihub.module.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.result.ApiResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/version")
@SaCheckLogin
@SaCheckRole("admin")
public class VersionController {

    @Value("${app.version:1.0.0}")
    private String currentVersion;

    @Value("${app.build-time:unknown}")
    private String buildTime;

    @Value("${app.build-commit:}")
    private String buildCommit;

    @Value("${app.build-branch:}")
    private String buildBranch;

    @Value("${app.update.prefer-release:true}")
    private boolean preferRelease;

    @Value("${app.update.cache-ttl-minutes:30}")
    private long cacheTtlMinutes;

    @Value("${app.update.request-timeout-seconds:6}")
    private long requestTimeoutSeconds;

    @Value("${app.update.github.api-base:https://api.github.com}")
    private String githubApiBase;

    @Value("${app.update.github.web-base:https://github.com}")
    private String githubWebBase;

    @Value("${app.update.github.owner:chenbuh}")
    private String githubOwner;

    @Value("${app.update.github.repo:OmniSpark-AI}")
    private String githubRepo;

    @Value("${app.update.github.branch:main}")
    private String githubBranch;

    @Value("${app.update.github.token:}")
    private String githubToken;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private volatile Map<String, Object> cachedCheckResult;
    private volatile long cachedCheckAt = 0L;

    public VersionController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    @GetMapping
    public ApiResult<Map<String, Object>> version() {
        Map<String, Object> info = new LinkedHashMap<>();
        String currentCommitSha = displayCurrentCommitSha();
        info.put("currentVersion", displayCurrentVersion());
        info.put("buildTime", displayBuildTime());
        info.put("currentBranch", displayCurrentBranch());
        info.put("currentCommitSha", currentCommitSha);
        info.put("currentCommitShortSha", abbreviateSha(currentCommitSha));
        info.put("serverTime", LocalDateTime.now().toString());
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("osName", System.getProperty("os.name"));
        info.put("osArch", System.getProperty("os.arch"));
        info.put("updateSource", githubOwner + "/" + githubRepo);
        info.put("defaultBranch", githubBranch);
        info.put("repositoryUrl", buildRepoUrl());
        return ApiResult.ok(info);
    }

    @GetMapping("/check")
    public ApiResult<Map<String, Object>> checkUpdate(@RequestParam(defaultValue = "false") boolean refresh) {
        long ttlMs = Math.max(cacheTtlMinutes, 1) * 60_000L;
        if (!refresh && cachedCheckResult != null && System.currentTimeMillis() - cachedCheckAt < ttlMs) {
            return ApiResult.ok(cachedCheckResult);
        }

        Map<String, Object> result = baseCheckResult();
        try {
            if (preferRelease && tryFillFromRelease(result)) {
                return cacheAndReturn(result);
            }
            if (tryFillFromTag(result)) {
                return cacheAndReturn(result);
            }
            if (tryFillFromCommit(result)) {
                return cacheAndReturn(result);
            }
            result.put("hasUpdate", false);
            result.put("error", "远程仓库暂无可识别的版本发布信息");
        } catch (Exception e) {
            result.put("hasUpdate", false);
            result.put("error", "无法连接更新服务器：" + safeMessage(e));
        }
        return cacheAndReturn(result);
    }

    private ApiResult<Map<String, Object>> cacheAndReturn(Map<String, Object> result) {
        cachedCheckResult = result;
        cachedCheckAt = System.currentTimeMillis();
        return ApiResult.ok(result);
    }

    private Map<String, Object> baseCheckResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        String currentCommitSha = displayCurrentCommitSha();
        result.put("currentVersion", displayCurrentVersion());
        result.put("checkTime", LocalDateTime.now().toString());
        result.put("sourceType", "unknown");
        result.put("sourceLabel", "未知来源");
        result.put("currentBranch", displayCurrentBranch());
        result.put("currentCommitSha", currentCommitSha);
        result.put("currentCommitShortSha", abbreviateSha(currentCommitSha));
        result.put("repositoryUrl", buildRepoUrl());
        result.put("defaultBranch", githubBranch);
        return result;
    }

    private boolean tryFillFromRelease(Map<String, Object> result) throws Exception {
        HttpResponse<String> response = sendGithubGet("/repos/" + encodedRepoPath() + "/releases/latest");
        if (response.statusCode() == 404) {
            return false;
        }
        ensureSuccess(response, "Release");

        JsonNode node = objectMapper.readTree(response.body());
        String rawTag = node.path("tag_name").asText("");
        String latestVersion = normalizeVersion(rawTag);
        if (latestVersion.isBlank()) {
            return false;
        }

        result.put("sourceType", "release");
        result.put("sourceLabel", "GitHub Release");
        result.put("latestVersion", latestVersion);
        result.put("rawTag", rawTag);
        result.put("hasUpdate", hasVersionUpdate(comparisonCurrentVersion(), latestVersion));
        result.put("releaseName", node.path("name").asText(""));
        result.put("releaseUrl", node.path("html_url").asText(buildRepoUrl()));
        result.put("releasePublishedAt", node.path("published_at").asText(""));
        result.put("releaseDraft", node.path("draft").asBoolean(false));
        result.put("releasePrerelease", node.path("prerelease").asBoolean(false));
        result.put("releaseNotes", truncate(node.path("body").asText(""), 4000));

        JsonNode assets = node.path("assets");
        if (assets.isArray() && !assets.isEmpty()) {
            JsonNode firstAsset = assets.get(0);
            result.put("downloadUrl", firstAsset.path("browser_download_url").asText(""));
            result.put("downloadName", firstAsset.path("name").asText(""));
            result.put("assetCount", assets.size());
        } else {
            result.put("downloadUrl", "");
            result.put("downloadName", "");
            result.put("assetCount", 0);
        }
        return true;
    }

    private boolean tryFillFromTag(Map<String, Object> result) throws Exception {
        HttpResponse<String> response = sendGithubGet("/repos/" + encodedRepoPath() + "/tags?per_page=1");
        ensureSuccess(response, "Tag");

        JsonNode tags = objectMapper.readTree(response.body());
        if (!tags.isArray() || tags.isEmpty()) {
            return false;
        }

        JsonNode tag = tags.get(0);
        String rawTag = tag.path("name").asText("");
        String latestVersion = normalizeVersion(rawTag);
        if (latestVersion.isBlank()) {
            return false;
        }

        result.put("sourceType", "tag");
        result.put("sourceLabel", "GitHub 标签");
        result.put("latestVersion", latestVersion);
        result.put("rawTag", rawTag);
        result.put("hasUpdate", hasVersionUpdate(comparisonCurrentVersion(), latestVersion));
        result.put("releaseUrl", buildRepoUrl() + "/releases/tag/" + encodePathSegment(rawTag));
        result.put("tagCommitSha", tag.path("commit").path("sha").asText(""));
        result.put("releaseNotes", "");
        result.put("error", "仓库尚未创建正式 Release，当前基于最新标签判断版本更新");
        return true;
    }

    private boolean tryFillFromCommit(Map<String, Object> result) throws Exception {
        HttpResponse<String> response = sendGithubGet("/repos/" + encodedRepoPath() + "/commits/" + encodePathSegment(githubBranch));
        ensureSuccess(response, "Commit");

        JsonNode node = objectMapper.readTree(response.body());
        String sha = node.path("sha").asText("");
        if (sha.isBlank()) {
            return false;
        }

        JsonNode commitNode = node.path("commit");
        result.put("sourceType", "commit");
        result.put("sourceLabel", "GitHub 最新提交");
        result.put("latestVersion", currentVersion);
        String currentCommitSha = displayCurrentCommitSha();
        boolean canCompareCommit = !currentCommitSha.isBlank();
        boolean hasUpdate = canCompareCommit && !sha.equalsIgnoreCase(currentCommitSha);
        result.put("hasUpdate", hasUpdate);
        result.put("commitComparisonAvailable", canCompareCommit);
        result.put("latestCommitSha", sha);
        result.put("latestCommitShortSha", abbreviateSha(sha));
        result.put("latestCommitMessage", truncate(commitNode.path("message").asText(""), 500));
        result.put("releasePublishedAt", commitNode.path("committer").path("date").asText(""));
        result.put("releaseUrl", node.path("html_url").asText(buildRepoUrl()));
        if (!canCompareCommit) {
            result.put("error", "仓库尚未发布版本标签或 Release，且当前部署未注入提交信息，暂时无法判断是否落后于远程主分支");
        } else if (hasUpdate) {
            result.put("error", "仓库尚未发布版本标签或 Release，当前根据主分支提交差异判断存在更新");
        } else {
            result.put("error", "仓库尚未发布版本标签或 Release，当前已与远程主分支最新提交保持一致");
        }
        return true;
    }

    private HttpResponse<String> sendGithubGet(String path) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(githubApiBase) + path))
                .timeout(Duration.ofSeconds(Math.max(requestTimeoutSeconds, 3)))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "OmniSpark-AI-UpdateChecker");
        if (githubToken != null && !githubToken.isBlank()) {
            builder.header("Authorization", "Bearer " + githubToken.trim());
        }
        return httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private void ensureSuccess(HttpResponse<String> response, String sourceName) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }
        throw new IllegalStateException(sourceName + " API 返回 " + response.statusCode());
    }

    private boolean hasVersionUpdate(String current, String latest) {
        if (current == null || current.isBlank() || latest == null || latest.isBlank()) {
            return false;
        }
        VersionValue currentValue = parseVersion(current);
        VersionValue latestValue = parseVersion(latest);
        if (currentValue != null && latestValue != null) {
            return currentValue.compareTo(latestValue) < 0;
        }
        return !normalizeVersion(current).equalsIgnoreCase(normalizeVersion(latest));
    }

    private VersionValue parseVersion(String value) {
        String normalized = normalizeVersion(value);
        if (normalized.isBlank()) {
            return null;
        }

        String[] buildSplit = normalized.split("\\+", 2);
        String[] prereleaseSplit = buildSplit[0].split("-", 2);
        String[] parts = prereleaseSplit[0].split("\\.");
        if (parts.length == 0 || parts.length > 3) {
            return null;
        }

        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            String prerelease = prereleaseSplit.length > 1 ? prereleaseSplit[1] : "";
            return new VersionValue(major, minor, patch, prerelease);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeVersion(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        while (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }

    private String displayCurrentVersion() {
        return sanitizeBuildValue(currentVersion, "开发环境");
    }

    private String displayBuildTime() {
        return sanitizeBuildValue(buildTime, "开发环境未注入");
    }

    private String displayCurrentCommitSha() {
        String configured = sanitizeBuildValue(buildCommit, "");
        if (!configured.isBlank()) {
            return configured;
        }
        String localCommit = readLocalGitCommitSha();
        return localCommit == null ? "" : localCommit;
    }

    private String displayCurrentBranch() {
        String configured = sanitizeBuildValue(buildBranch, "");
        if (!configured.isBlank()) {
            return configured;
        }
        String localBranch = readLocalGitBranch();
        return localBranch == null ? "" : localBranch;
    }

    private String comparisonCurrentVersion() {
        return sanitizeBuildValue(currentVersion, "");
    }

    private String sanitizeBuildValue(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("@") && trimmed.endsWith("@")) {
            return fallback;
        }
        return trimmed;
    }

    private String encodedRepoPath() {
        return encodePathSegment(githubOwner) + "/" + encodePathSegment(githubRepo);
    }

    private String buildRepoUrl() {
        return trimTrailingSlash(githubWebBase) + "/" + githubOwner + "/" + githubRepo;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength);
    }

    private String abbreviateSha(String sha) {
        if (sha == null || sha.isBlank()) {
            return "";
        }
        return sha.substring(0, Math.min(7, sha.length()));
    }

    private String readLocalGitCommitSha() {
        try {
            Path gitDir = resolveGitDir();
            if (gitDir == null) {
                return null;
            }
            String headContent = readTrimmed(gitDir.resolve("HEAD"));
            if (headContent == null || headContent.isBlank()) {
                return null;
            }
            if (!headContent.startsWith("ref:")) {
                return headContent;
            }
            String ref = headContent.substring(4).trim();
            String sha = readTrimmed(gitDir.resolve(ref));
            if (sha != null && !sha.isBlank()) {
                return sha;
            }
            return readPackedRef(gitDir, ref);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String readLocalGitBranch() {
        try {
            Path gitDir = resolveGitDir();
            if (gitDir == null) {
                return null;
            }
            String headContent = readTrimmed(gitDir.resolve("HEAD"));
            if (headContent == null || !headContent.startsWith("ref:")) {
                return null;
            }
            String ref = headContent.substring(4).trim();
            int lastSlash = ref.lastIndexOf('/');
            return lastSlash >= 0 ? ref.substring(lastSlash + 1) : ref;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Path resolveGitDir() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        while (current != null) {
            Path gitPath = current.resolve(".git");
            if (Files.isDirectory(gitPath)) {
                return gitPath;
            }
            if (Files.isRegularFile(gitPath)) {
                String pointer = readTrimmed(gitPath);
                if (pointer != null && pointer.startsWith("gitdir:")) {
                    String relativePath = pointer.substring("gitdir:".length()).trim();
                    return current.resolve(relativePath).normalize();
                }
            }
            current = current.getParent();
        }
        return null;
    }

    private String readPackedRef(Path gitDir, String ref) {
        Path packedRefsPath = gitDir.resolve("packed-refs");
        if (!Files.exists(packedRefsPath)) {
            return null;
        }
        try {
            List<String> lines = Files.readAllLines(packedRefsPath);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isBlank() || trimmed.startsWith("#") || trimmed.startsWith("^")) {
                    continue;
                }
                String[] parts = trimmed.split("\\s+", 2);
                if (parts.length == 2 && ref.equals(parts[1].trim())) {
                    return parts[0].trim();
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private String readTrimmed(Path path) {
        try {
            if (path == null || !Files.exists(path)) {
                return null;
            }
            return Files.readString(path).trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    private String safeMessage(Exception e) {
        if (e.getMessage() == null || e.getMessage().isBlank()) {
            return e.getClass().getSimpleName();
        }
        return e.getMessage();
    }

    private record VersionValue(int major, int minor, int patch, String prerelease) implements Comparable<VersionValue> {
        @Override
        public int compareTo(VersionValue other) {
            int majorCompare = Integer.compare(major, other.major);
            if (majorCompare != 0) {
                return majorCompare;
            }
            int minorCompare = Integer.compare(minor, other.minor);
            if (minorCompare != 0) {
                return minorCompare;
            }
            int patchCompare = Integer.compare(patch, other.patch);
            if (patchCompare != 0) {
                return patchCompare;
            }
            if (prerelease.isBlank() && other.prerelease.isBlank()) {
                return 0;
            }
            if (prerelease.isBlank()) {
                return 1;
            }
            if (other.prerelease.isBlank()) {
                return -1;
            }
            return prerelease.compareToIgnoreCase(other.prerelease);
        }
    }
}
