package com.curo.curo_backend.dto;

import com.curo.curo_backend.model.VerificationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/** A DOCTOR or PHARMACY account as the SUPER_ADMIN review screen sees it. No phone, no password. */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AdminUserView {
    private String id;
    private String careId;
    private String role;
    private String name;
    private String facilityId;
    private String licenceNumber;
    private String stateCouncil;
    private String drugLicenceNumber;
    private String gstin;
    private String state;
    private VerificationStatus verificationStatus;
    private String verificationReasonCode;
    private String verificationReason;
    private Instant createdAt;
}
