package com.curo.curo_backend.service;

import com.curo.curo_backend.model.LicenceRegistryEntry;
import com.curo.curo_backend.model.VerificationStatus;
import com.curo.curo_backend.repository.LicenceRegistryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MockRegistryVerificationServiceTest {

    private MockRegistryVerificationService service;

    @BeforeEach
    void setUp() {
        LicenceRegistryRepository repo = mock(LicenceRegistryRepository.class);
        // behaves like the seeded collection
        when(repo.findByTypeAndLicenceNumber(anyString(), anyString())).thenAnswer(inv -> LicenceRegistrySeed.entries().stream()
                .filter(e -> e.getType().equals(inv.getArgument(0)) && e.getLicenceNumber().equals(inv.getArgument(1)))
                .findFirst());
        service = new MockRegistryVerificationService();
        ReflectionTestUtils.setField(service, "registryRepository", repo);
    }

    private void assertResult(LicenceVerificationResult r, VerificationStatus status, String code) {
        assertThat(r.getStatus()).isEqualTo(status);
        assertThat(r.getCode()).isEqualTo(code);
        assertThat(r.getMessage()).isNotBlank();
    }

    // ---- doctors

    @Test
    void doctorMatchIsVerified() {
        assertResult(service.verifyDoctor("TN-48213", "Tamil Nadu Medical Council", "Anjali Varadarajan"),
                VerificationStatus.VERIFIED, "LICENCE_MATCHED");
    }

    @Test
    void doctorMatchIgnoresCaseSpacingAndTitle() {
        assertResult(service.verifyDoctor("  tn-48213 ", "tamil nadu  medical council", "DR. anjali varadarajan"),
                VerificationStatus.VERIFIED, "LICENCE_MATCHED");
    }

    @Test
    void doctorWithTheWrongNameIsRejected() {
        assertResult(service.verifyDoctor("TN-48213", "Tamil Nadu Medical Council", "Someone Else"),
                VerificationStatus.REJECTED, "LICENCE_NAME_MISMATCH");
    }

    @Test
    void doctorWithTheWrongCouncilIsRejected() {
        assertResult(service.verifyDoctor("TN-48213", "Karnataka Medical Council", "Anjali Varadarajan"),
                VerificationStatus.REJECTED, "LICENCE_STATE_MISMATCH");
    }

    @Test
    void doctorNumberNotInTheRegistryIsPending() {
        assertResult(service.verifyDoctor("TN-99999", "Tamil Nadu Medical Council", "Anjali Varadarajan"),
                VerificationStatus.PENDING, "LICENCE_NOT_IN_REGISTRY");
    }

    @Test
    void malformedDoctorNumberIsRejected() {
        assertResult(service.verifyDoctor("TN 48213", "Tamil Nadu Medical Council", "Anjali Varadarajan"),
                VerificationStatus.REJECTED, "INVALID_LICENCE_NUMBER");
        assertResult(service.verifyDoctor("ab", "Tamil Nadu Medical Council", "Anjali Varadarajan"),
                VerificationStatus.REJECTED, "INVALID_LICENCE_NUMBER");
        assertResult(service.verifyDoctor(null, "Tamil Nadu Medical Council", "Anjali Varadarajan"),
                VerificationStatus.REJECTED, "INVALID_LICENCE_NUMBER");
    }

    @Test
    void aPharmacyNumberDoesNotVerifyADoctor() {
        assertResult(service.verifyDoctor("TN/CHE/20B-48213", "Tamil Nadu Medical Council", "Sri Velan Medicals"),
                VerificationStatus.PENDING, "LICENCE_NOT_IN_REGISTRY");
    }

    // ---- pharmacies

    @Test
    void pharmacyMatchIsVerified() {
        assertResult(service.verifyPharmacy("TN/CHE/20B-48213", "33AAGFS4821K1Z5", "TN", "Sri Velan Medicals"),
                VerificationStatus.VERIFIED, "LICENCE_MATCHED");
        assertResult(service.verifyPharmacy("dl/20b-5102/2021", "07aajcy5102g1ze", "dl", "yamuna wellness pharmacy"),
                VerificationStatus.VERIFIED, "LICENCE_MATCHED");
    }

    @Test
    void pharmacyWithTheWrongNameIsRejected() {
        assertResult(service.verifyPharmacy("TN/CHE/20B-48213", "33AAGFS4821K1Z5", "TN", "Other Chemists"),
                VerificationStatus.REJECTED, "LICENCE_NAME_MISMATCH");
    }

    @Test
    void pharmacyWithTheWrongStateIsRejected() {
        assertResult(service.verifyPharmacy("TN/CHE/20B-48213", "33AAGFS4821K1Z5", "KA", "Sri Velan Medicals"),
                VerificationStatus.REJECTED, "LICENCE_STATE_MISMATCH");
    }

    @Test
    void pharmacyWithADifferentGstinIsRejected() {
        assertResult(service.verifyPharmacy("TN/CHE/20B-48213", "33AAGFS4821K1Z5".replace("S4821K", "S4822K"), "TN", "Sri Velan Medicals"),
                VerificationStatus.REJECTED, "LICENCE_GSTIN_MISMATCH");
        assertResult(service.verifyPharmacy("TN/CHE/20B-48213", null, "TN", "Sri Velan Medicals"),
                VerificationStatus.REJECTED, "LICENCE_GSTIN_MISMATCH");
    }

    @Test
    void pharmacyNumberNotInTheRegistryIsPending() {
        assertResult(service.verifyPharmacy("TN/CHE/20B-00000", "33AAGFS4821K1Z5", "TN", "Sri Velan Medicals"),
                VerificationStatus.PENDING, "LICENCE_NOT_IN_REGISTRY");
    }

    @Test
    void malformedPharmacyNumberIsRejected() {
        assertResult(service.verifyPharmacy("12 34", "33AAGFS4821K1Z5", "TN", "Sri Velan Medicals"),
                VerificationStatus.REJECTED, "INVALID_LICENCE_NUMBER");
    }

    @Test
    void messagesNeverRepeatRegistryData() {
        LicenceVerificationResult nameMismatch = service.verifyDoctor("TN-48213", "Tamil Nadu Medical Council", "Someone Else");
        LicenceVerificationResult gstinMismatch = service.verifyPharmacy("TN/CHE/20B-48213", "33AAGFS4822K1Z5", "TN", "Sri Velan Medicals");
        for (LicenceVerificationResult r : new LicenceVerificationResult[]{nameMismatch, gstinMismatch}) {
            assertThat(r.getMessage()).doesNotContain("Varadarajan", "Anjali", "Velan", "33AAGFS4821K1Z5");
        }
    }

    @Test
    void registryLookupIsByTypeSoLookupMissIsPendingNotAnError() {
        LicenceRegistryRepository empty = mock(LicenceRegistryRepository.class);
        when(empty.findByTypeAndLicenceNumber(anyString(), anyString())).thenReturn(Optional.<LicenceRegistryEntry>empty());
        ReflectionTestUtils.setField(service, "registryRepository", empty);

        assertThat(service.verifyDoctor("TN-48213", "Tamil Nadu Medical Council", "Anjali Varadarajan").getStatus())
                .isEqualTo(VerificationStatus.PENDING);
    }
}
