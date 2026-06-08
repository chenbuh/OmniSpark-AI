package com.example.aihub.infrastructure.service;

import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.infrastructure.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminTotpService {
    private static final String SETUP_TICKET_PREFIX = "auth:totp:setup:";
    private static final String LOGIN_TICKET_PREFIX = "auth:totp:login:";
    private static final String HMAC_SHA1 = "HmacSHA1";
    private static final int DIGITS = 6;
    private static final int STEP_SECONDS = 30;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.security.totp.issuer:OmniSpark AI}")
    private String issuer;

    @Value("${app.security.totp.allowed-drift-windows:2}")
    private int allowedDriftWindows;

    public PendingTotpSetup beginSetup(User user, String clientIp, String userAgent, String deviceId) {
        String secret = generateSecret();
        String setupTicket = randomTicket();
        PendingLoginState state = PendingLoginState.forSetup(user, clientIp, userAgent, deviceId, secret);
        storeState(SETUP_TICKET_PREFIX + setupTicket, state, Duration.ofMinutes(10));
        return new PendingTotpSetup(setupTicket, secret, buildOtpauthUrl(user.getUsername(), secret), issuer);
    }

    public String beginLoginChallenge(User user, String clientIp, String userAgent, String deviceId) {
        String loginTicket = randomTicket();
        PendingLoginState state = PendingLoginState.forLogin(user, clientIp, userAgent, deviceId);
        storeState(LOGIN_TICKET_PREFIX + loginTicket, state, Duration.ofMinutes(5));
        return loginTicket;
    }

    public CompletedTotpChallenge completeSetup(String setupTicket, String totpCode) {
        PendingLoginState state = readState(SETUP_TICKET_PREFIX + setupTicket, "验证器绑定状态已失效，请重新开始");
        if (!"setup".equals(state.stage) || state.pendingSecret == null || state.pendingSecret.isBlank()) {
            throw new BusinessException("验证器绑定状态已失效，请重新开始");
        }
        assertCodeValid(state.pendingSecret, totpCode, "动态验证码无效，请重新输入");
        redisTemplate.delete(SETUP_TICKET_PREFIX + setupTicket);
        return new CompletedTotpChallenge(state.userId, state.username, state.ip, state.userAgent, state.deviceId, state.pendingSecret);
    }

    public PendingLoginState peekLoginState(String loginTicket) {
        PendingLoginState state = readState(LOGIN_TICKET_PREFIX + loginTicket, "动态验证码状态已失效，请重新登录");
        if (!"login".equals(state.stage)) {
            throw new BusinessException("动态验证码状态已失效，请重新登录");
        }
        return state;
    }

    public CompletedTotpChallenge completeLogin(String loginTicket, String totpCode, String persistedSecret) {
        PendingLoginState state = peekLoginState(loginTicket);
        if (!"login".equals(state.stage)) {
            throw new BusinessException("动态验证码状态已失效，请重新登录");
        }
        redisTemplate.delete(LOGIN_TICKET_PREFIX + loginTicket);
        if (persistedSecret == null || persistedSecret.isBlank()) {
            throw new BusinessException("当前账号尚未完成验证器绑定");
        }
        assertCodeValid(persistedSecret, totpCode, "动态验证码无效，请重新输入");
        return new CompletedTotpChallenge(state.userId, state.username, state.ip, state.userAgent, state.deviceId, null);
    }

    public boolean isAdmin(User user) {
        return user != null && "admin".equalsIgnoreCase(user.getRole());
    }

    private void assertCodeValid(String secret, String code, String message) {
        String normalizedCode = normalizeCode(code);
        if (normalizedCode == null || !matches(secret, normalizedCode)) {
            throw new BusinessException(message);
        }
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim();
        if (normalized.length() != DIGITS) {
            return null;
        }
        for (int i = 0; i < normalized.length(); i++) {
            if (!Character.isDigit(normalized.charAt(i))) {
                return null;
            }
        }
        return normalized;
    }

    private boolean matches(String secret, String code) {
        long currentWindow = Instant.now().getEpochSecond() / STEP_SECONDS;
        int driftWindows = Math.max(0, allowedDriftWindows);
        for (int drift = -driftWindows; drift <= driftWindows; drift++) {
            if (code.equals(generateTotpCode(secret, currentWindow + drift))) {
                return true;
            }
        }
        return false;
    }

    private String generateTotpCode(String secret, long movingFactor) {
        try {
            byte[] key = decodeBase32(secret);
            byte[] data = new byte[8];
            long value = movingFactor;
            for (int i = 7; i >= 0; i--) {
                data[i] = (byte) (value & 0xFF);
                value >>= 8;
            }
            Mac mac = Mac.getInstance(HMAC_SHA1);
            mac.init(new SecretKeySpec(key, HMAC_SHA1));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % 1_000_000;
            return String.format(Locale.ROOT, "%06d", otp);
        } catch (Exception e) {
            throw new BusinessException("动态验证码处理失败，请稍后重试");
        }
    }

    private String buildOtpauthUrl(String username, String secret) {
        String accountName = issuer + ":" + username;
        return "otpauth://totp/"
                + urlEncode(accountName)
                + "?secret=" + urlEncode(secret)
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS
                + "&period=" + STEP_SECONDS;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String generateSecret() {
        byte[] bytes = new byte[20];
        secureRandom.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    private String randomTicket() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void storeState(String key, PendingLoginState state, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(state), ttl);
        } catch (Exception e) {
            throw new BusinessException("验证器状态初始化失败，请稍后重试");
        }
    }

    private PendingLoginState readState(String key, String missingMessage) {
        String raw = redisTemplate.opsForValue().get(key);
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(missingMessage);
        }
        try {
            return objectMapper.readValue(raw, PendingLoginState.class);
        } catch (Exception e) {
            throw new BusinessException(missingMessage);
        }
    }

    private String encodeBase32(byte[] bytes) {
        StringBuilder builder = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte current : bytes) {
            buffer = (buffer << 8) | (current & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                bitsLeft -= 5;
                builder.append(BASE32_ALPHABET.charAt(index));
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            builder.append(BASE32_ALPHABET.charAt(index));
        }
        return builder.toString();
    }

    private byte[] decodeBase32(String value) {
        String normalized = value == null ? "" : value.trim().replace("=", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (int i = 0; i < normalized.length(); i++) {
            int index = BASE32_ALPHABET.indexOf(normalized.charAt(i));
            if (index < 0) {
                throw new IllegalArgumentException("Invalid base32 secret");
            }
            buffer = (buffer << 5) | index;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return output.toByteArray();
    }

    public record PendingTotpSetup(String setupTicket, String secret, String otpauthUrl, String issuer) {
    }

    public record CompletedTotpChallenge(Long userId, String username, String ip, String userAgent, String deviceId, String secretToPersist) {
    }

    public static class PendingLoginState {
        public Long userId;
        public String username;
        public String ip;
        public String userAgent;
        public String deviceId;
        public String stage;
        public String pendingSecret;

        public PendingLoginState() {
        }

        static PendingLoginState forSetup(User user, String ip, String userAgent, String deviceId, String pendingSecret) {
            PendingLoginState state = forLogin(user, ip, userAgent, deviceId);
            state.stage = "setup";
            state.pendingSecret = pendingSecret;
            return state;
        }

        static PendingLoginState forLogin(User user, String ip, String userAgent, String deviceId) {
            PendingLoginState state = new PendingLoginState();
            state.userId = user.getId();
            state.username = user.getUsername();
            state.ip = ip;
            state.userAgent = userAgent;
            state.deviceId = deviceId;
            state.stage = "login";
            return state;
        }
    }

}
