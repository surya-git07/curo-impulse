package com.curo.curo_backend.dto;

import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.AuditOutcome;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class AuditEntryView {
    private String id;
    private Instant at;
    private ActorView actor;
    private AuditAction action;
    private String resource;
    private String resourceId;
    private String grantId;
    private AuditOutcome outcome;
}
