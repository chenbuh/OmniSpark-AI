package com.example.aihub.common.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * AES-256-GCM 加密/解密模型提供商的 API 密钥。
 * 加密密钥通过环境变量 {@code APP_MODEL_KEY_ENCRYPTION_KEY} 注入（64 字符十六进制串，即 256 位）。
 * 加密后的值以 {@code $AES$} 前缀标识，兼容已有的明文存量数据。
 */
@Service
public class ModelApiKeyEncryptor {
    private static final Logger log = LoggerFactory.getLogger(ModelApiKeyEncryptor.class);

    private static final String AES_GCM_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;      // 96 bits
    private static final int GCM_TAG_LENGTH = 128;     // bits
    private static final String ENCRYPTED_PREFIX = "$AES$";

    private static final String DEFAULT_KEY_WARNING =
        "⚠ 模型 API 密钥加密密钥使用了自动生成的默认值，重启后将无法解密已有数据！"
        + "请通过环境变量 APP_MODEL_KEY_ENCRYPTION_KEY 设置固定的 64 字符十六进制密钥（256 位）。";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public ModelApiKeyEncryptor(
            @Value("${app.security.model-key-encryption-key:}") String hexKey) {
        byte[] keyBytes;
        if (hexKey == null || hexKey.isBlank()) {
            // 开发环境下自动生成密钥（每次启动不同，适合本地开发）
            keyBytes = new byte[32];
            random.nextBytes(keyBytes);
            log.warn(DEFAULT_KEY_WARNING);
        } else {
            try {
                keyBytes = HexFormat.of().parseHex(hexKey);
            } catch (Exception ex) {
                throw new IllegalStateException(
                    "APP_MODEL_KEY_ENCRYPTION_KEY 格式错误，需要 64 字符十六进制串（256 位）", ex);
            }
            if (keyBytes.length != 32) {
                throw new IllegalStateException(
                    "APP_MODEL_KEY_ENCRYPTION_KEY 长度不正确：需要 64 字符十六进制（32 字节），实际 " + hexKey.length() + " 字符");
            }
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    @PostConstruct
    public void logKeyStatus() {
        log.info("模型 API 密钥加密服务已初始化");
    }

    /**
     * 加密 API 密钥。
     *
     * @param plaintext 明文 API 密钥
     * @return 加密后的字符串（含 {@code $AES$} 前缀），null 入参返回 null
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // 打包：IV(12) + 密文
            ByteBuffer buf = ByteBuffer.allocate(iv.length + ciphertext.length);
            buf.put(iv);
            buf.put(ciphertext);

            return ENCRYPTED_PREFIX + Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception ex) {
            throw new IllegalStateException("API 密钥加密失败", ex);
        }
    }

    /**
     * 解密 API 密钥。
     * 兼容存量明文数据：若不以 {@code $AES$} 开头则原样返回。
     *
     * @param ciphertext 密文（含 {@code $AES$} 前缀）或明文
     * @return 解密后的明文，null 入参返回 null
     */
    public String decrypt(String ciphertext) {
        if (ciphertext == null) {
            return null;
        }
        if (!ciphertext.startsWith(ENCRYPTED_PREFIX)) {
            // 存量明文数据，兼容返回
            return ciphertext;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(
                    ciphertext.substring(ENCRYPTED_PREFIX.length()));

            ByteBuffer buf = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buf.get(iv);
            byte[] encryptedData = new byte[buf.remaining()];
            buf.get(encryptedData);

            Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] plaintext = cipher.doFinal(encryptedData);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception ex) {
            // 解密失败时返回原始值，避免阻塞业务（记录警告但不抛异常）
            log.warn("API 密钥解密失败，返回原始值（通常是存量明文数据或密钥变更）");
            return ciphertext;
        }
    }
}
