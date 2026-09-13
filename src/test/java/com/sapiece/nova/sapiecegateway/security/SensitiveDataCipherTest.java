package com.sapiece.nova.sapiecegateway.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveDataCipherTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptsWithRandomNonceAndDecrypts() {
        SensitiveDataCipher cipher = new SensitiveDataCipher(KEY);
        String first = cipher.encrypt("oauth-secret");
        String second = cipher.encrypt("oauth-secret");
        assertTrue(first.startsWith("enc:v1:"));
        assertNotEquals(first, second);
        assertEquals("oauth-secret", cipher.decrypt(first));
        assertEquals("oauth-secret", cipher.decrypt(second));
    }

    @Test
    void rejectsTamperedCiphertextAndMissingKey() {
        SensitiveDataCipher cipher = new SensitiveDataCipher(KEY);
        String encrypted = cipher.encrypt("token");
        assertThrows(IllegalStateException.class,
                () -> cipher.decrypt(encrypted.substring(0, encrypted.length() - 2) + "AA"));
        assertThrows(IllegalStateException.class, () -> new SensitiveDataCipher("").encrypt("token"));
    }
}
