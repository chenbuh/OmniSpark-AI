package com.example.aihub.common.security;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
public class CanaryTokenService {
    private static final Logger log = LoggerFactory.getLogger(CanaryTokenService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${app.security.canary.secret:omnispark-dev-canary-secret-2026}")
    private String secret;

    @PostConstruct
    public void validateConfig() {
        if ("omnispark-dev-canary-secret-2026".equals(secret)) {
            log.warn("⚠ 金丝雀令牌密钥使用了不安全的默认值（omnispark-dev-canary-secret-2026），"
                    + "生产环境请通过环境变量 APP_CANARY_SECRET 设置一个强随机密钥！");
        } else if (secret.length() < 16) {
            log.warn("⚠ 金丝雀令牌密钥长度（{}）不足 16 个字符，建议使用更长的密钥", secret.length());
        } else {
            log.info("金丝雀令牌密钥已配置");
        }
    }

    public String create(String resourceType, String resourceId, HttpServletRequest request) {
        long timestamp = Instant.now().getEpochSecond();
        String userId = currentUserId();
        String apiKeyId = attr(request, SecurityRequestAttributes.API_KEY_ID);
        String payload = String.join("|",
                "v1",
                nullToEmpty(resourceType),
                nullToEmpty(resourceId),
                nullToEmpty(userId),
                nullToEmpty(apiKeyId),
                String.valueOf(timestamp)
        );
        String payloadPart = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signaturePart = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmac(payload));
        return "wm_" + payloadPart + "." + signaturePart;
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Canary token 签名失败", ex);
        }
    }

    private String currentUserId() {
        try {
            if (StpUtil.isLogin()) {
                return StpUtil.getLoginIdAsString();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private String attr(HttpServletRequest request, String name) {
        if (request == null) {
            return "";
        }
        Object value = request.getAttribute(name);
        return value == null ? "" : String.valueOf(value);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
