package com.curo.curo_backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@Document("users")
public class AppUser {

    @Id
    private String id;

    @Indexed(unique = true)
    private String careId;

    @Indexed(unique = true)
    private String phone;

    private String name;

    private String passwordHash;

    private String role;

    private Consent consent;

    // DOCTOR: licenceNumber + stateCouncil. PHARMACY: drugLicenceNumber + gstin + state. Numbers are stored upper-case.
    @Indexed(unique = true, sparse = true)
    private String licenceNumber;

    private String stateCouncil;

    @Indexed(unique = true, sparse = true)
    private String drugLicenceNumber;

    private String gstin;

    private String state;

    // set for DOCTOR and PHARMACY only; null for everyone else
    private VerificationStatus verificationStatus;

    private String verificationReasonCode;

    private String verificationReason;

    private String facilityId;

    private String facilityName;

    // patient's recorded allergies, as free text ("Penicillin", "NSAIDs"); used by the allergy check
    private List<String> allergies;

    // optional patient profile fields; only the patient sets them, and clinicians see them only through a PROFILE grant
    private LocalDate dateOfBirth;

    // MALE, FEMALE or OTHER
    private String gender;

    // A+, A-, B+, B-, AB+, AB-, O+ or O-
    private String bloodGroup;

    // "Any other disease", as free text entries
    private List<String> chronicConditions;

    private boolean profileCompleted = false;

    private Instant createdAt;

    private Instant deletedAt;
}
