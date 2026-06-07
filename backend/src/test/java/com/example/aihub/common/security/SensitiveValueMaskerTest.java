package com.example.aihub.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveValueMaskerTest {

    @Test
    void fullyMasksNonBlankValues() {
        assertThat(SensitiveValueMasker.maskFully("super-secret")).isEqualTo("********");
        assertThat(SensitiveValueMasker.maskFully("   ")).isEmpty();
        assertThat(SensitiveValueMasker.maskFully(null)).isEmpty();
    }

    @Test
    void keepsEdgesForLongValues() {
        assertThat(SensitiveValueMasker.maskKeepingEdges("abcd1234wxyz", 4, 4)).isEqualTo("abcd****wxyz");
        assertThat(SensitiveValueMasker.maskKeepingEdges("short", 4, 4)).isEqualTo("****");
    }

    @Test
    void detectsMaskedPlaceholders() {
        assertThat(SensitiveValueMasker.looksMasked("abcd****wxyz")).isTrue();
        assertThat(SensitiveValueMasker.looksMasked("plain-value")).isFalse();
    }
}
