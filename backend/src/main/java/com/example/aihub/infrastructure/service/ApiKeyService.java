package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.common.util.PagingUtil;
import com.example.aihub.infrastructure.entity.ApiKey;
import com.example.aihub.infrastructure.mapper.ApiKeyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aihub.common.util.SecurityUtil;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ApiKeyService {
    private final ApiKeyMapper apiKeyMapper;
    private final SecureRandom random = new SecureRandom();

    // 生成完整的 API Key 格式: omni_{prefix}_{secret}
    private static final String KEY_PREFIX = "omni";
    private static final int DEFAULT_DAILY_QUOTA = 1000;
    private static final int MAX_DAILY_QUOTA = 100_000;
    private static final int RISK_FREEZE_THRESHOLD = 6;
    private static final Set<String> ALLOWED_SCOPES = Set.of("all", "read", "write", "generation", "admin", "read_write");

    public record GeneratedKey(ApiKey key, String fullKey) {}
    public record AuthenticatedApiKey(Long userId, Long apiKeyId, String keyPrefix, String scope) {}
    public record AuthenticationResult(boolean success, int status, String message, AuthenticatedApiKey principal) {
        public static AuthenticationResult ok(ApiKey key) {
            return new AuthenticationResult(
                    true,
                    200,
                    "认证成功",
                    new AuthenticatedApiKey(key.getUserId(), key.getId(), key.getKeyPrefix(), key.getScope())
            );
        }

        public static AuthenticationResult fail(int status, String message) {
            return new AuthenticationResult(false, status, message, null);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public GeneratedKey create(Long userId, String name) {
        return create(userId, name, "all", null, DEFAULT_DAILY_QUOTA);
    }

    @Transactional(rollbackFor = Exception.class)
    public GeneratedKey create(Long userId, String name, String scope, LocalDateTime expiresAt, Integer dailyQuota) {
        // 生成前缀 (4位随机字母)
        byte[] prefixBytes = new byte[4];
        random.nextBytes(prefixBytes);
        StringBuilder prefix = new StringBuilder();
        for (byte b : prefixBytes) {
            prefix.append((char) ('a' + (Math.abs(b) % 26)));
        }

        // 生成长密钥 (32字节随机)
        byte[] secretBytes = new byte[32];
        random.nextBytes(secretBytes);
        String secret = HexFormat.of().formatHex(secretBytes);

        String fullKey = KEY_PREFIX + "_" + prefix + "_" + "sk-" + secret;
        String hash = cn.hutool.crypto.digest.DigestUtil.sha256Hex(fullKey);

        ApiKey key = new ApiKey();
        key.setUserId(userId);
        key.setName(name);
        key.setKeyPrefix(KEY_PREFIX + "_" + prefix);
        key.setKeyHash(hash);
        key.setPermissions("all");
        key.setScope(normalizeScope(scope));
        key.setExpiresAt(expiresAt);
        key.setDailyQuota(normalizeDailyQuota(dailyQuota));
        key.setDailyUsed(0);
        key.setQuotaResetDate(LocalDate.now());
        key.setRiskScore(0);
        key.setStatus(1);
        apiKeyMapper.insert(key);

        return new GeneratedKey(key, fullKey);
    }

    public List<ApiKey> listByUser(Long userId, int limit) {
        return apiKeyMapper.selectList(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getUserId, userId)
                .orderByDesc(ApiKey::getId)
                .last("LIMIT " + PagingUtil.clampLimit(limit, 100, 100)));
    }

    public List<ApiKey> listAll(int limit) {
        return apiKeyMapper.selectList(new LambdaQueryWrapper<ApiKey>()
                .orderByDesc(ApiKey::getId)
                .last("LIMIT " + PagingUtil.clampLimit(limit, 100, 100)));
    }

    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long id, Long userId) {
        ApiKey key = apiKeyMapper.selectById(id);
        if (key == null) throw new BusinessException("密钥不存在");
        if (!key.getUserId().equals(userId)) throw new BusinessException("只能吊销自己的密钥");
        key.setStatus(0);
        apiKeyMapper.updateById(key);
    }

    public Map<String, Object> generate(String name, Long userId) {
        return generate(name, userId, "all", null, DEFAULT_DAILY_QUOTA);
    }

    public Map<String, Object> generate(String name, Long userId, String scope, LocalDateTime expiresAt, Integer dailyQuota) {
        if (userId == null || userId <= 0) {
            userId = SecurityUtil.loginUserId();
        }
        GeneratedKey generated = create(userId, name, scope, expiresAt, dailyQuota);
        return Map.of("id", generated.key().getId(), "name", generated.key().getName(),
                "fullKey", generated.fullKey(), "keyPrefix", generated.key().getKeyPrefix(),
                "scope", generated.key().getScope(), "dailyQuota", generated.key().getDailyQuota());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateName(Long id, String name) {
        ApiKey key = apiKeyMapper.selectById(id);
        if (key == null) throw new BusinessException("密钥不存在");
        key.setName(name);
        apiKeyMapper.updateById(key);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteByAdmin(Long id) {
        ApiKey key = apiKeyMapper.selectById(id);
        if (key == null) throw new BusinessException("密钥不存在");
        apiKeyMapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void revokeByAdmin(Long id) {
        ApiKey key = apiKeyMapper.selectById(id);
        if (key == null) throw new BusinessException("密钥不存在");
        key.setStatus(0);
        apiKeyMapper.updateById(key);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unfreezeByAdmin(Long id) {
        ApiKey key = apiKeyMapper.selectById(id);
        if (key == null) throw new BusinessException("密钥不存在");
        key.setStatus(1);
        key.setFrozenReason(null);
        key.setRiskScore(0);
        apiKeyMapper.updateById(key);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateLastUsed(Long id) {
        ApiKey key = apiKeyMapper.selectById(id);
        if (key != null) {
            key.setLastUsedAt(LocalDateTime.now());
            apiKeyMapper.updateById(key);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public AuthenticationResult authenticate(String fullKey, String method, String path, String clientIp, String userAgent) {
        if (fullKey == null || fullKey.isBlank()) {
            return AuthenticationResult.fail(401, "API Key 不能为空");
        }
        String hash = cn.hutool.crypto.digest.DigestUtil.sha256Hex(fullKey);
        ApiKey key = apiKeyMapper.selectOne(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getKeyHash, hash));
        if (key == null) {
            return AuthenticationResult.fail(401, "API Key 无效");
        }
        if (key.getStatus() == null || key.getStatus() == 0) {
            return AuthenticationResult.fail(401, "API Key 已吊销");
        }
        if (key.getStatus() == 2) {
            String reason = key.getFrozenReason() == null ? "触发风险控制" : key.getFrozenReason();
            return AuthenticationResult.fail(403, "API Key 已冻结：" + reason);
        }
        if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(LocalDateTime.now())) {
            return AuthenticationResult.fail(403, "API Key 已过期");
        }
        if (!isScopeAllowed(key.getScope(), method, path)) {
            return AuthenticationResult.fail(403, "API Key scope 不允许访问该接口");
        }

        resetQuotaIfNeeded(key);
        int quota = key.getDailyQuota() == null ? DEFAULT_DAILY_QUOTA : key.getDailyQuota();
        int used = key.getDailyUsed() == null ? 0 : key.getDailyUsed();
        if (quota > 0 && used >= quota) {
            return AuthenticationResult.fail(429, "API Key 今日额度已用完");
        }

        int riskScore = calculateRiskScore(key, clientIp, userAgent);
        key.setRiskScore(riskScore);
        if (riskScore >= RISK_FREEZE_THRESHOLD) {
            key.setStatus(2);
            key.setFrozenReason("同一 API Key 来源 IP/UA 变化异常，已自动冻结");
            apiKeyMapper.updateById(key);
            return AuthenticationResult.fail(403, "API Key 已因异常调用被冻结");
        }

        key.setDailyUsed(used + 1);
        key.setLastUsedAt(LocalDateTime.now());
        key.setLastUsedIp(clientIp);
        key.setLastUserAgent(truncate(userAgent, 500));
        apiKeyMapper.updateById(key);
        return AuthenticationResult.ok(key);
    }

    public Long authenticate(String fullKey) {
        AuthenticationResult result = authenticate(fullKey, "GET", "/api", null, null);
        return result.success() && result.principal() != null ? result.principal().userId() : null;
    }

    private void resetQuotaIfNeeded(ApiKey key) {
        LocalDate today = LocalDate.now();
        if (key.getQuotaResetDate() == null || key.getQuotaResetDate().isBefore(today)) {
            key.setDailyUsed(0);
            key.setQuotaResetDate(today);
        }
    }

    private int calculateRiskScore(ApiKey key, String clientIp, String userAgent) {
        int score = key.getRiskScore() == null ? 0 : key.getRiskScore();
        if (!isBlank(key.getLastUsedIp()) && !isBlank(clientIp) && !key.getLastUsedIp().equals(clientIp)) {
            score += 2;
        }
        String previousUa = normalizeUserAgent(key.getLastUserAgent());
        String currentUa = normalizeUserAgent(userAgent);
        if (!previousUa.isBlank() && !currentUa.isBlank() && !previousUa.equals(currentUa)) {
            score += 1;
        }
        return score;
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return "all";
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String item : scope.split(",")) {
            String value = item.trim().toLowerCase();
            if (value.isBlank()) {
                continue;
            }
            if (!ALLOWED_SCOPES.contains(value)) {
                throw new BusinessException("API Key scope 非法：" + value);
            }
            normalized.add(value);
        }
        return normalized.isEmpty() ? "all" : String.join(",", normalized);
    }

    private int normalizeDailyQuota(Integer dailyQuota) {
        if (dailyQuota == null) {
            return DEFAULT_DAILY_QUOTA;
        }
        if (dailyQuota < 0) {
            throw new BusinessException("每日额度不能为负数");
        }
        return Math.min(dailyQuota, MAX_DAILY_QUOTA);
    }

    private boolean isScopeAllowed(String scope, String method, String path) {
        Set<String> scopes = parseScopes(scope);
        if (scopes.contains("all")) {
            return true;
        }
        String normalizedPath = path == null ? "" : path;
        String normalizedMethod = method == null ? "" : method.toUpperCase();
        if (normalizedPath.startsWith("/api/admin/") && !scopes.contains("admin")) {
            return false;
        }
        if (normalizedPath.startsWith("/api/generation/") && scopes.contains("generation")) {
            return true;
        }
        boolean readMethod = "GET".equals(normalizedMethod) || "HEAD".equals(normalizedMethod) || "OPTIONS".equals(normalizedMethod);
        if (readMethod) {
            return scopes.contains("read") || scopes.contains("read_write") || scopes.contains("admin");
        }
        return scopes.contains("write") || scopes.contains("read_write") || scopes.contains("admin");
    }

    private Set<String> parseScopes(String scope) {
        Set<String> result = new LinkedHashSet<>();
        if (scope == null || scope.isBlank()) {
            result.add("all");
            return result;
        }
        for (String item : scope.split(",")) {
            String value = item.trim().toLowerCase();
            if (!value.isBlank()) {
                result.add(value);
            }
        }
        return result;
    }

    private String normalizeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "";
        }
        return userAgent.length() <= 120 ? userAgent : userAgent.substring(0, 120);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
