package com.curo.curo_backend.service;

import com.curo.curo_backend.util.GstinValidator;
import com.curo.curo_backend.util.LicenceNumbers;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Demo only. Active when licence.verification.mode=demo-accept. It still checks the licence number format and the GSTIN
 * (format, state code and check character, and that the GSTIN matches the state), but it does not look anything up
 * in a registry and does not compare names: a licence that passes those checks is VERIFIED. Never use it with real data.
 * Duplicate licences are still refused by the signup code and the unique indexes, which this class does not touch.
 */
@Service
@ConditionalOnProperty(name = "licence.verification.mode", havingValue = "demo-accept")
public class DemoAcceptVerificationService implements LicenceVerificationService {

    private static final Logger log = LoggerFactory.getLogger(DemoAcceptVerificationService.class);

    @PostConstruct
    void warn() {
        log.warn("LICENCE VERIFICATION IS IN demo-accept MODE (licence.verification.mode). Any licence with a valid format "
                + "and GSTIN is marked VERIFIED without a registry check. Use for demos only, never with real data.");
    }

    @Override
    public LicenceVerificationResult verifyDoctor(String licenceNumber, String stateCouncil, String name) {
        if (!LicenceNumbers.isValidDoctorNumber(LicenceNumbers.normalize(licenceNumber))) {
            return LicenceVerificationResult.rejected(LicenceVerificationResult.INVALID_NUMBER,
                    "Licence number format is not recognised.");
        }
        return LicenceVerificationResult.verifiedInDemoMode();
    }

    @Override
    public LicenceVerificationResult verifyPharmacy(String drugLicenceNumber, String gstin, String state, String name) {
        if (!LicenceNumbers.isValidDrugLicenceNumber(LicenceNumbers.normalize(drugLicenceNumber))) {
            return LicenceVerificationResult.rejected(LicenceVerificationResult.INVALID_NUMBER,
                    "Drug licence number format is not recognised.");
        }
        return checkGstin(gstin, state);
    }

    @Override
    public LicenceVerificationResult verifyHospital(String licenceNumber, String gstin, String state, String name) {
        if (!LicenceNumbers.isValidHospitalNumber(LicenceNumbers.normalize(licenceNumber))) {
            return LicenceVerificationResult.rejected(LicenceVerificationResult.INVALID_NUMBER,
                    "Hospital licence number format is not recognised.");
        }
        return checkGstin(gstin, state);
    }

    // the same codes the signup code uses for a bad GSTIN
    private LicenceVerificationResult checkGstin(String gstin, String state) {
        return switch (GstinValidator.validate(gstin, state)) {
            case VALID -> LicenceVerificationResult.verifiedInDemoMode();
            case STATE_MISMATCH -> LicenceVerificationResult.rejected("GSTIN_STATE_MISMATCH",
                    "The GSTIN state code does not belong to the state entered.");
            case INVALID_FORMAT, UNKNOWN_STATE_CODE, INVALID_CHECK_CHARACTER -> LicenceVerificationResult.rejected("INVALID_GSTIN",
                    "Check the GSTIN: it is not valid.");
        };
    }
}
