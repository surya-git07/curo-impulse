package com.curo.curo_backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Lets one user (the grantee) see one patient's data. patientId and granteeId are user ids;
 * careId is kept alongside so grants can be listed and filtered by it. A null expiresAt means no expiry
 * (caregiver links); every other grant gets one when it becomes ACTIVE.
 */
@Data
@Document("access_grants")
@CompoundIndex(name = "patient_grantee_status", def = "{'patientId': 1, 'granteeId': 1, 'status': 1}")
public class AccessGrant {

    @Id
    private String id;

    private String patientId;

    private String careId;

    private String granteeId;

    // role of the grantee when the grant was made: DOCTOR, PHARMACY or CAREGIVER
    private String role;

    private String granteeName;

    private String granteeFacilityName;

    private List<GrantScope> scope;

    private GrantStatus status;

    private String reason;

    // hours the patient is asked for; used as the default when approving
    private Integer requestedDurationHours;

    private Instant expiresAt;

    private Instant createdAt;

    private Instant approvedAt;

    private Instant revokedAt;

    /** True only for an ACTIVE grant whose expiry (if any) is still in the future. */
    public boolean isUsableAt(Instant now) {
        return status == GrantStatus.ACTIVE && (expiresAt == null || expiresAt.isAfter(now));
    }

    /** ACTIVE but past its expiry. Stored status is updated lazily, so reads must use this. */
    public boolean isLapsedAt(Instant now) {
        return status == GrantStatus.ACTIVE && expiresAt != null && !expiresAt.isAfter(now);
    }

    public GrantStatus effectiveStatus(Instant now) {
        return isLapsedAt(now) ? GrantStatus.EXPIRED : status;
    }
}
