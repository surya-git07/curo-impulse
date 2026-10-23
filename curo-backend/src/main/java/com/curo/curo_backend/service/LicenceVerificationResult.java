package com.curo.curo_backend.service;

import com.curo.curo_backend.model.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LicenceVerificationResult {

    public static final String MATCHED = "LICENCE_MATCHED";
    public static final String NOT_IN_REGISTRY = "LICENCE_NOT_IN_REGISTRY";
    public static final String INVALID_NUMBER = "INVALID_LICENCE_NUMBER";
    public static final String NAME_MISMATCH = "LICENCE_NAME_MISMATCH";
    public static final String STATE_MISMATCH = "LICENCE_STATE_MISMATCH";
    public static final String GSTIN_MISMATCH = "LICENCE_GSTIN_MISMATCH";
    public static final String ACCEPTED_DEMO = "LICENCE_ACCEPTED_DEMO";

    private VerificationStatus status;

    // machine-readable reason, also used as the API error code when status is REJECTED
    private String code;

    // human-readable reason; never repeats what the registry holds
    private String message;

    public static LicenceVerificationResult verified() {
        return new LicenceVerificationResult(VerificationStatus.VERIFIED, MATCHED,
                "Licence number, name and state match the licence registry.");
    }

    /** demo-accept mode: the format checks passed, nothing was looked up. */
    public static LicenceVerificationResult verifiedInDemoMode() {
        return new LicenceVerificationResult(VerificationStatus.VERIFIED, ACCEPTED_DEMO,
                "Accepted in demo mode: the licence format passed. It was not checked against a registry.");
    }

    public static LicenceVerificationResult pending(String code, String message) {
        return new LicenceVerificationResult(VerificationStatus.PENDING, code, message);
    }

    public static LicenceVerificationResult rejected(String code, String message) {
        return new LicenceVerificationResult(VerificationStatus.REJECTED, code, message);
    }
}
