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
    private static final String EXPORT_REQUEST_KEY = "risk:export:req:";
    private static final String EXPORT_TARGET_KEY = "risk:export:target:";
    private static final String EXPORT_IP_USER_KEY = "risk:export:ip:user:";
    private static final String EXPORT_IP_TARGET_KEY = "risk:export:ip:target:";
    private static final String EXPORT_IP_UA_USER_KEY = "risk:export:ip-ua:user:";
    private static final String EXPORT_IP_UA_TARGET_KEY = "risk:export:ip-ua:target:";
    private static final String FIXED_INTERVAL_KEY = "risk:fixed:";
    private static final String SOURCE_KEY = "risk:source:";
    private static final String FLAG_KEY = "risk:flag:";
    private static final String ASSET_PAGE_KEY = "risk:asset:page:";
    private static final String ASSET_DETAIL_KEY = "risk:asset:detail:";
    private static final String ASSET_UNIQUE_KEY = "risk:asset:unique:";
    private static final String PUBLIC_CONTENT_PAGE_KEY = "risk:public-content:page:";
    private static final String PUBLIC_CONTENT_DETAIL_KEY = "risk:public-content:detail:";
    private static final String PUBLIC_CONTENT_COMMENT_KEY = "risk:public-content:comment:";
    private static final String PUBLIC_CONTENT_UNIQUE_KEY = "risk:public-content:unique:";
    private static final String PUBLIC_CONTENT_DISTRIBUTED_SLICE_KEY = "risk:public-content:distributed:slice:";
    private static final String UPLOAD_STEADY_KEY = "risk:upload:steady:";
    private static final String UPLOAD_STEADY_UNIQUE_KEY = "risk:upload:steady:unique:";
    private static final String UPLOAD_BURST_UNIQUE_KEY = "risk:upload:burst:unique:";
    private static final String UPLOAD_BURST_EVENT_KEY = "risk:upload:burst:event:";
    private static final String UPLOAD_BURST_EVENT_MARK_KEY = "risk:upload:burst:event:mark:";
    private static final String UPLOAD_STAGGERED_UNIQUE_KEY = "risk:upload:staggered:unique:";
    private static final String UPLOAD_STAGGERED_EVENT_KEY = "risk:upload:staggered:event:";
    private static final String UPLOAD_STAGGERED_EVENT_MARK_KEY = "risk:upload:staggered:event:mark:";
    private static final String UPLOAD_SLOW_KEY = "risk:upload:slow:";
    private static final String UPLOAD_SLOW_UNIQUE_KEY = "risk:upload:slow:unique:";
    private static final String UPLOAD_DISTRIBUTED_UNIQUE_KEY = "risk:upload:distributed:unique:";
    private static final String UPLOAD_DISTRIBUTED_PROJECT_KEY = "risk:upload:distributed:project:";
    private static final String UPLOAD_DISTRIBUTED_SLICE_KEY = "risk:upload:distributed:slice:";
    private static final String UPLOAD_IP_UA_DISTRIBUTED_USER_KEY = "risk:upload:ip-ua-distributed:user:";
    private static final String UPLOAD_IP_UA_DISTRIBUTED_UNIQUE_KEY = "risk:upload:ip-ua-distributed:unique:";
    private static final String UPLOAD_IP_UA_DISTRIBUTED_PROJECT_KEY = "risk:upload:ip-ua-distributed:project:";
    private static final String UPLOAD_IP_UA_DISTRIBUTED_SLICE_KEY = "risk:upload:ip-ua-distributed:slice:";
    private static final String UPLOAD_UA_DISTRIBUTED_IP_KEY = "risk:upload:ua-distributed:ip:";
    private static final String UPLOAD_UA_DISTRIBUTED_USER_KEY = "risk:upload:ua-distributed:user:";
    private static final String UPLOAD_UA_DISTRIBUTED_UNIQUE_KEY = "risk:upload:ua-distributed:unique:";
    private static final String UPLOAD_UA_DISTRIBUTED_PROJECT_KEY = "risk:upload:ua-distributed:project:";
    private static final String UPLOAD_UA_DISTRIBUTED_SLICE_KEY = "risk:upload:ua-distributed:slice:";
    private static final String UPLOAD_IP_FAST_USER_KEY = "risk:upload:ip-fast:user:";
    private static final String UPLOAD_IP_FAST_UNIQUE_KEY = "risk:upload:ip-fast:unique:";
    private static final String UPLOAD_IP_FAST_PROJECT_KEY = "risk:upload:ip-fast:project:";
    private static final String UPLOAD_IP_UA_FAST_USER_KEY = "risk:upload:ip-ua-fast:user:";
    private static final String UPLOAD_IP_UA_FAST_UNIQUE_KEY = "risk:upload:ip-ua-fast:unique:";
    private static final String UPLOAD_IP_UA_FAST_PROJECT_KEY = "risk:upload:ip-ua-fast:project:";
    private static final Duration ASSET_HARVEST_TTL = Duration.ofMinutes(15);
    private static final long MAX_ASSET_PAGE_SCANS = 8;
    private static final long MAX_ASSET_DETAIL_HITS = 60;
    private static final long MAX_ASSET_UNIQUE_RESOURCES = 40;
    private static final long MAX_ASSET_COMBINED_UNIQUE = 20;
    private static final Duration PUBLIC_CONTENT_HARVEST_TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    @Value("${app.security.upload-access.download.risk.fast-ip-window-seconds:10}")
    private long uploadFastIpWindowSeconds;

    @Value("${app.security.upload-access.download.risk.fast-ip-max-users:2}")
    private long uploadFastIpMaxUsers;

    @Value("${app.security.upload-access.download.risk.fast-ip-max-unique-files:15}")
    private long uploadFastIpMaxUniqueFiles;

    @Value("${app.security.upload-access.download.risk.fast-ip-min-unique-projects:2}")
    private long uploadFastIpMinUniqueProjects;

    @Value("${app.security.upload-access.download.risk.fast-ip-ua-window-seconds:10}")
    private long uploadFastIpUaWindowSeconds;

    @Value("${app.security.upload-access.download.risk.fast-ip-ua-max-users:1}")
    private long uploadFastIpUaMaxUsers;

    @Value("${app.security.upload-access.download.risk.fast-ip-ua-max-unique-files:12}")
    private long uploadFastIpUaMaxUniqueFiles;

    @Value("${app.security.upload-access.download.risk.fast-ip-ua-min-unique-projects:2}")
    private long uploadFastIpUaMinUniqueProjects;

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

    @Value("${app.security.upload-access.download.risk.staggered-window-seconds:12}")
    private long uploadStaggeredWindowSeconds;

    @Value("${app.security.upload-access.download.risk.staggered-min-unique-files-user:6}")
    private long uploadStaggeredMinUniqueFilesUser;

    @Value("${app.security.upload-access.download.risk.staggered-event-window-minutes:15}")
    private long uploadStaggeredEventWindowMinutes;

    @Value("${app.security.upload-access.download.risk.staggered-max-events-user:3}")
    private long uploadStaggeredMaxEventsUser;

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

    @Value("${app.security.upload-access.download.risk.distributed-window-minutes:60}")
    private long uploadDistributedWindowMinutes;

    @Value("${app.security.upload-access.download.risk.distributed-time-slice-minutes:5}")
    private long uploadDistributedTimeSliceMinutes;

    @Value("${app.security.upload-access.download.risk.distributed-min-active-slices-user:4}")
    private long uploadDistributedMinActiveSlicesUser;

    @Value("${app.security.upload-access.download.risk.distributed-min-unique-projects-user:3}")
    private long uploadDistributedMinUniqueProjectsUser;

    @Value("${app.security.upload-access.download.risk.distributed-min-unique-files-user:24}")
    private long uploadDistributedMinUniqueFilesUser;

    @Value("${app.security.upload-access.download.risk.distributed-multi-user-window-minutes:60}")
    private long uploadDistributedMultiUserWindowMinutes;

    @Value("${app.security.upload-access.download.risk.distributed-multi-user-time-slice-minutes:5}")
    private long uploadDistributedMultiUserTimeSliceMinutes;

    @Value("${app.security.upload-access.download.risk.distributed-multi-user-min-active-slices-ip-ua:4}")
    private long uploadDistributedMultiUserMinActiveSlicesIpUa;

    @Value("${app.security.upload-access.download.risk.distributed-multi-user-min-unique-users-ip-ua:2}")
    private long uploadDistributedMultiUserMinUniqueUsersIpUa;

    @Value("${app.security.upload-access.download.risk.distributed-multi-user-min-unique-projects-ip-ua:3}")
    private long uploadDistributedMultiUserMinUniqueProjectsIpUa;

    @Value("${app.security.upload-access.download.risk.distributed-multi-user-min-unique-files-ip-ua:24}")
    private long uploadDistributedMultiUserMinUniqueFilesIpUa;

    @Value("${app.security.upload-access.download.risk.ua-distributed-window-minutes:60}")
    private long uploadUaDistributedWindowMinutes;

    @Value("${app.security.upload-access.download.risk.ua-distributed-time-slice-minutes:5}")
    private long uploadUaDistributedTimeSliceMinutes;

    @Value("${app.security.upload-access.download.risk.ua-distributed-min-active-slices-ua:4}")
    private long uploadUaDistributedMinActiveSlicesUa;

    @Value("${app.security.upload-access.download.risk.ua-distributed-min-unique-ips-ua:3}")
    private long uploadUaDistributedMinUniqueIpsUa;

    @Value("${app.security.upload-access.download.risk.ua-distributed-min-unique-users-ua:2}")
    private long uploadUaDistributedMinUniqueUsersUa;

    @Value("${app.security.upload-access.download.risk.ua-distributed-min-unique-projects-ua:3}")
    private long uploadUaDistributedMinUniqueProjectsUa;

    @Value("${app.security.upload-access.download.risk.ua-distributed-min-unique-files-ua:24}")
    private long uploadUaDistributedMinUniqueFilesUa;

    @Value("${app.security.export-risk.subject-window-minutes:30}")
    private long exportSubjectWindowMinutes;

    @Value("${app.security.export-risk.subject-max-requests:4}")
    private long exportSubjectMaxRequests;

    @Value("${app.security.export-risk.subject-max-distinct-targets:2}")
    private long exportSubjectMaxDistinctTargets;

    @Value("${app.security.export-risk.ip-window-minutes:30}")
    private long exportIpWindowMinutes;

    @Value("${app.security.export-risk.ip-max-users:1}")
    private long exportIpMaxUsers;

    @Value("${app.security.export-risk.ip-max-distinct-targets:2}")
    private long exportIpMaxDistinctTargets;

    @Value("${app.security.export-risk.ip-ua-window-minutes:15}")
    private long exportIpUaWindowMinutes;

    @Value("${app.security.export-risk.ip-ua-max-users:1}")
    private long exportIpUaMaxUsers;

    @Value("${app.security.export-risk.ip-ua-max-distinct-targets:1}")
    private long exportIpUaMaxDistinctTargets;

    @Value("${app.security.public-content-risk.window-minutes:15}")
    private long publicContentWindowMinutes;

    @Value("${app.security.public-content-risk.max-page-scans:18}")
    private long publicContentMaxPageScans;

    @Value("${app.security.public-content-risk.max-detail-hits:45}")
    private long publicContentMaxDetailHits;

    @Value("${app.security.public-content-risk.max-comment-hits:20}")
    private long publicContentMaxCommentHits;

    @Value("${app.security.public-content-risk.max-unique-targets:30}")
    private long publicContentMaxUniqueTargets;

    @Value("${app.security.public-content-risk.min-combined-unique-targets:12}")
    private long publicContentMinCombinedUniqueTargets;

    @Value("${app.security.public-content-risk.distributed-window-minutes:60}")
    private long publicContentDistributedWindowMinutes;

    @Value("${app.security.public-content-risk.distributed-time-slice-minutes:5}")
    private long publicContentDistributedTimeSliceMinutes;

    @Value("${app.security.public-content-risk.distributed-min-active-slices:4}")
    private long publicContentDistributedMinActiveSlices;

    @Value("${app.security.public-content-risk.distributed-min-page-scans:8}")
    private long publicContentDistributedMinPageScans;

    @Value("${app.security.public-content-risk.distributed-min-unique-targets:24}")
    private long publicContentDistributedMinUniqueTargets;

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

    public String inspectUploadRequest(HttpServletRequest request, String clientIp) {
        if (request == null) {
            return null;
        }
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (!isRead(method) || path == null || !path.startsWith("/uploads/")) {
            return null;
        }
        String subject = subject(request, clientIp);
        if (subject == null) {
            return null;
        }
        if (isInlineMediaPreview(request, path)) {
            return null;
        }
        try {
            String before = redisTemplate.opsForValue().get(FLAG_KEY + subject);
            detectAssetHarvest(request, subject, clientIp, path, method);
            String after = redisTemplate.opsForValue().get(FLAG_KEY + subject);
            if (after == null || after.isBlank()) {
                return null;
            }
            if (before != null && before.equals(after)) {
                return null;
            }
            return after;
        } catch (Exception ignored) {
            return null;
        }
    }

    public String inspectExportRequest(HttpServletRequest request, String clientIp) {
        if (request == null) {
            return null;
        }
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (!isExport(method, path)) {
            return null;
        }
        String subject = subject(request, clientIp);
        if (subject == null) {
            return null;
        }
        try {
            String before = redisTemplate.opsForValue().get(FLAG_KEY + subject);
            detectExportHarvest(request, subject, clientIp);
            String after = redisTemplate.opsForValue().get(FLAG_KEY + subject);
            if (after == null || after.isBlank()) {
                return null;
            }
            if (before != null && before.equals(after)) {
                return null;
            }
            return after;
        } catch (Exception ignored) {
            return null;
        }
    }

    public String inspectPublicContentRequest(HttpServletRequest request, String clientIp) {
        if (request == null) {
            return null;
        }
        String path = request.getRequestURI();
        String method = request.getMethod();
        if (!isRead(method) || !isPublicContentPath(path)) {
            return null;
        }
        String subject = subject(request, clientIp);
        if (subject == null) {
            return null;
        }
        try {
            String before = redisTemplate.opsForValue().get(FLAG_KEY + subject);
            detectPublicContentHarvest(subject, path);
            String after = redisTemplate.opsForValue().get(FLAG_KEY + subject);
            if (after == null || after.isBlank()) {
                return null;
            }
            if (before != null && before.equals(after)) {
                return null;
            }
            return after;
        } catch (Exception ignored) {
            return null;
        }
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
            if (path == null || !path.startsWith("/uploads/")) {
                detectAssetHarvest(request, subject, clientIp, path, method);
            }
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

    private void detectPublicContentHarvest(String subject, String path) {
        if (subject == null || path == null) {
            return;
        }
        Duration ttl = Duration.ofMinutes(Math.max(publicContentWindowMinutes, 1));
        long pageScans = isPublicContentPagePath(path)
                ? increment(PUBLIC_CONTENT_PAGE_KEY + subject, ttl)
                : readCounter(PUBLIC_CONTENT_PAGE_KEY + subject);
        long detailHits = readCounter(PUBLIC_CONTENT_DETAIL_KEY + subject);
        long commentHits = readCounter(PUBLIC_CONTENT_COMMENT_KEY + subject);
        long uniqueCount = readSetSize(PUBLIC_CONTENT_UNIQUE_KEY + subject);

        String fingerprint = publicContentFingerprint(path);
        if (fingerprint != null) {
            if (isPublicContentCommentPath(path)) {
                commentHits = increment(PUBLIC_CONTENT_COMMENT_KEY + subject, ttl);
            } else {
                detailHits = increment(PUBLIC_CONTENT_DETAIL_KEY + subject, ttl);
            }
            uniqueCount = distinctCount(PUBLIC_CONTENT_UNIQUE_KEY + subject, fingerprint, ttl);
        }

        if (uniqueCount > publicContentMaxUniqueTargets) {
            flag(subject, "15 分钟内持续遍历大量不同公共内容目标");
            return;
        }
        if (pageScans > publicContentMaxPageScans
                && uniqueCount >= publicContentMinCombinedUniqueTargets) {
            flag(subject, "检测到公共内容分页扫库并伴随详情/评论遍历");
            return;
        }
        if (detailHits > publicContentMaxDetailHits
                && uniqueCount >= publicContentMinCombinedUniqueTargets) {
            flag(subject, "检测到公共内容详情批量遍历");
            return;
        }
        if (commentHits > publicContentMaxCommentHits
                && uniqueCount >= publicContentMinCombinedUniqueTargets) {
            flag(subject, "检测到公共内容评论树批量遍历");
            return;
        }

        Duration distributedTtl = Duration.ofMinutes(Math.max(publicContentDistributedWindowMinutes, 1));
        long sliceMinutes = Math.max(publicContentDistributedTimeSliceMinutes, 1);
        long activeSlices = distinctCount(
                PUBLIC_CONTENT_DISTRIBUTED_SLICE_KEY + subject,
                String.valueOf(System.currentTimeMillis() / (sliceMinutes * 60_000L)),
                distributedTtl
        );
        if (activeSlices >= publicContentDistributedMinActiveSlices
                && pageScans >= publicContentDistributedMinPageScans
                && uniqueCount >= publicContentDistributedMinUniqueTargets) {
            flag(subject, "检测到跨时段慢速公共内容采集");
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

            Duration staggeredTtl = Duration.ofSeconds(Math.max(uploadStaggeredWindowSeconds, 1));
            long staggeredUniqueCount = distinctCount(UPLOAD_STAGGERED_UNIQUE_KEY + subject, fingerprint, staggeredTtl);
            if (staggeredUniqueCount >= uploadStaggeredMinUniqueFilesUser) {
                Boolean firstStaggeredInWindow = redisTemplate.opsForValue().setIfAbsent(
                        UPLOAD_STAGGERED_EVENT_MARK_KEY + subject,
                        "1",
                        staggeredTtl
                );
                if (Boolean.TRUE.equals(firstStaggeredInWindow)) {
                    long staggeredEvents = increment(
                            UPLOAD_STAGGERED_EVENT_KEY + subject,
                            Duration.ofMinutes(Math.max(uploadStaggeredEventWindowMinutes, 1))
                    );
                    if (staggeredEvents >= uploadStaggeredMaxEventsUser) {
                        flag(subject, "检测到分段式小批量下载行为");
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

            Long subjectProjectId = asLong(request.getAttribute(SecurityRequestAttributes.UPLOAD_PROJECT_ID));
            if (subjectProjectId != null && subjectProjectId > 0) {
                Duration distributedTtl = Duration.ofMinutes(Math.max(uploadDistributedWindowMinutes, 1));
                long sliceMinutes = Math.max(uploadDistributedTimeSliceMinutes, 1);
                long activeSlices = distinctCount(
                        UPLOAD_DISTRIBUTED_SLICE_KEY + subject,
                        String.valueOf(System.currentTimeMillis() / (sliceMinutes * 60_000L)),
                        distributedTtl
                );
                long distributedProjects = distinctCount(
                        UPLOAD_DISTRIBUTED_PROJECT_KEY + subject,
                        String.valueOf(subjectProjectId),
                        distributedTtl
                );
                long distributedFiles = distinctCount(
                        UPLOAD_DISTRIBUTED_UNIQUE_KEY + subject,
                        fingerprint,
                        distributedTtl
                );
                if (activeSlices >= uploadDistributedMinActiveSlicesUser
                        && distributedProjects >= uploadDistributedMinUniqueProjectsUser
                        && distributedFiles >= uploadDistributedMinUniqueFilesUser) {
                    flag(subject, "检测到跨时段、跨项目的低速分布式资源搬运");
                    return;
                }
            }
        }

        Long signedUserId = asLong(request.getAttribute(SecurityRequestAttributes.UPLOAD_SIGNED_USER_ID));
        if (signedUserId == null || signedUserId <= 0 || clientIp == null || clientIp.isBlank()) {
            return;
        }

        Duration ipFastTtl = Duration.ofSeconds(Math.max(uploadFastIpWindowSeconds, 1));
        long ipFastUsers = distinctCount(UPLOAD_IP_FAST_USER_KEY + clientIp, String.valueOf(signedUserId), ipFastTtl);
        long ipFastFiles = distinctCount(UPLOAD_IP_FAST_UNIQUE_KEY + clientIp, fingerprint, ipFastTtl);
        Long projectId = asLong(request.getAttribute(SecurityRequestAttributes.UPLOAD_PROJECT_ID));
        long ipFastProjects = projectId != null && projectId > 0
                ? distinctCount(UPLOAD_IP_FAST_PROJECT_KEY + clientIp, String.valueOf(projectId), ipFastTtl)
                : 0;
        if (ipFastUsers > uploadFastIpMaxUsers
                && ipFastFiles > uploadFastIpMaxUniqueFiles
                && ipFastProjects >= uploadFastIpMinUniqueProjects) {
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
        long ipUaFastProjects = projectId != null && projectId > 0
                ? distinctCount(
                UPLOAD_IP_UA_FAST_PROJECT_KEY + clientIp + ":" + uaBucket,
                String.valueOf(projectId),
                ipUaFastTtl
        )
                : 0;
        if (ipUaFastUsers > uploadFastIpUaMaxUsers
                && ipUaFastFiles > uploadFastIpUaMaxUniqueFiles
                && ipUaFastProjects >= uploadFastIpUaMinUniqueProjects) {
            flag(subject, "10 秒内同一 IP/UA 跨多个账号访问不同资源超过 " + uploadFastIpUaMaxUniqueFiles + " 个");
            return;
        }

        if (projectId != null && projectId > 0) {
            Duration ipUaDistributedTtl = Duration.ofMinutes(Math.max(uploadDistributedMultiUserWindowMinutes, 1));
            long sliceMinutes = Math.max(uploadDistributedMultiUserTimeSliceMinutes, 1);
            String ipUaPrefix = clientIp + ":" + uaBucket;
            long ipUaDistributedUsers = distinctCount(
                    UPLOAD_IP_UA_DISTRIBUTED_USER_KEY + ipUaPrefix,
                    String.valueOf(signedUserId),
                    ipUaDistributedTtl
            );
            long ipUaDistributedProjects = distinctCount(
                    UPLOAD_IP_UA_DISTRIBUTED_PROJECT_KEY + ipUaPrefix,
                    String.valueOf(projectId),
                    ipUaDistributedTtl
            );
            long ipUaDistributedFiles = distinctCount(
                    UPLOAD_IP_UA_DISTRIBUTED_UNIQUE_KEY + ipUaPrefix,
                    fingerprint,
                    ipUaDistributedTtl
            );
            long ipUaDistributedSlices = distinctCount(
                    UPLOAD_IP_UA_DISTRIBUTED_SLICE_KEY + ipUaPrefix,
                    String.valueOf(System.currentTimeMillis() / (sliceMinutes * 60_000L)),
                    ipUaDistributedTtl
            );
            if (ipUaDistributedUsers >= uploadDistributedMultiUserMinUniqueUsersIpUa
                    && ipUaDistributedProjects >= uploadDistributedMultiUserMinUniqueProjectsIpUa
                    && ipUaDistributedFiles >= uploadDistributedMultiUserMinUniqueFilesIpUa
                    && ipUaDistributedSlices >= uploadDistributedMultiUserMinActiveSlicesIpUa) {
                flag(subject, "检测到同一 IP/UA 下多账号跨时段慢速协同搬运");
                return;
            }

            Duration uaDistributedTtl = Duration.ofMinutes(Math.max(uploadUaDistributedWindowMinutes, 1));
            long uaSliceMinutes = Math.max(uploadUaDistributedTimeSliceMinutes, 1);
            long uaDistributedIps = distinctCount(
                    UPLOAD_UA_DISTRIBUTED_IP_KEY + uaBucket,
                    clientIp,
                    uaDistributedTtl
            );
            long uaDistributedUsers = distinctCount(
                    UPLOAD_UA_DISTRIBUTED_USER_KEY + uaBucket,
                    String.valueOf(signedUserId),
                    uaDistributedTtl
            );
            long uaDistributedProjects = distinctCount(
                    UPLOAD_UA_DISTRIBUTED_PROJECT_KEY + uaBucket,
                    String.valueOf(projectId),
                    uaDistributedTtl
            );
            long uaDistributedFiles = distinctCount(
                    UPLOAD_UA_DISTRIBUTED_UNIQUE_KEY + uaBucket,
                    fingerprint,
                    uaDistributedTtl
            );
            long uaDistributedSlices = distinctCount(
                    UPLOAD_UA_DISTRIBUTED_SLICE_KEY + uaBucket,
                    String.valueOf(System.currentTimeMillis() / (uaSliceMinutes * 60_000L)),
                    uaDistributedTtl
            );
            if (uaDistributedIps >= uploadUaDistributedMinUniqueIpsUa
                    && uaDistributedUsers >= uploadUaDistributedMinUniqueUsersUa
                    && uaDistributedProjects >= uploadUaDistributedMinUniqueProjectsUa
                    && uaDistributedFiles >= uploadUaDistributedMinUniqueFilesUa
                    && uaDistributedSlices >= uploadUaDistributedMinActiveSlicesUa) {
                flag(subject, "检测到跨 IP 池分散协同搬运");
            }
        }
    }

    private void detectExportHarvest(HttpServletRequest request, String subject, String clientIp) {
        String target = exportFingerprint(request);
        if (target == null) {
            return;
        }

        Duration subjectTtl = Duration.ofMinutes(Math.max(exportSubjectWindowMinutes, 1));
        long subjectRequests = increment(EXPORT_REQUEST_KEY + subject, subjectTtl);
        long subjectTargets = distinctCount(EXPORT_TARGET_KEY + subject, target, subjectTtl);
        if (subjectRequests > exportSubjectMaxRequests) {
            flag(subject, Math.max(exportSubjectWindowMinutes, 1) + " 分钟内连续导出超过 " + exportSubjectMaxRequests + " 次");
            return;
        }
        if (subjectTargets > exportSubjectMaxDistinctTargets && subjectRequests > exportSubjectMaxDistinctTargets) {
            flag(subject, Math.max(exportSubjectWindowMinutes, 1) + " 分钟内连续导出多个不同目标");
            return;
        }

        if (clientIp == null || clientIp.isBlank()) {
            return;
        }

        Duration ipTtl = Duration.ofMinutes(Math.max(exportIpWindowMinutes, 1));
        long ipUsers = distinctCount(EXPORT_IP_USER_KEY + clientIp, subject, ipTtl);
        long ipTargets = distinctCount(EXPORT_IP_TARGET_KEY + clientIp, target, ipTtl);
        if (ipUsers > exportIpMaxUsers && ipTargets > exportIpMaxDistinctTargets) {
            flag(subject, Math.max(exportIpWindowMinutes, 1) + " 分钟内同一 IP 下多个账号连续导出多个目标");
            return;
        }

        String uaBucket = userAgentBucket(request.getHeader("User-Agent"));
        Duration ipUaTtl = Duration.ofMinutes(Math.max(exportIpUaWindowMinutes, 1));
        long ipUaUsers = distinctCount(EXPORT_IP_UA_USER_KEY + clientIp + ":" + uaBucket, subject, ipUaTtl);
        long ipUaTargets = distinctCount(EXPORT_IP_UA_TARGET_KEY + clientIp + ":" + uaBucket, target, ipUaTtl);
        if (ipUaUsers > exportIpUaMaxUsers && ipUaTargets > exportIpUaMaxDistinctTargets) {
            flag(subject, Math.max(exportIpUaWindowMinutes, 1) + " 分钟内同一 IP/UA 下多个账号连续导出多个目标");
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

    private boolean isPublicContentPath(String path) {
        if (path == null) {
            return false;
        }
        return "/api/community/posts".equals(path)
                || "/api/community/categories".equals(path)
                || "/api/prompt-templates".equals(path)
                || "/api/style-cards".equals(path)
                || path.matches("^/api/community/posts/\\d+(/comments)?$")
                || path.matches("^/api/prompt-templates/\\d+(/comments)?$")
                || path.matches("^/api/style-cards/\\d+(/comments)?$");
    }

    private boolean isPublicContentPagePath(String path) {
        return "/api/community/posts".equals(path)
                || "/api/community/categories".equals(path)
                || "/api/prompt-templates".equals(path)
                || "/api/style-cards".equals(path);
    }

    private boolean isPublicContentCommentPath(String path) {
        return path != null
                && (path.matches("^/api/community/posts/\\d+/comments$")
                || path.matches("^/api/prompt-templates/\\d+/comments$")
                || path.matches("^/api/style-cards/\\d+/comments$"));
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

    private String publicContentFingerprint(String path) {
        if (path == null) {
            return null;
        }
        if (path.matches("^/api/community/posts/\\d+$")) {
            return "community:detail:" + trailingNumericSegment(path);
        }
        if (path.matches("^/api/community/posts/\\d+/comments$")) {
            return "community:comments:" + pathSegmentAt(path, 4);
        }
        if (path.matches("^/api/prompt-templates/\\d+$")) {
            return "prompt-template:detail:" + trailingNumericSegment(path);
        }
        if (path.matches("^/api/prompt-templates/\\d+/comments$")) {
            return "prompt-template:comments:" + pathSegmentAt(path, 3);
        }
        if (path.matches("^/api/style-cards/\\d+$")) {
            return "style-card:detail:" + trailingNumericSegment(path);
        }
        if (path.matches("^/api/style-cards/\\d+/comments$")) {
            return "style-card:comments:" + pathSegmentAt(path, 3);
        }
        return null;
    }

    private String trailingNumericSegment(String path) {
        int slash = path.lastIndexOf('/');
        if (slash < 0 || slash == path.length() - 1) {
            return null;
        }
        String segment = path.substring(slash + 1);
        return segment.chars().allMatch(Character::isDigit) ? segment : null;
    }

    private String pathSegmentAt(String path, int index) {
        String[] parts = path.split("/");
        if (parts.length <= index) {
            return null;
        }
        String segment = parts[index];
        return segment.chars().allMatch(Character::isDigit) ? segment : null;
    }

    private String exportFingerprint(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String path = request.getRequestURI();
        if (path == null || !path.contains("/export")) {
            return null;
        }
        if (path.startsWith("/api/projects/") && path.endsWith("/export")) {
            String remainder = path.substring("/api/projects/".length(), path.length() - "/export".length());
            return remainder.isBlank() ? "export:/api/projects" : "project:" + remainder;
        }
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return "export:" + path;
        }
        return "export:" + path + "?" + hashKey(query);
    }

    private String hashKey(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String normalized = input.length() <= 180 ? input : input.substring(0, 180);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ignored) {
            return Integer.toHexString(normalized.hashCode());
        }
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
        Long signedUserId = asLong(request.getAttribute(SecurityRequestAttributes.UPLOAD_SIGNED_USER_ID));
        if (signedUserId != null && signedUserId > 0) {
            return "user:" + signedUserId;
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
