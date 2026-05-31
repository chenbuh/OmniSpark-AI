package com.example.aihub.common.util;

import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.crypto.digest.DigestUtil;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class PasswordUtil {
    public static final int BCRYPT_COST = 12;
    public static final int MIN_PASSWORD_LENGTH = 8;

    private static final Pattern LEGACY_SHA256_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$");
    private static final Set<String> COMMON_WEAK_PASSWORDS = Set.of(
            "123456",
            "12345678",
            "123456789",
            "1234567890",
            "111111",
            "11111111",
            "000000",
            "00000000",
            "password",
            "password123",
            "qwerty",
            "qwerty123",
            "abc123",
            "iloveyou"
    );

    private PasswordUtil() {
    }

    /** 使用 BCrypt（自带随机盐）对明文密码做哈希，用于注册 / 改密 / 重置时存储。 */
    public static String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(BCRYPT_COST));
    }

    /** 校验明文密码与存储哈希是否匹配，兼容历史无盐 SHA-256 哈希以便平滑迁移。 */
    public static boolean matches(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        if (isBcryptHash(storedHash)) {
            return BCrypt.checkpw(rawPassword, storedHash);
        }
        if (isLegacySha256(storedHash)) {
            return DigestUtil.sha256Hex(rawPassword).equalsIgnoreCase(storedHash);
        }
        return false;
    }

    /** 是否为需要在下次成功登录时升级为 BCrypt 的历史哈希。 */
    public static boolean isLegacyHash(String storedHash) {
        return storedHash != null && isLegacySha256(storedHash);
    }

    /** 是否需要在成功登录后重新以目标 cost 重写哈希。 */
    public static boolean needsRehash(String storedHash) {
        if (storedHash == null || storedHash.isBlank()) {
            return false;
        }
        if (isLegacyHash(storedHash)) {
            return true;
        }
        Integer currentCost = getBcryptCost(storedHash);
        return currentCost != null && currentCost < BCRYPT_COST;
    }

    /** 返回密码策略校验结果，通过时返回 null。 */
    public static String getPasswordValidationError(String rawPassword, String username) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return "密码不能为空";
        }
        if (rawPassword.length() < MIN_PASSWORD_LENGTH) {
            return "密码长度不能少于 " + MIN_PASSWORD_LENGTH + " 位";
        }

        String normalizedPassword = normalize(rawPassword);
        if (COMMON_WEAK_PASSWORDS.contains(normalizedPassword)) {
            return "密码过于简单，请更换为更安全的密码";
        }

        if (username != null && !username.isBlank() && normalizedPassword.equals(normalize(username))) {
            return "密码不能与账号相同";
        }
        return null;
    }

    public static Integer getBcryptCost(String value) {
        if (!isBcryptHash(value)) {
            return null;
        }
        String[] segments = value.split("\\$");
        if (segments.length < 3) {
            return null;
        }
        try {
            return Integer.parseInt(segments[2]);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isBcryptHash(String value) {
        return value != null
                && (value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$"));
    }

    private static boolean isLegacySha256(String value) {
        return value != null && LEGACY_SHA256_PATTERN.matcher(value).matches();
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
