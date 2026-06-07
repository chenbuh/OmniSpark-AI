package com.example.aihub.common.security;

/**
 * Shared policy for deciding whether a system config key should be masked in responses.
 */
public final class SensitiveConfigPolicy {
    private SensitiveConfigPolicy() {}

    public static boolean isSensitiveConfigKey(String configKey) {
        return SensitiveKeyPolicy.looksSensitive(configKey);
    }

    public static String maskSensitiveValue(String value) {
        return SensitiveValueMasker.maskFully(value);
    }
}
