package com.sapiece.nova.sapiecegateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-256-GCM encryption for OAuth secrets and tokens stored in MySQL. */
@Component
public class SensitiveDataCipher {
    private static final String PREFIX = "enc:v1:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] key;

    public SensitiveDataCipher(@Value("${oauth2.encryption-key:}") String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            this.key = null;
        } else {
            this.key = Base64.getDecoder().decode(encodedKey.trim());
            if (key.length != 32) throw new IllegalArgumentException("OAUTH_ENCRYPTION_KEY必须是Base64编码的32字节密钥");
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return plaintext;
        requireKey();
        try {
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception error) {
            throw new IllegalStateException("敏感数据加密失败", error);
        }
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank() || !value.startsWith(PREFIX)) return value;
        requireKey();
        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            if (payload.length < 29) throw new IllegalArgumentException("密文长度不正确");
            byte[] nonce = java.util.Arrays.copyOfRange(payload, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(payload, 12, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new IllegalStateException("敏感数据解密失败", error);
        }
    }

    private void requireKey() {
        if (key == null) throw new IllegalStateException("未配置OAUTH_ENCRYPTION_KEY");
    }
}
