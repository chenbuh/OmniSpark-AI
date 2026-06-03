package com.example.aihub.infrastructure.service;

import cn.dev33.satoken.stp.StpUtil;
import com.example.aihub.common.security.SecurityRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AntiCrawlerRiskService {
    private static final String READ_KEY = "risk:read:";
    private static final String EXPORT_KEY = "risk:export:";
    private static final String FIXED_INTERVAL_KEY = "risk:fixed:";
    private static final String SOURCE_KEY = "risk:source:";
    private static final String FLAG_KEY = "risk:flag:";
    private static final String ASSET_PAGE_KEY = "risk:asset:page:";
    private static final String ASSET_DETAIL_KEY = "risk:asset:detail:";
    private static final String ASSET_UNIQUE_KEY = "risk:asset:unique:";
    private static final String UPLOAD_STEADY_KEY = "risk:upload:steady:";
    private static final String UPLOAD_STEADY_UNIQUE_KEY = "risk:upload:steady:unique:";
    private static final String UPLOAD_BURST_UNIQUE_KEY = "risk:upload:burst:unique:";
    private static final String UPLOAD_BURST_EVENT_KEY = "risk:upload:burst:event:";
    private static final String UPLOAD_BURST_EVENT_MARK_KEY = "risk:upload:burst:event:mark:";
    private static final String UPLOAD_SLOW_KEY = "risk:upload:slow:";
    private static final String UPLOAD_SLOW_UNIQUE_KEY = "risk:upload:slow:unique:";
    private static final String UPLOAD_IP_FAST_USER_KEY = "risk:upload:ip-fast:user:";
    private static final String UPLOAD_IP_FAST_UNIQUE_KEY = "risk:upload:ip-fast:unique:";
    private static final String UPLOAD_IP_UA_FAST_USER_KEY = "risk:upload:ip-ua-fast:user:";
    private static final String UPLOAD_IP_UA_FAST_UNIQUE_KEY = "risk:upload:ip-ua-fast:unique:";
    private static final Duration ASSET_HARVEST_TTL = Duration.ofMinutes(15);
    private static final long MAX_ASSET_PAGE_SCANS = 8;
    private static final long MAX_ASSET_DETAIL_HITS = 60;
    private static final long MAX_ASSET_UNIQUE_RESOURCES = 40;
    private static final long MAX_ASSET_COMBINED_UNIQUE = 20;

    private final StringRedisTemplate redisTemplate;

    @Value("${app.security.upload-access.download.risk.fast-ip-window-seconds:10}")
    private long uploadFastIpWindowSeconds;

    @Value("${app.security.upload-access.download.risk.fast-ip-max-users:2}")
    private long uploadFastIpMaxUsers;

    @Value("${app.security.upload-access.download.risk.fast-ip-max-unique-files:15}")
    private long uploadFastIpMaxUniqueFiles;

    @Value("${app.security.upload-access.download.risk.fast-ip-ua-window-seconds:10}")
    private long uploadFastIpUaWindowSeconds;

    @Value("${app.security.upload-access.download.risk.fast-ip-ua-max-users:1}")
    private long uploadFastIpUaMaxUsers;

    @Value("${app.security.upload-access.download.risk.fast-ip-ua-max-unique-files:12}")
    private long uploadFastIpUaMaxUniqueFiles;

    @Value("${app.security.upload-access.download.risk.steady-window-minutes:2}")
    private long uploadSteadyWindowMinutes;

    @Value("${app.security.upload-access.download.risk.steady-interval-min-millis:900}")
    private long uploadSteadyIntervalMinMillis;

    @Value("${app.security.upload-access.download.risk.steady-interval-max-millis:5000}")
    private long uploadSteadyIntervalMaxMillis;

    @Value("${app.security.upload-access.download.risk.steady-interval-jitter-millis:160}")
    private long uploadSteadyIntervalJitterMillis;

    @Value("${app.security.upload-access.download.risk.steady-streak-user:10}")
    private long uploadSteadyStreakUser;

    @Value("${app.security.upload-access.download.risk.steady-min-unique-files-user:12}")
    private long uploadSteadyMinUniqueFilesUser;

    @Value("${app.security.upload-access.download.risk.burst-window-seconds:3}")
    private long uploadBurstWindowSeconds;

    @Value("${app.security.upload-access.download.risk.burst-min-unique-files-user:12}")
    private long uploadBurstMinUniqueFilesUser;

    @Value("${app.security.upload-access.download.risk.burst-event-window-minutes:2}")
    private long uploadBurstEventWindowMinutes;

    @Value("${app.security.upload-access.download.risk.burst-max-events-user:2}")
    private long uploadBurstMaxEventsUser;

    @Value("${app.security.upload-access.download.risk.slow-window-minutes:20}")
    private long uploadSlowWindowMinutes;

    @Value("${app.security.upload-access.download.risk.slow-interval-min-millis:6000}")
    private long uploadSlowIntervalMinMillis;

    @Value("${app.security.upload-access.download.risk.slow-interval-max-millis:45000}")
    private long uploadSlowIntervalMaxMillis;

    @Value("${app.security.upload-access.download.risk.slow-interval-jitter-millis:1200}")
    private long uploadSlowIntervalJitterMillis;

    @Value("${app.security.upload-access.download.risk.slow-streak-user:8}")
    private long uploadSlowStreakUser;

    @Value("${app.security.upload-access.download.risk.slow-min-unique-files-user:10}")
    private long uploadSlowMinUniqueFilesUser;

    public String currentRiskReason(HttpServletRequest request, String clientIp) {
        String subject = subject(request, clientIp);
        if (subject == null) {
            return null;
        }
        return redisTemplate.opsForValue().get(FLAG_KEY + subject);
    }

    public void clearRisk(HttpServletRequest request, String clientIp) {
        String subject = subject(request, clientIp);
        if (subject != null) {
            redisTemplate.delete(FLAG_KEY + subject);
        }
    }

    public void flag(HttpServletRequest request, String clientIp, String reason, Duration ttl) {
        String subject = subject(request, clientIp);
        if (subject == null || reason == null || reason.isBlank()) {
            return;
        }
        redisTemplate.opsForValue().set(FLAG_KEY + subject, reason, ttl == null ? Duration.ofMinutes(30) : ttl);
    }

    public void recordAccess(HttpServletRequest request, int statusCode, String clientIp) {
        if (request == null || statusCode >= 500) {
            return;
        }
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (!isHighValue(method, path)) {
            return;
        }
        String subject = subject(request, clientIp);
        if (subject == null) {
            return;
        }
        try {
            if (isRead(method)) {
                long reads = increment(READ_KEY + subject, Duration.ofMinutes(1));
                if (reads > 120) {
                    flag(subject, "1 分钟内读取高价值接口超过 120 次");
                }
            }
            if (isExport(method, path)) {
                long exports = increment(EXPORT_KEY + subject, Duration.ofHours(1));
                if (exports > 5) {
                    flag(subject, "1 小时内连续导出超过 5 次");
                }
            }
            detectSourceChange(subject, clientIp, request.getHeader("User-Agent"));
            detectFixedInterval(subject);
            detectAssetHarvest(request, subject, clientIp, path, method);
        } catch (Exception ignored) {
            // 风控计数不可影响主业务
        }
    }

    private long increment(String key, Duration ttl) {
        Long value = redisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            redisTemplate.expire(key, ttl);
        }
        return value == null ? 0 : value;
    }

    private void flag(String subject, String reason) {
        redisTemplate.opsForValue().set(FLAG_KEY + subject, reason, Duration.ofMinutes(30));
    }

    private void detectFixedInterval(String subject) {
        long now = System.currentTimeMillis();
        String key = FIXED_INTERVAL_KEY + subject;
        String previous = redisTemplate.opsForValue().get(key);
        if (previous == null || previous.isBlank()) {
            redisTemplate.opsForValue().set(key, now + ":0:0", Duration.ofMinutes(10));
            return;
        }
        String[] parts = previous.split(":");
        long lastAt = parseLong(parts, 0);
        long lastInterval = parseLong(parts, 1);
        long streak = parseLong(parts, 2);
        long interval = now - lastAt;
        if (interval >= 800 && interval <= 15_000 && lastInterval > 0 && Math.abs(interval - lastInterval) <= 120) {
            streak++;
        } else {
            streak = 0;
        }
        if (streak >= 12) {
            flag(subject, "检测到固定间隔批量访问模式");
        }
        redisTemplate.opsForValue().set(key, now + ":" + interval + ":" + streak, Duration.ofMinutes(10));
    }

    private void detectSourceChange(String subject, String clientIp, String userAgent) {
        if (subject == null || (!subject.startsWith("user:") && !subject.startsWith("apiKey:"))) {
            return;
        }
        String normalizedIp = clientIp == null ? "" : clientIp;
        String normalizedUa = normalizeUserAgent(userAgent);
        String key = SOURCE_KEY + subject;
        String previous = redisTemplate.opsForValue().get(key);
        if (previous == null || previous.isBlank()) {
            redisTemplate.opsForValue().set(key, normalizedIp + "\n" + normalizedUa + "\n0", Duration.ofMinutes(30));
            return;
        }
        String[] parts = previous.split("\n", -1);
        String lastIp = parts.length > 0 ? parts[0] : "";
        String lastUa = parts.length > 1 ? parts[1] : "";
        int score = (int) parseLong(parts, 2);
        if (!lastIp.isBlank() && !normalizedIp.isBlank() && !lastIp.equals(normalizedIp)) {
            score += 2;
        }
        if (!lastUa.isBlank() && !normalizedUa.isBlank() && !lastUa.equals(normalizedUa)) {
            score += 1;
        }
        if (score >= 6) {
            flag(subject, "同一账号/API Key 来源 IP 或 User-Agent 变化异常");
        }
        redisTemplate.opsForValue().set(key, normalizedIp + "\n" + normalizedUa + "\n" + score, Duration.ofMinutes(30));
    }

    private void detectAssetHarvest(HttpServletRequest request, String subject, String clientIp, String path, String method) {
        if (!isRead(method) || path == null || subject == null) {
            return;
        }
        // 浏览器在媒体库中批量加载缩略图/预览图是正常交互，不应被视为爬虫扫库或稳定下载节奏。
        if (isInlineMediaPreview(request, path)) {
            return;
        }
        detectUploadRhythm(request, subject, clientIp, path, method);
        String pageKey = ASSET_PAGE_KEY + subject;
        String detailKey = ASSET_DETAIL_KEY + subject;
        String uniqueKey = ASSET_UNIQUE_KEY + subject;

        long pageCount = isAssetPagePath(path)
                ? increment(pageKey, ASSET_HARVEST_TTL)
                : readCounter(pageKey);
        long detailHits = readCounter(detailKey);
        long uniqueCount = readSetSize(uniqueKey);
        String fingerprint = assetFingerprint(path);
        if (fingerprint != null) {
            detailHits = increment(detailKey, ASSET_HARVEST_TTL);
            uniqueCount = distinctCount(uniqueKey, fingerprint, ASSET_HARVEST_TTL);
        }

        if (uniqueCount > MAX_ASSET_UNIQUE_RESOURCES) {
            flag(subject, "15 分钟内持续访问大量不同资产资源");
            return;
        }
        if (pageCount > MAX_ASSET_PAGE_SCANS && uniqueCount > MAX_ASSET_COMBINED_UNIQUE) {
            flag(subject, "检测到资产分页扫库并伴随详情/下载遍历");
            return;
        }
        if (detailHits > MAX_ASSET_DETAIL_HITS && uniqueCount > MAX_ASSET_COMBINED_UNIQUE) {
            flag(subject, "检测到持续性的资产详情/版本/下载批量遍历");
        }
    }

    private void detectUploadRhythm(HttpServletRequest request, String subject, String clientIp, String path, String method) {
        if (!isRead(method) || request == null || path == null || !path.startsWith("/uploads/")) {
            return;
        }
        String fingerprint = assetFingerprint(path);
        if (fingerprint == null) {
            return;
        }

        if (subject.startsWith("user:")) {
            Duration burstTtl = Duration.ofSeconds(Math.max(uploadBurstWindowSeconds, 1));
            long burstUniqueCount = distinctCount(UPLOAD_BURST_UNIQUE_KEY + subject, fingerprint, burstTtl);
            if (burstUniqueCount >= uploadBurstMinUniqueFilesUser) {
                Boolean firstBurstInWindow = redisTemplate.opsForValue().setIfAbsent(
                        UPLOAD_BURST_EVENT_MARK_KEY + subject,
                        "1",
                        burstTtl
                );
                if (Boolean.TRUE.equals(firstBurstInWindow)) {
                    long burstEvents = increment(
                            UPLOAD_BURST_EVENT_KEY + subject,
                            Duration.ofMinutes(Math.max(uploadBurstEventWindowMinutes, 1))
                    );
                    if (burstEvents >= uploadBurstMaxEventsUser) {
                        flag(subject, "检测到秒级突发批量下载行为");
                        return;
                    }
                }
            }

            Duration steadyTtl = Duration.ofMinutes(Math.max(uploadSteadyWindowMinutes, 1));
            long streak = recordIntervalStreak(
                    UPLOAD_STEADY_KEY + subject,
                    steadyTtl,
                    uploadSteadyIntervalMinMillis,
                    uploadSteadyIntervalMaxMillis,
                    uploadSteadyIntervalJitterMillis
            );
            long uniqueCount = distinctCount(UPLOAD_STEADY_UNIQUE_KEY + subject, fingerprint, steadyTtl);
            if (streak >= uploadSteadyStreakUser && uniqueCount >= uploadSteadyMinUniqueFilesUser) {
                flag(subject, "检测到稳定间隔的批量下载节奏");
                return;
            }

            Duration slowTtl = Duration.ofMinutes(Math.max(uploadSlowWindowMinutes, 1));
            long slowStreak = recordIntervalStreak(
                    UPLOAD_SLOW_KEY + subject,
                    slowTtl,
                    uploadSlowIntervalMinMillis,
                    uploadSlowIntervalMaxMillis,
                    uploadSlowIntervalJitterMillis
            );
            long slowUniqueCount = distinctCount(UPLOAD_SLOW_UNIQUE_KEY + subject, fingerprint, slowTtl);
            if (slowStreak >= uploadSlowStreakUser && slowUniqueCount >= uploadSlowMinUniqueFilesUser) {
                flag(subject, "检测到长时间低抖动的批量顺序拉取");
                return;
            }
        }

        Long signedUserId = asLong(request.getAttribute(SecurityRequestAttributes.UPLOAD_SIGNED_USER_ID));
        if (signedUserId == null || signedUserId <= 0 || clientIp == null || clientIp.isBlank()) {
            return;
        }

        Duration ipFastTtl = Duration.ofSeconds(Math.max(uploadFastIpWindowSeconds, 1));
        long ipFastUsers = distinctCount(UPLOAD_IP_FAST_USER_KEY + clientIp, String.valueOf(signedUserId), ipFastTtl);
        long ipFastFiles = distinctCount(UPLOAD_IP_FAST_UNIQUE_KEY + clientIp, fingerprint, ipFastTtl);
        if (ipFastUsers > uploadFastIpMaxUsers && ipFastFiles > uploadFastIpMaxUniqueFiles) {
            flag(subject, "10 秒内同一 IP 跨多个账号访问不同资源超过 " + uploadFastIpMaxUniqueFiles + " 个");
            return;
        }

        Duration ipUaFastTtl = Duration.ofSeconds(Math.max(uploadFastIpUaWindowSeconds, 1));
        String uaBucket = userAgentBucket(request.getHeader("User-Agent"));
        long ipUaFastUsers = distinctCount(
                UPLOAD_IP_UA_FAST_USER_KEY + clientIp + ":" + uaBucket,
                String.valueOf(signedUserId),
                ipUaFastTtl
        );
        long ipUaFastFiles = distinctCount(
                UPLOAD_IP_UA_FAST_UNIQUE_KEY + clientIp + ":" + uaBucket,
                fingerprint,
                ipUaFastTtl
        );
        if (ipUaFastUsers > uploadFastIpUaMaxUsers && ipUaFastFiles > uploadFastIpUaMaxUniqueFiles) {
            flag(subject, "10 秒内同一 IP/UA 跨多个账号访问不同资源超过 " + uploadFastIpUaMaxUniqueFiles + " 个");
        }
    }

    private long distinctCount(String key, String value, Duration ttl) {
        Long added = redisTemplate.opsForSet().add(key, value);
        if (added != null && added > 0) {
            redisTemplate.expire(key, ttl);
        } else {
            redisTemplate.expire(key, ttl);
        }
        Long size = redisTemplate.opsForSet().size(key);
        return size == null ? 0 : size;
    }

    private long readCounter(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private long readSetSize(String key) {
        Long size = redisTemplate.opsForSet().size(key);
        return size == null ? 0 : size;
    }

    private long recordIntervalStreak(String key, Duration ttl, long minIntervalMillis, long maxIntervalMillis, long jitterMillis) {
        long now = System.currentTimeMillis();
        String previous = redisTemplate.opsForValue().get(key);
        if (previous == null || previous.isBlank()) {
            redisTemplate.opsForValue().set(key, now + ":0:0", ttl);
            return 0;
        }

        String[] parts = previous.split(":");
        long lastAt = parseLong(parts, 0);
        long lastInterval = parseLong(parts, 1);
        long streak = parseLong(parts, 2);
        long interval = Math.max(now - lastAt, 0);
        if (interval >= minIntervalMillis
                && interval <= maxIntervalMillis
                && lastInterval > 0
                && Math.abs(interval - lastInterval) <= jitterMillis) {
            streak++;
        } else if (interval >= minIntervalMillis && interval <= maxIntervalMillis) {
            streak = 1;
        } else {
            streak = 0;
        }
        redisTemplate.opsForValue().set(key, now + ":" + interval + ":" + streak, ttl);
        return streak;
    }

    private boolean isAssetPagePath(String path) {
        return "/api/assets".equals(path)
                || "/api/assets/page".equals(path)
                || "/api/assets/shared".equals(path);
    }

    private String assetFingerprint(String path) {
        if (path.startsWith("/api/assets/")) {
            String remainder = path.substring("/api/assets/".length());
            if (remainder.isBlank() || "page".equals(remainder) || "stats".equals(remainder) || "shared".equals(remainder)) {
                return null;
            }
            int slash = remainder.indexOf('/');
            String assetId = slash >= 0 ? remainder.substring(0, slash) : remainder;
            return assetId.isBlank() ? null : "asset:" + assetId;
        }
        if (path.startsWith("/uploads/")) {
            return "upload:" + path;
        }
        return null;
    }

    private String normalizeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "";
        }
        return userAgent.length() <= 120 ? userAgent : userAgent.substring(0, 120);
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Long.parseLong(string);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String userAgentBucket(String userAgent) {
        String normalized = userAgent == null ? "" : userAgent.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return "unknown";
        }
        if (normalized.length() > 180) {
            normalized = normalized.substring(0, 180);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ignored) {
            return Integer.toHexString(normalized.hashCode());
        }
    }

    private long parseLong(String[] parts, int index) {
        if (parts == null || parts.length <= index) {
            return 0;
        }
        try {
            return Long.parseLong(parts[index]);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean isHighValue(String method, String path) {
        if (path == null) {
            return false;
        }
        if (path.startsWith("/api/auth/captcha/")
                || path.equals("/api/auth/sign/challenge")
                || path.equals("/api/auth/public-key")
                || path.equals("/api/admin/health")) {
            return false;
        }
        return isRead(method)
                || isExport(method, path)
                || path.startsWith("/api/assets")
                || path.startsWith("/api/community")
                || path.startsWith("/api/tasks")
                || path.startsWith("/api/stats")
                || path.startsWith("/api/admin/stats")
                || path.startsWith("/uploads/");
    }

    private boolean isRead(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

    private boolean isExport(String method, String path) {
        return path != null && path.contains("/export")
                && ("GET".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method));
    }

    private boolean isInlineMediaPreview(HttpServletRequest request, String path) {
        if (request == null || path == null || !path.startsWith("/uploads/")) {
            return false;
        }
        String fetchDest = request.getHeader("Sec-Fetch-Dest");
        if (fetchDest != null) {
            String normalizedDest = fetchDest.trim().toLowerCase(Locale.ROOT);
            if ("image".equals(normalizedDest) || "video".equals(normalizedDest) || "audio".equals(normalizedDest)) {
                return true;
            }
        }
        String accept = request.getHeader("Accept");
        if (accept == null || accept.isBlank()) {
            return false;
        }
        String normalizedAccept = accept.toLowerCase(Locale.ROOT);
        return normalizedAccept.contains("image/")
                || normalizedAccept.contains("video/")
                || normalizedAccept.contains("audio/");
    }

    private String subject(HttpServletRequest request, String clientIp) {
        Object apiKeyId = request.getAttribute(SecurityRequestAttributes.API_KEY_ID);
        if (apiKeyId != null) {
            return "apiKey:" + apiKeyId;
        }
        try {
            if (StpUtil.isLogin()) {
                return "user:" + StpUtil.getLoginIdAsString();
            }
        } catch (Exception ignored) {
        }
        return clientIp == null || clientIp.isBlank() ? null : "ip:" + clientIp;
    }
}
