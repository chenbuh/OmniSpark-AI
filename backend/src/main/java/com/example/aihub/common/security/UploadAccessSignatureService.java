package com.example.aihub.common.security;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

@Service
public class UploadAccessSignatureService {
    private static final Logger log = LoggerFactory.getLogger(UploadAccessSignatureService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    public static final String MODE_AUTHENTICATED = "auth";
    public static final String MODE_PROJECT = "project";

    @Value("${app.security.upload-access.secret:omnispark-dev-upload-access-secret-2026}")
    private String secret;

    @Value("${app.security.upload-access.ttl-seconds:7200}")
    private long ttlSeconds;

    @PostConstruct
    public void validateConfig() {
        if ("omnispark-dev-upload-access-secret-2026".equals(secret)) {
            log.warn("⚠ 上传访问签名密钥使用了不安全的默认值（omnispark-dev-upload-access-secret-2026），"
                    + "生产环境请通过环境变量 APP_UPLOAD_ACCESS_SECRET 设置一个强随机密钥！");
        } else if (secret.length() < 16) {
            log.warn("⚠ 上传访问签名密钥长度（{}）不足 16 个字符，建议使用更长的密钥", secret.length());
        } else {
            log.info("上传访问签名密钥已配置");
        }
    }

    public String signUrl(String rawUrl) {
        return signAuthenticatedUrl(rawUrl);
    }

    public String signAuthenticatedUrl(String rawUrl) {
        return signUrl(rawUrl, MODE_AUTHENTICATED, null, currentUserId());
    }

    public String signProjectUrl(String rawUrl, Long projectId) {
        return signProjectUrl(rawUrl, projectId, currentUserId());
    }

    public String signProjectUrl(String rawUrl, Long projectId, Long userId) {
        if (projectId == null || projectId <= 0) {
            return signUrl(rawUrl, MODE_AUTHENTICATED, null, userId);
        }
        return signUrl(rawUrl, MODE_PROJECT, projectId, userId);
    }

    private String signUrl(String rawUrl, String mode, Long projectId, Long userId) {
        if (!isLocalUploadUrl(rawUrl)) {
            return rawUrl;
        }
        String path = normalizePath(rawUrl);
        long expiresAt = Instant.now().getEpochSecond() + Math.max(ttlSeconds, 60);
        String normalizedMode = normalizeMode(mode);
        String projectPart = MODE_PROJECT.equals(normalizedMode) && projectId != null && projectId > 0
                ? "&pid=" + projectId
                : "";
        String userPart = userId != null ? "&uid=" + userId : "";
        String signature = sign(path, expiresAt, normalizedMode, projectId, userId);
        return path + "?exp=" + expiresAt + "&mode=" + normalizedMode + projectPart + userPart + "&sig=" + signature;
    }

    public AccessDescriptor verify(String requestPath, String expiresAt, String signature, String mode, String projectId, String userId) {
        if (!isLocalUploadUrl(requestPath) || isBlank(expiresAt) || isBlank(signature) || isBlank(mode)) {
            return AccessDescriptor.invalid();
        }
        long exp;
        try {
            exp = Long.parseLong(expiresAt);
        } catch (NumberFormatException ex) {
            return AccessDescriptor.invalid();
        }
        if (exp < Instant.now().getEpochSecond()) {
            return AccessDescriptor.invalid();
        }

        String normalizedMode = normalizeMode(mode);
        if (normalizedMode == null) {
            return AccessDescriptor.invalid();
        }
        Long parsedProjectId = parseProjectId(normalizedMode, projectId);
        if (MODE_PROJECT.equals(normalizedMode) && parsedProjectId == null) {
            return AccessDescriptor.invalid();
        }
        Long parsedUserId = parseUserId(userId);
        String normalizedPath = normalizePath(requestPath);

        if (parsedUserId != null) {
            String expected = sign(normalizedPath, exp, normalizedMode, parsedProjectId, parsedUserId);
            if (!constantTimeEquals(expected, signature)) {
                return AccessDescriptor.invalid();
            }
            return new AccessDescriptor(true, normalizedMode, parsedProjectId, parsedUserId);
        }

        // 兼容未携带 uid 的旧签名，等待旧链接在短 TTL 内自然淘汰。
        String legacyExpected = sign(normalizedPath, exp, normalizedMode, parsedProjectId, null);
        if (!constantTimeEquals(legacyExpected, signature)) {
            return AccessDescriptor.invalid();
        }
        return new AccessDescriptor(true, normalizedMode, parsedProjectId, null);
    }

    public boolean isLocalUploadUrl(String url) {
        if (isBlank(url)) {
            return false;
        }
        String normalized = normalizePath(url);
        return normalized.startsWith("/uploads/");
    }

    public String normalizePath(String url) {
        String value = url == null ? "" : url.trim();
        int queryIndex = value.indexOf('?');
        if (queryIndex >= 0) {
            value = value.substring(0, queryIndex);
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            try {
                value = java.net.URI.create(value).getPath();
            } catch (Exception ignored) {
                return value;
            }
        }
        if (!value.startsWith("/")) {
            value = "/" + value.replaceFirst("^\\./", "");
        }
        return value;
    }

    private String sign(String path, long expiresAt, String mode, Long projectId, Long userId) {
        String payload = path + "\n" + expiresAt + "\n" + mode + "\n"
                + (projectId == null ? "" : projectId) + "\n"
                + (userId == null ? "" : userId);
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Upload access 签名失败", ex);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeMode(String mode) {
        if (isBlank(mode)) {
            return null;
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        if (MODE_AUTHENTICATED.equals(normalized) || MODE_PROJECT.equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private Long parseProjectId(String mode, String projectId) {
        if (!MODE_PROJECT.equals(mode)) {
            return null;
        }
        if (isBlank(projectId)) {
            return null;
        }
        try {
            long parsed = Long.parseLong(projectId);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long parseUserId(String userId) {
        if (isBlank(userId)) {
            return null;
        }
        try {
            long parsed = Long.parseLong(userId);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long currentUserId() {
        try {
            if (!StpUtil.isLogin()) {
                return null;
            }
            return StpUtil.getLoginIdAsLong();
        } catch (Exception ignored) {
            return null;
        }
    }

    public record AccessDescriptor(boolean valid, String mode, Long projectId, Long userId) {
        public static AccessDescriptor invalid() {
            return new AccessDescriptor(false, null, null, null);
        }
    }
}
