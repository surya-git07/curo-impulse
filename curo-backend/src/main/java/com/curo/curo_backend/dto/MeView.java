package com.curo.curo_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.curo.curo_backend.model.Consent;
import com.curo.curo_backend.model.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

/** GET /auth/me: the login `user` object plus consent, and the verification fields (only present for DOCTOR and PHARMACY). */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MeView {
    private String id;
    private String careId;
    private String name;
    private String role;
    private String facilityId;
    private String facilityName;
    private boolean profileCompleted;
    private Consent consent;
    private VerificationStatus verificationStatus;
    private String verificationReasonCode;
    private String verificationReason;
}
