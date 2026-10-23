package com.curo.curo_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class ShareCodeResponse {
    private String shareCode;
    private Instant expiresAt;
}
