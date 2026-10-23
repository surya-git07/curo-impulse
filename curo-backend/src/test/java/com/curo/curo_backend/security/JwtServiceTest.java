package com.curo.curo_backend.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService serviceWithSecret(String secret) {
        JwtService service = new JwtService();
        ReflectionTestUtils.setField(service, "secretKey", secret);
        ReflectionTestUtils.setField(service, "expirationMs", 60_000L);
        return service;
    }

    private String randomSecret() {
        return UUID.randomUUID() + UUID.randomUUID().toString();
    }

    @Test
    void tokenCarriesContractClaims() {
        JwtService service = serviceWithSecret(randomSecret());

        String token = service.generateToken("665f0000000000000000a001", "PATIENT", "CID-7K3MQ9", null);

        assertThat(service.extractUsername(token)).isEqualTo("665f0000000000000000a001");
        assertThat(service.extractRole(token)).isEqualTo("PATIENT");
        assertThat(service.<String>extractClaim(token, c -> c.get("careId", String.class))).isEqualTo("CID-7K3MQ9");
        assertThat(service.extractClaim(token, (Claims c) -> c.get("facilityId"))).isNull();
        assertThat(service.isTokenValid(token, "665f0000000000000000a001")).isTrue();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = serviceWithSecret(randomSecret()).generateToken("u1", "PATIENT", "CID-7K3MQ9", null);
        JwtService other = serviceWithSecret(randomSecret());

        assertThatThrownBy(() -> other.extractUsername(token)).isInstanceOf(Exception.class);
    }

    @Test
    void shortOrMissingSecretFailsFast() {
        assertThatThrownBy(() -> serviceWithSecret("short").checkSecret())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> serviceWithSecret(null).checkSecret())
                .isInstanceOf(IllegalStateException.class);
        serviceWithSecret(randomSecret()).checkSecret();
    }
}
