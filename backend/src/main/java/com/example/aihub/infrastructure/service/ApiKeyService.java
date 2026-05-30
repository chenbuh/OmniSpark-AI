package com.example.aihub.infrastructure.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.infrastructure.entity.ApiKey;
import com.example.aihub.infrastructure.mapper.ApiKeyMapper;
import com.example.aihub.infrastructure.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aihub.common.util.SecurityUtil;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ApiKeyService {
    private final ApiKeyMapper apiKeyMapper;
    private final UserMapper userMapper;
    private final SecureRandom random = new SecureRandom();

    // 生成完整的 API Key 格式: omni_{prefix}_{secret}
    private static final String KEY_PREFIX = "omni";

    public record GeneratedKey(ApiKey key, String fullKey) {}

    @Transactional(rollbackFor = Exception.class)
    public GeneratedKey create(Long userId, String name) {
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
        key.setStatus(1);
        apiKeyMapper.insert(key);

        return new GeneratedKey(key, fullKey);
    }

    public List<ApiKey> listByUser(Long userId) {
        return apiKeyMapper.selectList(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getUserId, userId).orderByDesc(ApiKey::getId));
    }

    public List<ApiKey> listAll() {
        return apiKeyMapper.selectList(new LambdaQueryWrapper<ApiKey>().orderByDesc(ApiKey::getId));
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
        if (userId == null || userId <= 0) {
            userId = SecurityUtil.loginUserId();
        }
        GeneratedKey generated = create(userId, name);
        return Map.of("id", generated.key().getId(), "name", generated.key().getName(),
                "fullKey", generated.fullKey(), "keyPrefix", generated.key().getKeyPrefix());
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
    public void updateLastUsed(Long id) {
        ApiKey key = apiKeyMapper.selectById(id);
        if (key != null) {
            key.setLastUsedAt(LocalDateTime.now());
            apiKeyMapper.updateById(key);
        }
    }

    public Long authenticate(String fullKey) {
        if (fullKey == null || fullKey.isBlank()) return null;
        String hash = cn.hutool.crypto.digest.DigestUtil.sha256Hex(fullKey);
        ApiKey key = apiKeyMapper.selectOne(new LambdaQueryWrapper<ApiKey>()
                .eq(ApiKey::getKeyHash, hash).eq(ApiKey::getStatus, 1));
        if (key == null) return null;
        updateLastUsed(key.getId());
        return key.getUserId();
    }
}
