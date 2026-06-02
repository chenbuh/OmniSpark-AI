package com.example.aihub.common.security;

import cn.dev33.satoken.stp.StpUtil;
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
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    public static final String MODE_AUTHENTICATED = "auth";
    public static final String MODE_PROJECT = "project";

    @Value("${app.security.upload-access.secret:omnispark-dev-upload-access-secret-2026}")
    private String secret;

    @Value("${app.security.upload-access.ttl-seconds:7200}")
    private long ttlSeconds;

    public String signUrl(String rawUrl) {
        return signAuthenticatedUrl(rawUrl);
    }

    public String signAuthenticatedUrl(String rawUrl) {
        return signUrl(rawUrl, MODE_AUTHENTICATED, null);
    }

    public String signProjectUrl(String rawUrl, Long projectId) {
        if (projectId == null || projectId <= 0) {
            return signAuthenticatedUrl(rawUrl);
        }
        return signUrl(rawUrl, MODE_PROJECT, projectId);
    }

    private String signUrl(String rawUrl, String mode, Long projectId) {
        if (!isLocalUploadUrl(rawUrl)) {
            return rawUrl;
        }
        String path = normalizePath(rawUrl);
        long expiresAt = Instant.now().getEpochSecond() + Math.max(ttlSeconds, 60);
        String normalizedMode = normalizeMode(mode);
        Long currentUserId = currentUserId();
        String projectPart = MODE_PROJECT.equals(normalizedMode) && projectId != null && projectId > 0
                ? "&pid=" + projectId
                : "";
        String userPart = currentUserId != null ? "&uid=" + currentUserId : "";
        String signature = sign(path, expiresAt, normalizedMode, projectId, currentUserId);
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
        if (!StpUtil.isLogin()) {
            return null;
        }
        return StpUtil.getLoginIdAsLong();
    }

    public record AccessDescriptor(boolean valid, String mode, Long projectId, Long userId) {
        public static AccessDescriptor invalid() {
            return new AccessDescriptor(false, null, null, null);
        }
    }
}
