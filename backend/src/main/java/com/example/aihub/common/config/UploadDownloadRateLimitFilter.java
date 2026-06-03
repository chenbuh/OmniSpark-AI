package com.example.aihub.common.config;

import cn.dev33.satoken.stp.StpUtil;
import com.example.aihub.common.security.ClientIpResolver;
import com.example.aihub.common.security.SecurityRequestAttributes;
import com.example.aihub.infrastructure.service.AntiCrawlerRiskService;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

@Component
@Order(2)
@RequiredArgsConstructor
public class UploadDownloadRateLimitFilter implements Filter {
    private static final String KEY_PREFIX = "rate:uploads:";

    private final ClientIpResolver clientIpResolver;
    private final StringRedisTemplate redisTemplate;
    private final AntiCrawlerRiskService antiCrawlerRiskService;

    @Value("${app.security.upload-access.download.max-per-minute-user:80}")
    private int maxDownloadsPerMinuteUser;

    @Value("${app.security.upload-access.download.max-per-minute-ip:120}")
    private int maxDownloadsPerMinuteIp;

    @Value("${app.security.upload-access.download.max-unique-files-5m-user:45}")
    private int maxUniqueFilesPerFiveMinutesUser;

    @Value("${app.security.upload-access.download.max-unique-files-10m-project-user:25}")
    private int maxUniqueFilesPerTenMinutesProjectUser;

    @Value("${app.security.upload-access.download.max-unique-projects-15m-user:3}")
    private int maxUniqueProjectsPerFifteenMinutesUser;

    @Value("${app.security.upload-access.download.max-unique-files-15m-multi-project-user:35}")
    private int maxUniqueFilesPerFifteenMinutesMultiProjectUser;

    @Value("${app.security.upload-access.download.max-unique-users-15m-ip:2}")
    private int maxUniqueUsersPerFifteenMinutesIp;

    @Value("${app.security.upload-access.download.max-unique-files-15m-ip-multi-user:30}")
    private int maxUniqueFilesPerFifteenMinutesIpMultiUser;

    @Value("${app.security.upload-access.download.max-unique-users-2m-ip-burst:1}")
    private int maxUniqueUsersPerTwoMinutesIpBurst;

    @Value("${app.security.upload-access.download.max-unique-files-2m-ip-burst:10}")
    private int maxUniqueFilesPerTwoMinutesIpBurst;

    @Value("${app.security.upload-access.download.max-unique-users-10m-ip-ua:1}")
    private int maxUniqueUsersPerTenMinutesIpUa;

    @Value("${app.security.upload-access.download.max-unique-files-10m-ip-ua-multi-user:24}")
    private int maxUniqueFilesPerTenMinutesIpUaMultiUser;

    @Value("${app.security.upload-access.download.max-unique-users-2m-ip-ua-burst:1}")
    private int maxUniqueUsersPerTwoMinutesIpUaBurst;

    @Value("${app.security.upload-access.download.max-unique-files-2m-ip-ua-burst:8}")
    private int maxUniqueFilesPerTwoMinutesIpUaBurst;

    @Value("${app.security.upload-access.download.block-minutes:30}")
    private long blockMinutes;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        String path = req.getRequestURI();
        if (path == null || !path.startsWith("/uploads/")) {
            chain.doFilter(request, response);
            return;
        }

        String clientIp = clientIpResolver.resolve(req);
        try {
            String existingRisk = antiCrawlerRiskService.currentRiskReason(req, clientIp);
            if (existingRisk != null && !existingRisk.isBlank()) {
                req.setAttribute(SecurityRequestAttributes.RISK_REASON, existingRisk);
                writeTooManyRequests((HttpServletResponse) response, existingRisk);
                return;
            }

            String subject = resolveSubject(clientIp);
            int maxDownloads = subject.startsWith("user:") ? maxDownloadsPerMinuteUser : maxDownloadsPerMinuteIp;
            String minuteKey = KEY_PREFIX + "minute:" + subject + ":" + (System.currentTimeMillis() / 60_000);
            Long current = redisTemplate.opsForValue().increment(minuteKey);
            if (current != null && current == 1L) {
                redisTemplate.expire(minuteKey, Duration.ofMinutes(2));
            }
            if (current != null && current > maxDownloads) {
                block(req, (HttpServletResponse) response, clientIp,
                        "1 分钟内下载资源超过 " + maxDownloads + " 次");
                return;
            }

            if (subject.startsWith("user:")) {
                String normalizedPath = stripQuery(path);
                String uniqueKey = KEY_PREFIX + "unique:" + subject;
                redisTemplate.opsForSet().add(uniqueKey, normalizedPath);
                redisTemplate.expire(uniqueKey, Duration.ofMinutes(5));
                Long uniqueCount = redisTemplate.opsForSet().size(uniqueKey);
                if (uniqueCount != null && uniqueCount > maxUniqueFilesPerFiveMinutesUser) {
                    block(req, (HttpServletResponse) response, clientIp,
                            "5 分钟内访问不同资源超过 " + maxUniqueFilesPerFiveMinutesUser + " 个");
                    return;
                }

                Long projectId = asLong(req.getAttribute(SecurityRequestAttributes.UPLOAD_PROJECT_ID));
                if (projectId != null && projectId > 0) {
                    String projectUniqueKey = KEY_PREFIX + "project-unique:" + subject + ":" + projectId;
                    redisTemplate.opsForSet().add(projectUniqueKey, normalizedPath);
                    redisTemplate.expire(projectUniqueKey, Duration.ofMinutes(10));
                    Long projectUniqueCount = redisTemplate.opsForSet().size(projectUniqueKey);
                    if (projectUniqueCount != null && projectUniqueCount > maxUniqueFilesPerTenMinutesProjectUser) {
                        block(req, (HttpServletResponse) response, clientIp,
                                "10 分钟内访问同一项目不同资源超过 " + maxUniqueFilesPerTenMinutesProjectUser + " 个");
                        return;
                    }

                    String projectSpanKey = KEY_PREFIX + "project-span:" + subject;
                    redisTemplate.opsForSet().add(projectSpanKey, String.valueOf(projectId));
                    redisTemplate.expire(projectSpanKey, Duration.ofMinutes(15));
                    Long projectSpanCount = redisTemplate.opsForSet().size(projectSpanKey);

                    String multiProjectUniqueKey = KEY_PREFIX + "multi-project-unique:" + subject;
                    redisTemplate.opsForSet().add(multiProjectUniqueKey, normalizedPath);
                    redisTemplate.expire(multiProjectUniqueKey, Duration.ofMinutes(15));
                    Long multiProjectUniqueCount = redisTemplate.opsForSet().size(multiProjectUniqueKey);
                    if (projectSpanCount != null && multiProjectUniqueCount != null
                            && projectSpanCount > maxUniqueProjectsPerFifteenMinutesUser
                            && multiProjectUniqueCount > maxUniqueFilesPerFifteenMinutesMultiProjectUser) {
                        block(req, (HttpServletResponse) response, clientIp,
                                "15 分钟内跨多个项目访问不同资源超过 " + maxUniqueFilesPerFifteenMinutesMultiProjectUser + " 个");
                        return;
                    }
                }
            }

            Long signedUserId = asLong(req.getAttribute(SecurityRequestAttributes.UPLOAD_SIGNED_USER_ID));
            if (signedUserId != null && signedUserId > 0 && clientIp != null && !clientIp.isBlank()) {
                String normalizedPath = stripQuery(path);
                String ipUserSpanKey = KEY_PREFIX + "ip-user-span:" + clientIp;
                redisTemplate.opsForSet().add(ipUserSpanKey, String.valueOf(signedUserId));
                redisTemplate.expire(ipUserSpanKey, Duration.ofMinutes(15));
                Long ipUserSpanCount = redisTemplate.opsForSet().size(ipUserSpanKey);

                String ipUniqueKey = KEY_PREFIX + "ip-multi-user-unique:" + clientIp;
                redisTemplate.opsForSet().add(ipUniqueKey, normalizedPath);
                redisTemplate.expire(ipUniqueKey, Duration.ofMinutes(15));
                Long ipUniqueCount = redisTemplate.opsForSet().size(ipUniqueKey);

                String ipBurstUserSpanKey = KEY_PREFIX + "ip-burst-user-span:" + clientIp;
                redisTemplate.opsForSet().add(ipBurstUserSpanKey, String.valueOf(signedUserId));
                redisTemplate.expire(ipBurstUserSpanKey, Duration.ofMinutes(2));
                Long ipBurstUserSpanCount = redisTemplate.opsForSet().size(ipBurstUserSpanKey);

                String ipBurstUniqueKey = KEY_PREFIX + "ip-burst-unique:" + clientIp;
                redisTemplate.opsForSet().add(ipBurstUniqueKey, normalizedPath);
                redisTemplate.expire(ipBurstUniqueKey, Duration.ofMinutes(2));
                Long ipBurstUniqueCount = redisTemplate.opsForSet().size(ipBurstUniqueKey);
                if (ipUserSpanCount != null && ipUniqueCount != null
                        && ipBurstUserSpanCount != null && ipBurstUniqueCount != null
                        && ipUserSpanCount > maxUniqueUsersPerFifteenMinutesIp
                        && ipUniqueCount > maxUniqueFilesPerFifteenMinutesIpMultiUser
                        && ipBurstUserSpanCount > maxUniqueUsersPerTwoMinutesIpBurst
                        && ipBurstUniqueCount > maxUniqueFilesPerTwoMinutesIpBurst) {
                        block(req, (HttpServletResponse) response, clientIp,
                                "15 分钟内同一 IP 跨多个账号访问不同资源超过 "
                                        + maxUniqueFilesPerFifteenMinutesIpMultiUser
                                        + " 个，且近 2 分钟仍在持续突发下载");
                        return;
                }

                String uaBucket = userAgentBucket(req.getHeader("User-Agent"));
                String ipUaUserSpanKey = KEY_PREFIX + "ip-ua-user-span:" + clientIp + ":" + uaBucket;
                redisTemplate.opsForSet().add(ipUaUserSpanKey, String.valueOf(signedUserId));
                redisTemplate.expire(ipUaUserSpanKey, Duration.ofMinutes(10));
                Long ipUaUserSpanCount = redisTemplate.opsForSet().size(ipUaUserSpanKey);

                String ipUaUniqueKey = KEY_PREFIX + "ip-ua-multi-user-unique:" + clientIp + ":" + uaBucket;
                redisTemplate.opsForSet().add(ipUaUniqueKey, normalizedPath);
                redisTemplate.expire(ipUaUniqueKey, Duration.ofMinutes(10));
                Long ipUaUniqueCount = redisTemplate.opsForSet().size(ipUaUniqueKey);

                String ipUaBurstUserSpanKey = KEY_PREFIX + "ip-ua-burst-user-span:" + clientIp + ":" + uaBucket;
                redisTemplate.opsForSet().add(ipUaBurstUserSpanKey, String.valueOf(signedUserId));
                redisTemplate.expire(ipUaBurstUserSpanKey, Duration.ofMinutes(2));
                Long ipUaBurstUserSpanCount = redisTemplate.opsForSet().size(ipUaBurstUserSpanKey);

                String ipUaBurstUniqueKey = KEY_PREFIX + "ip-ua-burst-unique:" + clientIp + ":" + uaBucket;
                redisTemplate.opsForSet().add(ipUaBurstUniqueKey, normalizedPath);
                redisTemplate.expire(ipUaBurstUniqueKey, Duration.ofMinutes(2));
                Long ipUaBurstUniqueCount = redisTemplate.opsForSet().size(ipUaBurstUniqueKey);
                if (ipUaUserSpanCount != null && ipUaUniqueCount != null
                        && ipUaBurstUserSpanCount != null && ipUaBurstUniqueCount != null
                        && ipUaUserSpanCount > maxUniqueUsersPerTenMinutesIpUa
                        && ipUaUniqueCount > maxUniqueFilesPerTenMinutesIpUaMultiUser
                        && ipUaBurstUserSpanCount > maxUniqueUsersPerTwoMinutesIpUaBurst
                        && ipUaBurstUniqueCount > maxUniqueFilesPerTwoMinutesIpUaBurst) {
                        block(req, (HttpServletResponse) response, clientIp,
                                "10 分钟内同一 IP/UA 跨多个账号访问不同资源超过 "
                                        + maxUniqueFilesPerTenMinutesIpUaMultiUser
                                        + " 个，且近 2 分钟仍在持续突发下载");
                        return;
                }
            }
        } catch (Exception ignored) {
            // Redis 不可用时放行，避免静态资源完全不可访问。
        }

        chain.doFilter(request, response);
    }

    private void block(HttpServletRequest request, HttpServletResponse response, String clientIp, String reason) throws IOException {
        request.setAttribute(SecurityRequestAttributes.RATE_LIMIT_HIT, Boolean.TRUE);
        request.setAttribute(SecurityRequestAttributes.RISK_REASON, reason);
        antiCrawlerRiskService.flag(request, clientIp, reason, Duration.ofMinutes(Math.max(blockMinutes, 1)));
        writeTooManyRequests(response, reason);
    }

    private String resolveSubject(String clientIp) {
        try {
            if (StpUtil.isLogin()) {
                return "user:" + StpUtil.getLoginIdAsString();
            }
        } catch (Exception ignored) {
        }
        return "ip:" + (clientIp == null ? "unknown" : clientIp);
    }

    private String stripQuery(String path) {
        int queryIndex = path.indexOf('?');
        return queryIndex >= 0 ? path.substring(0, queryIndex) : path;
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Long.valueOf(string);
            } catch (NumberFormatException ignored) {
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

    private void writeTooManyRequests(HttpServletResponse response, String reason) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        String message = reason == null || reason.isBlank()
                ? "资产下载过于频繁，请稍后再试"
                : reason.replace("\"", "'");
        response.getWriter().write("{\"code\":429,\"message\":\"" + message + "，已临时限制资源访问\",\"data\":null}");
        response.getWriter().flush();
    }
}
