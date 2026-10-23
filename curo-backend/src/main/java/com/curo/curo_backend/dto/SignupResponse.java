package com.curo.curo_backend.dto;

import com.curo.curo_backend.model.Consent;
import com.curo.curo_backend.model.VerificationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SignupResponse {
    private String id;
    private String careId;
    private String name;
    private String role;
    private Consent consent;
    // DOCTOR and PHARMACY only; omitted from the JSON for other roles
    private VerificationStatus verificationStatus;
    private String verificationReasonCode;
    private String verificationReason;
    // PHARMACY only: the pharmacy record created for the account
    private String facilityId;

    public SignupResponse(String id, String careId, String name, String role, Consent consent) {
        this(id, careId, name, role, consent, null, null, null, null);
    }

    public SignupResponse(String id, String careId, String name, String role, Consent consent,
                          VerificationStatus verificationStatus, String verificationReasonCode, String verificationReason) {
        this(id, careId, name, role, consent, verificationStatus, verificationReasonCode, verificationReason, null);
    }
}
