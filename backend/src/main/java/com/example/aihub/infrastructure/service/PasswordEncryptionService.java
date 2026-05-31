package com.example.aihub.infrastructure.service;

import com.example.aihub.common.exception.BusinessException;
import com.example.aihub.infrastructure.vo.PasswordPublicKeyVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

@Service
public class PasswordEncryptionService {
    private static final String KEY_ALGORITHM = "RSA";
    private static final String CIPHER_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String PUBLIC_KEY_ALGORITHM = "RSA-OAEP-256";
    private static final OAEPParameterSpec OAEP_SHA256_PARAMETER_SPEC = new OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
    );

    private final PrivateKey privateKey;
    private final PasswordPublicKeyVO publicKeyVO;

    /**
     * 是否强制要求密码以密文字段（encryptedPassword 等）提交。
     * 默认 true：拒绝明文密码字段，传输加密在协议层成为强约束。
     * 灰度切换或本地排障时可临时设为 false 放行明文。
     */
    @Value("${app.security.password.require-encrypted:true}")
    private boolean requireEncrypted;

    public PasswordEncryptionService() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            this.privateKey = keyPair.getPrivate();
            this.publicKeyVO = buildPublicKeyVO(keyPair.getPublic());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("初始化密码传输加密密钥失败", e);
        }
    }

    public PasswordPublicKeyVO getPublicKey() {
        PasswordPublicKeyVO vo = new PasswordPublicKeyVO();
        vo.setAlgorithm(publicKeyVO.getAlgorithm());
        vo.setPublicKey(publicKeyVO.getPublicKey());
        return vo;
    }

    public String resolvePassword(String plainPassword, String encryptedPassword) {
        if (encryptedPassword != null && !encryptedPassword.isBlank()) {
            return decrypt(encryptedPassword);
        }
        if (requireEncrypted && plainPassword != null && !plainPassword.isBlank()) {
            // 明文降级通道已关闭：客户端必须走 /api/auth/public-key 加密后提交
            throw new BusinessException("密码须加密传输，请刷新页面后重试");
        }
        return plainPassword;
    }

    private String decrypt(String encryptedPassword) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SHA256_PARAMETER_SPEC);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedPassword));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("密码解密失败，请刷新页面后重试");
        }
    }

    private PasswordPublicKeyVO buildPublicKeyVO(PublicKey publicKey) {
        PasswordPublicKeyVO vo = new PasswordPublicKeyVO();
        vo.setAlgorithm(PUBLIC_KEY_ALGORITHM);
        vo.setPublicKey(toPem(publicKey.getEncoded()));
        return vo;
    }

    private String toPem(byte[] encoded) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(encoded);
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }
}
