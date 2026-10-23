package com.curo.curo_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Checks the shared secret EventBridge sends. If no secret is configured, nobody gets in. */
@Component
public class InternalKeyVerifier {

    @Value("${internal.api-key:}")
    private String configuredKey;

    public boolean matches(String presented) {
        if (configuredKey == null || configuredKey.isBlank() || presented == null) {
            return false;
        }
        // constant-time comparison, so response timing does not reveal how much of a guess was right
        return MessageDigest.isEqual(configuredKey.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }
}
