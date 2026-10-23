package com.curo.curo_backend.dto;

import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.GrantStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/** The grant as the API shows it. status is the effective one (an ACTIVE grant past its expiry shows EXPIRED). */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class AccessGrantView {
    private String id;
    private String careId;
    // CLINICAL, PHARMACY or CAREGIVER
    private String type;
    private List<GrantScope> scope;
    private GrantStatus status;
    private ActorView requester;
    private String reason;
    private Instant requestedAt;
    private Instant approvedAt;
    private Instant expiresAt;
    private Instant revokedAt;
}
