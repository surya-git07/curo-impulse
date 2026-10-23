package com.curo.curo_backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Who touched which patient's data and when. Append-only: nothing in the code updates or deletes these.
 * Holds ids and names only, never the data that was read.
 */
@Data
@Document("audit_logs")
@CompoundIndex(name = "patient_at", def = "{'patientId': 1, 'at': -1}")
public class AuditLog {

    @Id
    private String id;

    private String patientId;

    private String careId;

    // null actor id with role SYSTEM for automatic events such as a grant expiring
    private String actorUserId;

    private String actorName;

    private String actorRole;

    private String actorFacilityName;

    private AuditAction action;

    // what kind of data, e.g. PROFILE or PRESCRIPTION
    private String resource;

    private String resourceId;

    private String grantId;

    private AuditOutcome outcome;

    private Instant at;
}
