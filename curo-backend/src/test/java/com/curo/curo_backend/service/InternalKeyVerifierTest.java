package com.curo.curo_backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InternalKeyVerifierTest {

    private InternalKeyVerifier with(String configured) {
        InternalKeyVerifier v = new InternalKeyVerifier();
        ReflectionTestUtils.setField(v, "configuredKey", configured);
        return v;
    }

    @Test
    void onlyTheExactKeyMatches() {
        String key = UUID.randomUUID().toString();
        InternalKeyVerifier v = with(key);

        assertThat(v.matches(key)).isTrue();
        assertThat(v.matches(key + " ")).isFalse();
        assertThat(v.matches(key.substring(1))).isFalse();
        assertThat(v.matches(key.toUpperCase())).isFalse();
        assertThat(v.matches("")).isFalse();
        assertThat(v.matches(null)).isFalse();
    }

    @Test
    void anEmptyOrMissingConfiguredKeyLetsNobodyIn() {
        for (String configured : new String[]{"", "   ", null}) {
            InternalKeyVerifier v = with(configured);
            assertThat(v.matches("")).isFalse();
            assertThat(v.matches("   ")).isFalse();
            assertThat(v.matches(null)).isFalse();
            assertThat(v.matches("anything")).isFalse();
        }
    }
}
