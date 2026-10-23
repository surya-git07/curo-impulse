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

    private VerificationStatus status;

    // machine-readable reason, also used as the API error code when status is REJECTED
    private String code;

    // human-readable reason; never repeats what the registry holds
    private String message;

    public static LicenceVerificationResult verified() {
        return new LicenceVerificationResult(VerificationStatus.VERIFIED, MATCHED,
                "Licence number, name and state match the licence registry.");
    }

    public static LicenceVerificationResult pending(String code, String message) {
        return new LicenceVerificationResult(VerificationStatus.PENDING, code, message);
    }

    public static LicenceVerificationResult rejected(String code, String message) {
        return new LicenceVerificationResult(VerificationStatus.REJECTED, code, message);
    }
}
