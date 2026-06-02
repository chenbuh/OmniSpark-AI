package com.example.aihub.common.config;

import cn.hutool.crypto.digest.DigestUtil;
import com.example.aihub.common.security.ClientIpResolver;
import com.example.aihub.common.security.SigningChallengeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequestWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.net.URLEncoder;

@Component
@RequiredArgsConstructor
public class ApiSignInterceptor implements HandlerInterceptor {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String NONCE_KEY_PREFIX = "api:sign:nonce:";

    private final StringRedisTemplate redisTemplate;
    private final SigningChallengeService signingChallengeService;
    private final ClientIpResolver clientIpResolver;

    @Value("${app.security.signing.window-seconds:300}")
    private long windowSeconds;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!requiresSignature(request)) {
            return true;
        }

        String timestamp = request.getHeader("X-Timestamp");
        String nonce = request.getHeader("X-Nonce");
        String signature = request.getHeader("X-Sign");
        String challengeId = request.getHeader("X-Challenge-Id");
        if (isBlank(timestamp) || isBlank(nonce) || isBlank(signature) || isBlank(challengeId)) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "请求签名缺失，请刷新页面后重试");
            return false;
        }

        long requestTimeMillis;
        try {
            requestTimeMillis = Long.parseLong(timestamp);
        } catch (NumberFormatException ex) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "请求时间戳无效");
            return false;
        }

        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000;
        if (Math.abs(now - requestTimeMillis) > windowMillis) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "请求已过期，请刷新页面后重试");
            return false;
        }

        String clientIp = clientIpResolver.resolve(request);
        SigningChallengeService.ConsumedChallenge challenge = signingChallengeService.consume(challengeId, clientIp);
        if (challenge == null) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "请求签名挑战已失效，请刷新页面后重试");
            return false;
        }

        String expectedSignature = sign(buildSigningPayload(request, timestamp, nonce, challenge.challengeId()), challenge.challengeSecret());
        if (!constantTimeEquals(expectedSignature, signature)) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "请求签名校验失败");
            return false;
        }

        Boolean accepted = redisTemplate.opsForValue().setIfAbsent(
                NONCE_KEY_PREFIX + nonce,
                timestamp,
                // 时间戳可接受跨度为 [now-window, now+window]，nonce 去重必须覆盖整个跨度，
                // 再加一秒余量，否则窗口边界存在秒级重放竞态。
                Duration.ofSeconds(windowSeconds * 2 + 1)
        );
        if (!Boolean.TRUE.equals(accepted)) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "检测到重复请求，请稍后再试");
            return false;
        }
        return true;
    }

    private boolean requiresSignature(HttpServletRequest request) {
        String method = request.getMethod();
        if (method == null || "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) || "OPTIONS".equalsIgnoreCase(method)) {
            return false;
        }
        String path = request.getRequestURI();
        if (path == null) {
            return false;
        }
        return path.startsWith("/api/auth/")
                || path.startsWith("/api/admin/users");
    }

    private String buildSigningPayload(HttpServletRequest request, String timestamp, String nonce, String challengeId) throws Exception {
        String method = request.getMethod() == null ? "" : request.getMethod().toUpperCase();
        String path = request.getRequestURI();
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            path += "?" + request.getQueryString();
        }
        String body = isFormUrlEncoded(request) ? buildCanonicalFormBody(request) : readBody(request);
        String bodyHash = DigestUtil.sha256Hex(body);
        return method + "\n" + path + "\n" + bodyHash + "\n" + timestamp + "\n" + nonce + "\n" + challengeId;
    }

    private String readBody(HttpServletRequest request) {
        HttpServletRequest current = request;
        while (current != null) {
            if (current instanceof CachedBodyHttpServletRequest cachedRequest) {
                return cachedRequest.getCachedBodyAsString();
            }
            if (current instanceof HttpServletRequestWrapper wrapper) {
                current = (HttpServletRequest) wrapper.getRequest();
                continue;
            }
            break;
        }
        return "";
    }

    private boolean isFormUrlEncoded(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null) {
            return false;
        }
        return contentType.toLowerCase().startsWith("application/x-www-form-urlencoded");
    }

    private String buildCanonicalFormBody(HttpServletRequest request) {
        Map<String, String[]> sortedParams = new TreeMap<>(request.getParameterMap());
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String[]> entry : sortedParams.entrySet()) {
            String key = urlEncode(entry.getKey());
            String[] values = entry.getValue();
            if (values == null || values.length == 0) {
                appendFormParam(sb, key, "");
                continue;
            }
            for (String value : values) {
                appendFormParam(sb, key, value);
            }
        }
        return sb.toString();
    }

    private void appendFormParam(StringBuilder sb, String encodedKey, String value) {
        if (sb.length() > 0) {
            sb.append('&');
        }
        sb.append(encodedKey).append('=').append(urlEncode(value == null ? "" : value));
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String sign(String payload, String signingSecret) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        return Base64.getEncoder().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(leftBytes, rightBytes);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":400,\"message\":\"" + message + "\",\"data\":null}");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
