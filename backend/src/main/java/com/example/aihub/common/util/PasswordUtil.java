package com.example.aihub.common.util;

import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.crypto.digest.DigestUtil;

public final class PasswordUtil {
    private PasswordUtil() {
    }

    /** 使用 BCrypt（自带随机盐）对明文密码做哈希，用于注册 / 改密 / 重置时存储。 */
    public static String encode(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return BCrypt.hashpw(rawPassword);
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

    private static boolean isBcryptHash(String value) {
        return value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$");
    }

    private static boolean isLegacySha256(String value) {
        return value.matches("^[a-fA-F0-9]{64}$");
    }
}
