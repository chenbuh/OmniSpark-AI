package com.example.aihub.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;

@Service
public class SigningChallengeService {
    private static final String KEY_PREFIX = "api:sign:challenge:";

    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.security.signing.challenge-ttl-seconds:120}")
    private long ttlSeconds;

    public SigningChallengeService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Challenge create(String clientIp) {
        String challengeId = randomHex(16);
        String challengeSecret = randomHex(32);
        long now = System.currentTimeMillis();
        long expiresAt = now + ttlSeconds * 1000;
        String value = challengeSecret + "\n" + (clientIp == null ? "" : clientIp);
        redisTemplate.opsForValue().set(KEY_PREFIX + challengeId, value, Duration.ofSeconds(ttlSeconds));
        return new Challenge(challengeId, challengeSecret, now, expiresAt);
    }

    public ConsumedChallenge consume(String challengeId, String clientIp) {
        if (challengeId == null || challengeId.isBlank()) {
            return null;
        }
        String key = KEY_PREFIX + challengeId;
        String value = redisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return null;
        }
        redisTemplate.delete(key);
        String[] parts = value.split("\n", 2);
        String secret = parts[0];
        String boundIp = parts.length > 1 ? parts[1] : "";
        if (!boundIp.isBlank() && clientIp != null && !boundIp.equals(clientIp)) {
            return null;
        }
        return new ConsumedChallenge(challengeId, secret);
    }

    private String randomHex(int bytes) {
        byte[] data = new byte[bytes];
        random.nextBytes(data);
        return HexFormat.of().formatHex(data);
    }

    public record Challenge(String challengeId, String challengeSecret, long serverTime, long expiresAt) {
    }

    public record ConsumedChallenge(String challengeId, String challengeSecret) {
    }
}
