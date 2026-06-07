package com.example.aihub.module.system;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.example.aihub.common.meta.BuildMetadataService;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/version")
@SaCheckLogin
@SaCheckRole("admin")
public class VersionController {

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

    @Value("${app.update.github.owner:}")
    private String githubOwner;

    @Value("${app.update.github.repo:}")
    private String githubRepo;

    @Value("${app.update.github.branch:}")
    private String githubBranch;

    @Value("${app.update.github.token:}")
    private String githubToken;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final BuildMetadataService buildMetadataService;

    private volatile Map<String, Object> cachedCheckResult;
    private volatile long cachedCheckAt = 0L;
    private volatile Map<String, Object> cachedHistoryResult;
    private volatile long cachedHistoryAt = 0L;
    private volatile int cachedHistoryLimit = 0;

    public VersionController(ObjectMapper objectMapper, BuildMetadataService buildMetadataService) {
        this.objectMapper = objectMapper;
        this.buildMetadataService = buildMetadataService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    @GetMapping
    public ApiResult<Map<String, Object>> version() {
        Map<String, Object> info = new LinkedHashMap<>();
        String currentCommitSha = displayCurrentCommitSha();
        String repositoryUrl = buildRepoUrl();
        String repositorySlug = buildRepositorySlug();
        boolean updateConfigured = isUpdateSourceConfigured();
        info.put("currentVersion", displayCurrentVersion());
        info.put("buildTime", displayBuildTime());
        info.put("currentBranch", displayCurrentBranch());
        info.put("currentCommitSha", currentCommitSha);
        info.put("currentCommitShortSha", abbreviateSha(currentCommitSha));
        info.put("serverTime", LocalDateTime.now().toString());
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("osName", System.getProperty("os.name"));
        info.put("osArch", System.getProperty("os.arch"));
        info.put("updateSource", updateConfigured ? repositorySlug : "未配置 GitHub 更新源");
        info.put("defaultBranch", effectiveGithubBranch());
        info.put("repositoryUrl", repositoryUrl);
        info.put("updateConfigured", updateConfigured);
        info.put("updateUnavailableReason", updateConfigured ? "" : missingUpdateSourceMessage());
        return ApiResult.ok(info);
    }

    @GetMapping("/check")
    public synchronized ApiResult<Map<String, Object>> checkUpdate(@RequestParam(defaultValue = "false") boolean refresh) {
        long ttlMs = Math.max(cacheTtlMinutes, 1) * 60_000L;
        if (!refresh && cachedCheckResult != null && isCacheValid(cachedCheckAt, ttlMs)) {
            return ApiResult.ok(cachedCheckResult);
        }

        Map<String, Object> result = baseCheckResult();
        if (!isUpdateSourceConfigured()) {
            result.put("hasUpdate", false);
            result.put("error", missingUpdateSourceMessage());
            return cacheAndReturn(result);
        }
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

    @GetMapping("/history")
    public synchronized ApiResult<Map<String, Object>> history(@RequestParam(defaultValue = "false") boolean refresh,
                                                  @RequestParam(defaultValue = "6") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        long ttlMs = Math.max(cacheTtlMinutes, 1) * 60_000L;
        if (!refresh
                && cachedHistoryResult != null
                && cachedHistoryLimit == safeLimit
                && isCacheValid(cachedHistoryAt, ttlMs)) {
            return ApiResult.ok(cachedHistoryResult);
        }

        Map<String, Object> result = baseHistoryResult();
        if (!isUpdateSourceConfigured()) {
            result.put("error", missingUpdateSourceMessage());
            return cacheAndReturnHistory(result, safeLimit);
        }
        try {
            if (preferRelease && tryFillHistoryFromRelease(result, safeLimit)) {
                return cacheAndReturnHistory(result, safeLimit);
            }
            if (tryFillHistoryFromCommit(result, safeLimit)) {
                return cacheAndReturnHistory(result, safeLimit);
            }
            result.put("items", new ArrayList<>());
            result.put("total", 0);
            result.put("error", "远程仓库暂无可读取的更新记录");
        } catch (Exception e) {
            result.put("items", new ArrayList<>());
            result.put("total", 0);
            result.put("error", "无法读取更新历史：" + safeMessage(e));
        }
        return cacheAndReturnHistory(result, safeLimit);
    }

    private ApiResult<Map<String, Object>> cacheAndReturn(Map<String, Object> result) {
        cachedCheckResult = result;
        cachedCheckAt = System.currentTimeMillis();
        return ApiResult.ok(result);
    }

    private ApiResult<Map<String, Object>> cacheAndReturnHistory(Map<String, Object> result, int limit) {
        cachedHistoryResult = result;
        cachedHistoryAt = System.currentTimeMillis();
        cachedHistoryLimit = limit;
        return ApiResult.ok(result);
    }

    private Map<String, Object> baseCheckResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        String currentCommitSha = displayCurrentCommitSha();
        String defaultBranch = effectiveGithubBranch();
        result.put("currentVersion", displayCurrentVersion());
        result.put("checkTime", LocalDateTime.now().toString());
        result.put("sourceType", "unknown");
        result.put("sourceLabel", "未知来源");
        result.put("currentBranch", displayCurrentBranch());
        result.put("currentCommitSha", currentCommitSha);
        result.put("currentCommitShortSha", abbreviateSha(currentCommitSha));
        result.put("repositoryUrl", buildRepoUrl());
        result.put("defaultBranch", defaultBranch);
        result.put("latestRefName", defaultBranch);
        result.put("updateConfigured", isUpdateSourceConfigured());
        result.put("updateUnavailableReason", isUpdateSourceConfigured() ? "" : missingUpdateSourceMessage());
        return result;
    }

    private Map<String, Object> baseHistoryResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        String currentCommitSha = displayCurrentCommitSha();
        result.put("currentVersion", displayCurrentVersion());
        result.put("currentBranch", displayCurrentBranch());
        result.put("currentCommitSha", currentCommitSha);
        result.put("currentCommitShortSha", abbreviateSha(currentCommitSha));
        result.put("defaultBranch", effectiveGithubBranch());
        result.put("repositoryUrl", buildRepoUrl());
        result.put("checkTime", LocalDateTime.now().toString());
        result.put("sourceType", "unknown");
        result.put("sourceLabel", "未知来源");
        result.put("items", new ArrayList<>());
        result.put("total", 0);
        result.put("updateConfigured", isUpdateSourceConfigured());
        result.put("updateUnavailableReason", isUpdateSourceConfigured() ? "" : missingUpdateSourceMessage());
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
        HttpResponse<String> response = sendGithubGet("/repos/" + encodedRepoPath() + "/commits/" + encodePathSegment(effectiveGithubBranch()));
        ensureSuccess(response, "Commit");

        JsonNode node = objectMapper.readTree(response.body());
        String sha = node.path("sha").asText("");
        if (sha.isBlank()) {
            return false;
        }

        JsonNode commitNode = node.path("commit");
        String branchName = effectiveGithubBranch();
        result.put("sourceType", "commit");
        result.put("sourceLabel", "GitHub 最新提交");
        result.put("latestVersion", "");
        result.put("latestRefName", branchName);
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

    private boolean tryFillHistoryFromRelease(Map<String, Object> result, int limit) throws Exception {
        HttpResponse<String> response = sendGithubGet("/repos/" + encodedRepoPath() + "/releases?per_page=" + limit);
        ensureSuccess(response, "Release history");

        JsonNode releases = objectMapper.readTree(response.body());
        if (!releases.isArray() || releases.isEmpty()) {
            return false;
        }

        String currentVersion = comparisonCurrentVersion();
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode releaseNode : releases) {
            String rawTag = releaseNode.path("tag_name").asText("");
            String normalizedVersion = normalizeVersion(rawTag);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sourceType", "release");
            item.put("version", normalizedVersion);
            item.put("rawTag", rawTag);
            item.put("name", releaseNode.path("name").asText(""));
            item.put("publishedAt", releaseNode.path("published_at").asText(""));
            item.put("url", releaseNode.path("html_url").asText(buildRepoUrl()));
            item.put("notes", truncate(releaseNode.path("body").asText(""), 1200));
            item.put("draft", releaseNode.path("draft").asBoolean(false));
            item.put("prerelease", releaseNode.path("prerelease").asBoolean(false));
            item.put("latest", items.isEmpty());
            item.put("installed", !currentVersion.isBlank()
                    && !normalizedVersion.isBlank()
                    && normalizeVersion(currentVersion).equalsIgnoreCase(normalizedVersion));

            JsonNode assets = releaseNode.path("assets");
            if (assets.isArray() && !assets.isEmpty()) {
                JsonNode firstAsset = assets.get(0);
                item.put("downloadUrl", firstAsset.path("browser_download_url").asText(""));
            } else {
                item.put("downloadUrl", "");
            }
            items.add(item);
        }

        if (items.isEmpty()) {
            return false;
        }

        result.put("sourceType", "release");
        result.put("sourceLabel", "GitHub Releases");
        result.put("items", items);
        result.put("total", items.size());
        return true;
    }

    private boolean tryFillHistoryFromCommit(Map<String, Object> result, int limit) throws Exception {
        String branchName = effectiveGithubBranch();
        HttpResponse<String> response = sendGithubGet("/repos/" + encodedRepoPath()
                + "/commits?sha=" + encodePathSegment(branchName)
                + "&per_page=" + limit);
        ensureSuccess(response, "Commit history");

        JsonNode commits = objectMapper.readTree(response.body());
        if (!commits.isArray() || commits.isEmpty()) {
            return false;
        }

        String currentCommitSha = displayCurrentCommitSha();
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonNode commitNode : commits) {
            String sha = commitNode.path("sha").asText("");
            if (sha.isBlank()) {
                continue;
            }
            JsonNode detailNode = commitNode.path("commit");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sourceType", "commit");
            item.put("refName", branchName);
            item.put("sha", sha);
            item.put("shortSha", abbreviateSha(sha));
            item.put("message", truncate(detailNode.path("message").asText(""), 500));
            item.put("publishedAt", detailNode.path("committer").path("date").asText(""));
            item.put("author", firstNonBlank(
                    detailNode.path("author").path("name").asText(""),
                    commitNode.path("author").path("login").asText(""),
                    ""));
            item.put("url", commitNode.path("html_url").asText(buildRepoUrl()));
            item.put("latest", items.isEmpty());
            item.put("installed", !currentCommitSha.isBlank() && sha.equalsIgnoreCase(currentCommitSha));
            items.add(item);
        }

        if (items.isEmpty()) {
            return false;
        }

        result.put("sourceType", "commit");
        result.put("sourceLabel", "GitHub 提交记录");
        result.put("items", items);
        result.put("total", items.size());
        return true;
    }

    private HttpResponse<String> sendGithubGet(String path) throws Exception {
        if (effectiveGithubOwner().isBlank() || effectiveGithubRepo().isBlank()) {
            throw new IllegalStateException("当前仓库远端待确认，无法检查 GitHub 更新");
        }
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

    private boolean isCacheValid(long cachedAt, long ttlMs) {
        return cachedAt > 0 && System.currentTimeMillis() - cachedAt < ttlMs;
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
        return buildMetadataService.currentVersionForDisplay();
    }

    private String displayBuildTime() {
        return buildMetadataService.buildTimeForDisplay();
    }

    private String displayCurrentCommitSha() {
        return buildMetadataService.currentCommitSha();
    }

    private String displayCurrentBranch() {
        return buildMetadataService.currentBranch();
    }

    private String comparisonCurrentVersion() {
        return buildMetadataService.currentVersionOrBlank();
    }

    private String encodedRepoPath() {
        return encodePathSegment(effectiveGithubOwner()) + "/" + encodePathSegment(effectiveGithubRepo());
    }

    private String buildRepoUrl() {
        String detectedUrl = buildMetadataService.repositoryUrl();
        if (!detectedUrl.isBlank()) {
            return detectedUrl;
        }
        String owner = effectiveGithubOwner();
        String repo = effectiveGithubRepo();
        if (owner.isBlank() || repo.isBlank()) {
            return "";
        }
        return trimTrailingSlash(githubWebBase) + "/" + owner + "/" + repo;
    }

    private String buildRepositorySlug() {
        String detectedSlug = buildMetadataService.repositorySlug();
        if (!detectedSlug.isBlank()) {
            return detectedSlug;
        }
        String owner = effectiveGithubOwner();
        String repo = effectiveGithubRepo();
        if (owner.isBlank() || repo.isBlank()) {
            return "";
        }
        return owner + "/" + repo;
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

    private String safeMessage(Exception e) {
        if (e.getMessage() == null || e.getMessage().isBlank()) {
            return e.getClass().getSimpleName();
        }
        return e.getMessage();
    }

    private String effectiveGithubOwner() {
        return firstNonBlank(trimToEmpty(githubOwner), buildMetadataService.repositoryOwner(), "");
    }

    private String effectiveGithubRepo() {
        return firstNonBlank(trimToEmpty(githubRepo), buildMetadataService.repositoryName(), "");
    }

    private String effectiveGithubBranch() {
        return firstNonBlank(trimToEmpty(githubBranch), buildMetadataService.defaultRemoteBranch(), displayCurrentBranch(), "main");
    }

    private boolean isUpdateSourceConfigured() {
        return !effectiveGithubOwner().isBlank() && !effectiveGithubRepo().isBlank();
    }

    private String missingUpdateSourceMessage() {
        return "未配置 GitHub 仓库 owner/repo，系统更新检查当前不可用";
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
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
