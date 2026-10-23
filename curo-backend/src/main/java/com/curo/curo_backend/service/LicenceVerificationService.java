package com.curo.curo_backend.service;

/**
 * Checks a professional licence. VERIFIED: matches the registry. PENDING: could not be confirmed,
 * a SUPER_ADMIN decides. REJECTED: contradicts the registry or is malformed.
 */
public interface LicenceVerificationService {

    LicenceVerificationResult verifyDoctor(String licenceNumber, String stateCouncil, String name);

    LicenceVerificationResult verifyPharmacy(String drugLicenceNumber, String gstin, String state, String name);
}
