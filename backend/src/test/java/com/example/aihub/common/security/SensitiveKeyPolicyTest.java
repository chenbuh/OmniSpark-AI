package com.example.aihub.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveKeyPolicyTest {

    @Test
    void detectsCommonSecretPatterns() {
        assertThat(SensitiveKeyPolicy.looksSensitive("platform.password")).isTrue();
        assertThat(SensitiveKeyPolicy.looksSensitive("oauth.client-secret")).isTrue();
        assertThat(SensitiveKeyPolicy.looksSensitive("provider_api_key")).isTrue();
        assertThat(SensitiveKeyPolicy.looksSensitive("refreshToken")).isTrue();
    }

    @Test
    void ignoresOrdinaryKeys() {
        assertThat(SensitiveKeyPolicy.looksSensitive("platform.name")).isFalse();
        assertThat(SensitiveKeyPolicy.looksSensitive("maintenance_message")).isFalse();
        assertThat(SensitiveKeyPolicy.looksSensitive("tokenizer.model")).isFalse();
        assertThat(SensitiveKeyPolicy.looksSensitive("secretary.displayName")).isFalse();
    }
}
