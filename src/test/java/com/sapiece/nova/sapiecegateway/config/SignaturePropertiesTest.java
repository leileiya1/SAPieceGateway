package com.sapiece.nova.sapiecegateway.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignaturePropertiesTest {

    @Test
    void allowsAnEmptySecretWhileVerificationIsDisabled() {
        assertDoesNotThrow(new SignatureProperties()::validate);
    }

    @Test
    void rejectsAWeakSecretWhenVerificationIsEnabled() {
        var properties = new SignatureProperties();
        properties.setEnabled(true);
        properties.setSecret("too-short");

        assertThrows(IllegalStateException.class, properties::validate);
    }
}
