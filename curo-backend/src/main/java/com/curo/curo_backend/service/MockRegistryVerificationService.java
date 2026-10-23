package com.curo.curo_backend.service;

import com.curo.curo_backend.model.LicenceRegistryEntry;
import com.curo.curo_backend.repository.LicenceRegistryRepository;
import com.curo.curo_backend.util.GstinValidator;
import com.curo.curo_backend.util.LicenceNumbers;
import com.curo.curo_backend.util.NameMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/** Verifies licences against the invented licence_registry collection. Active when licence.verification.mode=mock. */
@Service
@ConditionalOnProperty(name = "licence.verification.mode", havingValue = "mock", matchIfMissing = true)
public class MockRegistryVerificationService implements LicenceVerificationService {

    private static final String NOT_LISTED = "Licence number was not found in the licence registry. A SUPER_ADMIN will review it.";

    @Autowired
    private LicenceRegistryRepository registryRepository;

    @Override
    public LicenceVerificationResult verifyDoctor(String licenceNumber, String stateCouncil, String name) {
        String number = LicenceNumbers.normalize(licenceNumber);
        if (!LicenceNumbers.isValidDoctorNumber(number)) {
            return LicenceVerificationResult.rejected(LicenceVerificationResult.INVALID_NUMBER,
                    "Licence number format is not recognised.");
        }

        Optional<LicenceRegistryEntry> found = registryRepository.findByTypeAndLicenceNumber(LicenceRegistryEntry.DOCTOR, number);
        if (found.isEmpty()) {
            return LicenceVerificationResult.pending(LicenceVerificationResult.NOT_IN_REGISTRY, NOT_LISTED);
        }

        LicenceRegistryEntry entry = found.get();
        if (!NameMatcher.matches(name, entry.getName())) {
            return LicenceVerificationResult.rejected(LicenceVerificationResult.NAME_MISMATCH,
                    "The name does not match the one registered for this licence number.");
        }
        if (!NameMatcher.matches(stateCouncil, entry.getStateCouncil())) {
            return LicenceVerificationResult.rejected(LicenceVerificationResult.STATE_MISMATCH,
                    "The state council does not match the one registered for this licence number.");
        }
        return LicenceVerificationResult.verified();
    }

    @Override
    public LicenceVerificationResult verifyPharmacy(String drugLicenceNumber, String gstin, String state, String name) {
        String number = LicenceNumbers.normalize(drugLicenceNumber);
        if (!LicenceNumbers.isValidDrugLicenceNumber(number)) {
            return LicenceVerificationResult.rejected(LicenceVerificationResult.INVALID_NUMBER,
                    "Drug licence number format is not recognised.");
        }

        Optional<LicenceRegistryEntry> found = registryRepository.findByTypeAndLicenceNumber(LicenceRegistryEntry.PHARMACY, number);
        if (found.isEmpty()) {
            return LicenceVerificationResult.pending(LicenceVerificationResult.NOT_IN_REGISTRY, NOT_LISTED);
        }

        LicenceRegistryEntry entry = found.get();
        if (!NameMatcher.matches(name, entry.getName())) {
            return LicenceVerificationResult.rejected(LicenceVerificationResult.NAME_MISMATCH,
                    "The name does not match the one registered for this licence number.");
        }
        if (state == null || !state.trim().toUpperCase(Locale.ROOT).equals(entry.getState())) {
            return LicenceVerificationResult.rejected(LicenceVerificationResult.STATE_MISMATCH,
                    "The state does not match the one registered for this licence number.");
        }
        if (!java.util.Objects.equals(GstinValidator.normalize(gstin), entry.getGstin())) {
            return LicenceVerificationResult.rejected(LicenceVerificationResult.GSTIN_MISMATCH,
                    "The GSTIN does not match the one registered for this licence number.");
        }
        return LicenceVerificationResult.verified();
    }
}
