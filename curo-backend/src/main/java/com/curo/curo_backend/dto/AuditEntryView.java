package com.curo.curo_backend.dto;

import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.AuditOutcome;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public class AuditEntryView {
    private String id;
    private Instant at;
    private ActorView actor;
    private AuditAction action;
    private String resource;
    private String resourceId;
    private String grantId;
    private AuditOutcome outcome;
    // e.g. the drug a pharmacy dispensed; absent for most entries
    private String detail;

    public AuditEntryView(String id, Instant at, ActorView actor, AuditAction action, String resource, String resourceId,
                          String grantId, AuditOutcome outcome) {
        this(id, at, actor, action, resource, resourceId, grantId, outcome, null);
    }
}
