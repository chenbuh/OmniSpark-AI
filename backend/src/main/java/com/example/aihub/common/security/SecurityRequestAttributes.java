package com.example.aihub.common.security;

public final class SecurityRequestAttributes {
    private SecurityRequestAttributes() {
    }

    public static final String CLIENT_IP = "security.clientIp";
    public static final String API_KEY_ID = "security.apiKeyId";
    public static final String API_KEY_PREFIX = "security.apiKeyPrefix";
    public static final String API_KEY_SCOPE = "security.apiKeyScope";
    public static final String API_KEY_USER_ID = "security.apiKeyUserId";
    public static final String API_KEY_AUTHENTICATED = "security.apiKeyAuthenticated";
    public static final String RATE_LIMIT_HIT = "security.rateLimitHit";
    public static final String RISK_REASON = "security.riskReason";
    public static final String UPLOAD_ACCESS_MODE = "security.uploadAccessMode";
    public static final String UPLOAD_PROJECT_ID = "security.uploadProjectId";
    public static final String UPLOAD_SIGNED_USER_ID = "security.uploadSignedUserId";
}
