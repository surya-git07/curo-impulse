package com.curo.curo_backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/** A short-lived, single-use code a patient shows a clinician. Redeeming it creates an ACTIVE grant. */
@Data
@Document("share_codes")
public class ShareCode {

    @Id
    private String id;

    // six digits
    @Indexed(unique = true)
    private String code;

    private String patientId;

    private List<GrantScope> scope;

    // length of the grant created when the code is redeemed
    private int durationHours;

    // MongoDB removes the document shortly after this time; redeem still checks it
    @Indexed(expireAfterSeconds = 0)
    private Instant expiresAt;

    private Instant createdAt;
}
