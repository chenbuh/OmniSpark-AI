package com.example.aihub.infrastructure.service;

import com.example.aihub.common.exception.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

@Service
public class LoginProtectionService {
    private static final String KEY_PREFIX = "auth:login:fail:";

    private final StringRedisTemplate redisTemplate;

    public LoginProtectionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void ensureAllowed(String username, String ip, String deviceId) {
        if (isExceeded(usernameKey(username), 8)) {
            throw new BusinessException("登录尝试过于频繁，请稍后再试");
        }
        if (isExceeded(usernameIpKey(username, ip), 5)) {
            throw new BusinessException("登录尝试过于频繁，请稍后再试");
        }
        if (isExceeded(deviceUsernameKey(deviceId, username), 6)) {
            throw new BusinessException("登录尝试过于频繁，请稍后再试");
        }
    }

    public void recordFailure(String username, String ip, String deviceId) {
        increment(usernameKey(username), Duration.ofMinutes(15));
        increment(usernameIpKey(username, ip), Duration.ofMinutes(10));
        increment(deviceUsernameKey(deviceId, username), Duration.ofMinutes(20));
    }

    public void clearFailures(String username, String ip, String deviceId) {
        delete(usernameKey(username));
        delete(usernameIpKey(username, ip));
        delete(deviceUsernameKey(deviceId, username));
    }

    private boolean isExceeded(String key, long threshold) {
        if (key == null) {
            return false;
        }
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null || raw.isBlank()) {
                return false;
            }
            return Long.parseLong(raw) >= threshold;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void increment(String key, Duration ttl) {
        if (key == null) {
            return;
        }
        try {
            Long current = redisTemplate.opsForValue().increment(key);
            if (current != null && current == 1L) {
                redisTemplate.expire(key, ttl);
            }
        } catch (Exception ignored) {
        }
    }

    private void delete(String key) {
        if (key == null) {
            return;
        }
        try {
            redisTemplate.delete(key);
        } catch (Exception ignored) {
        }
    }

    private String usernameKey(String username) {
        String normalized = normalizeSubject(username);
        return normalized == null ? null : KEY_PREFIX + "username:" + normalized;
    }

    private String usernameIpKey(String username, String ip) {
        String normalizedUsername = normalizeSubject(username);
        String normalizedIp = normalizeSubject(ip);
        if (normalizedUsername == null || normalizedIp == null) {
            return null;
        }
        return KEY_PREFIX + "username-ip:" + normalizedUsername + ":" + normalizedIp;
    }

    private String deviceUsernameKey(String deviceId, String username) {
        String normalizedUsername = normalizeSubject(username);
        String normalizedDeviceId = normalizeSubject(deviceId);
        if (normalizedUsername == null || normalizedDeviceId == null) {
            return null;
        }
        return KEY_PREFIX + "device-username:" + normalizedDeviceId + ":" + normalizedUsername;
    }

    private String normalizeSubject(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }
}
