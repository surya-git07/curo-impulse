package com.curo.curo_backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** A patient's choice to let a caregiver see their dose schedule and missed-dose alerts. It owns a standing CAREGIVER access grant. */
@Data
@Document("caregiver_links")
@CompoundIndex(name = "patient_caregiver_status", def = "{'patientId': 1, 'caregiverId': 1, 'status': 1}")
public class CaregiverLink {

    @Id
    private String id;

    private String patientId;

    private String patientCareId;

    private String patientName;

    private String caregiverId;

    private String caregiverCareId;

    private String caregiverName;

    // free text such as "daughter"
    private String relationship;

    private LinkStatus status;

    // the access grant this link created; revoking the link revokes it
    private String grantId;

    private Instant createdAt;

    private Instant revokedAt;
}
