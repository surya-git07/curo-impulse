package com.curo.curo_backend.model;

import lombok.Data;

import java.time.Instant;

@Data
public class Consent {

    private boolean accepted;

    private String policyVersion;

    private Instant acceptedAt;
}
