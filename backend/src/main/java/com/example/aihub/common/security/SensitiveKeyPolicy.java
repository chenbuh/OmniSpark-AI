package com.example.aihub.common.security;

import java.util.Locale;
import java.util.Set;

/**
 * Shared heuristics for identifying keys that likely hold secrets or credentials.
 */
public final class SensitiveKeyPolicy {
    private static final Set<String> SENSITIVE_TOKENS = Set.of(
            "password", "passwd", "pwd", "secret", "token", "credential", "credentials"
    );

    private SensitiveKeyPolicy() {}

    public static boolean looksSensitive(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("api-key") || normalized.contains("api_key") || normalized.contains("apikey")) {
            return true;
        }
        if (normalized.contains("private-key") || normalized.contains("private_key") || normalized.contains("privatekey")) {
            return true;
        }
        if (normalized.contains("client-secret") || normalized.contains("client_secret") || normalized.contains("clientsecret")) {
            return true;
        }
        if (normalized.contains("access-token") || normalized.contains("access_token") || normalized.contains("accesstoken")) {
            return true;
        }
        if (normalized.contains("refresh-token") || normalized.contains("refresh_token") || normalized.contains("refreshtoken")) {
            return true;
        }
        for (String token : normalized.split("[^a-z0-9]+")) {
            if (SENSITIVE_TOKENS.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
