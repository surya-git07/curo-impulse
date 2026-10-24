package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.ActivePrescriptionView;
import com.curo.curo_backend.dto.ActivePrescriptionsResponse;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.AuditLog;
import com.curo.curo_backend.model.AuditOutcome;
import com.curo.curo_backend.model.DispenseStatus;
import com.curo.curo_backend.model.Frequency;
import com.curo.curo_backend.model.Pharmacy;
import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.model.PrescriptionStatus;
import com.curo.curo_backend.repository.DispensingLogRepository;
import com.curo.curo_backend.repository.PharmacyRepository;
import com.curo.curo_backend.support.PrescriptionHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Supplier;

import static com.curo.curo_backend.support.Fixtures.signIn;
import static com.curo.curo_backend.support.Fixtures.signOut;
import static com.curo.curo_backend.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The pharmacy lookup: only what can still be dispensed, only for a pharmacy, always on the patient access log. */
class PharmacyLookupServiceTest {

    private static final Instant T0 = PrescriptionHarness.T0;
    private static final String CARE_ID = "CID-7K3MQ9";

    private PrescriptionHarness h;
    private PharmacyLookupService service;
    private DispensingLogRepository logRepo;
    private PharmacyLookupLimiter limiter;
    private Pharmacy pharmacy;

    @BeforeEach
    void setUp() {
        h = new PrescriptionHarness();
        pharmacy = new Pharmacy();
        pharmacy.setId("pharm1");
        pharmacy.setName("Test Pharmacy");
        pharmacy.setVerified(true);
        h.pharmacy.setFacilityId("pharm1");
        PharmacyRepository pharmacyRepo = mock(PharmacyRepository.class);
        when(pharmacyRepo.findById(anyString())).thenAnswer(inv -> "pharm1".equals(inv.getArgument(0)) ? Optional.of(pharmacy) : Optional.empty());
        logRepo = mock(DispensingLogRepository.class);
        when(logRepo.existsByPrescriptionIdAndStatus(anyString(), any(DispenseStatus.class))).thenReturn(false);

        limiter = new PharmacyLookupLimiter();
        ReflectionTestUtils.setField(limiter, "maxFailures", 3);
        ReflectionTestUtils.setField(limiter, "windowMinutes", 15);
        ReflectionTestUtils.setField(limiter, "clock", h.clock);

        service = new PharmacyLookupService();
        ReflectionTestUtils.setField(service, "timeZone", "Asia/Kolkata");
        ReflectionTestUtils.setField(service, "currentUser", ReflectionTestUtils.getField(h.guard, "currentUser"));
        ReflectionTestUtils.setField(service, "pharmacyRepository", pharmacyRepo);
        ReflectionTestUtils.setField(service, "userRepository", h.userRepository);
        ReflectionTestUtils.setField(service, "prescriptionRepository", h.prescriptionRepository);
        ReflectionTestUtils.setField(service, "dispensingLogRepository", logRepo);
        ReflectionTestUtils.setField(service, "auditService", ReflectionTestUtils.getField(h.guard, "auditService"));
        ReflectionTestUtils.setField(service, "limiter", limiter);
        ReflectionTestUtils.setField(service, "clock", h.clock);
        signIn(h.pharmacy);
    }

    @AfterEach
    void tearDown() {
        signOut();
    }

    private Prescription rx(String drug, PrescriptionStatus status, int days) {
        return h.existing(drug, T0.minus(Duration.ofHours(1)), days, status);
    }

    private void assertApiError(Supplier<?> call, HttpStatus status, String code) {
        assertThatThrownBy(call::get).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    @Test
    void aPharmacyGetsTheActiveListWithOnlyTheHandOverFields() {
        Prescription p = rx("Amoxicillin", PrescriptionStatus.ACTIVE, 7);
        p.setFrequency(Frequency.TWICE_DAILY);
        p.setDuration("7 days");

        ActivePrescriptionsResponse res = service.activePrescriptions(CARE_ID);

        assertThat(res.getCareId()).isEqualTo(CARE_ID);
        assertThat(res.getPrescriptions()).hasSize(1);
        ActivePrescriptionView v = res.getPrescriptions().get(0);
        assertThat(v.getId()).isEqualTo(p.getId());
        assertThat(v.getDrug()).isEqualTo("Amoxicillin");
        assertThat(v.getDose()).isEqualTo("5 mg");
        assertThat(v.getFrequency()).isEqualTo(Frequency.TWICE_DAILY);
        assertThat(v.getDuration()).isEqualTo("7 days");
        assertThat(v.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(v.getDoctorName()).isEqualTo("Dr Test");
        assertThat(v.getDispenseStatus()).isEqualTo("NOT_DISPENSED");
    }

    @Test
    void theResponseCarriesNoAllergiesHistoryOrProfileData() {
        h.patient.setAllergies(java.util.List.of("Penicillin"));
        h.patient.setBloodGroup("B+");
        rx("Amoxicillin", PrescriptionStatus.ACTIVE, 7);

        // the response type has only the hand-over fields; this also checks nothing sensitive leaks into them
        String json = service.activePrescriptions(CARE_ID).toString();

        assertThat(json).doesNotContain("Penicillin").doesNotContain("allerg").doesNotContain("B+").doesNotContain("versions")
                .doesNotContain("phone").doesNotContain("Test Patient");
    }

    @Test
    void discontinuedDispensedExpiredAndLoggedPrescriptionsAreNotListed() {
        Prescription active = rx("Amoxicillin", PrescriptionStatus.ACTIVE, 7);
        rx("Ibuprofen", PrescriptionStatus.DISCONTINUED, 7);
        rx("Metformin", PrescriptionStatus.DISPENSED, 7);
        rx("Cetirizine", PrescriptionStatus.ACTIVE, 7).setIssuedAt(T0.minus(Duration.ofDays(20))); // course ended
        Prescription logged = rx("Warfarin", PrescriptionStatus.ACTIVE, 7);
        when(logRepo.existsByPrescriptionIdAndStatus(logged.getId(), DispenseStatus.DISPENSED)).thenReturn(true);

        var drugs = service.activePrescriptions(CARE_ID).getPrescriptions();

        assertThat(drugs).extracting(ActivePrescriptionView::getId).containsExactly(active.getId());
    }

    @Test
    void aPatientWithNothingActiveGetsAnEmptyList() {
        assertThat(service.activePrescriptions(CARE_ID).getPrescriptions()).isEmpty();
    }

    @Test
    void anUnknownOrNonPatientCareIdIs404AndNotAudited() {
        assertApiError(() -> service.activePrescriptions("CID-ZZZZZ2"), HttpStatus.NOT_FOUND, "PATIENT_NOT_FOUND");
        assertApiError(() -> service.activePrescriptions(h.doctor.getCareId()), HttpStatus.NOT_FOUND, "PATIENT_NOT_FOUND");
        assertApiError(() -> service.activePrescriptions("not a careid"), HttpStatus.NOT_FOUND, "PATIENT_NOT_FOUND");
        assertThat(h.audit).isEmpty();
    }

    @Test
    void everyLookupIsWrittenToThePatientAccessLog() {
        rx("Amoxicillin", PrescriptionStatus.ACTIVE, 7);

        service.activePrescriptions(" cid-7k3mq9 ");

        assertThat(h.audit).singleElement().satisfies((AuditLog a) -> {
            assertThat(a.getAction()).isEqualTo(AuditAction.PHARMACY_LOOKUP);
            assertThat(a.getActorRole()).isEqualTo("PHARMACY");
            assertThat(a.getActorUserId()).isEqualTo(h.pharmacy.getId());
            assertThat(a.getPatientId()).isEqualTo(h.patient.getId());
            assertThat(a.getCareId()).isEqualTo(CARE_ID);
            assertThat(a.getResource()).isEqualTo("PRESCRIPTIONS");
            assertThat(a.getOutcome()).isEqualTo(AuditOutcome.ALLOWED);
            assertThat(a.getAt()).isEqualTo(T0);
        });
    }

    @Test
    void ifTheLookupCannotBeRecordedNothingIsReturned() {
        var auditService = ReflectionTestUtils.getField(h.guard, "auditService");
        var repo = (com.curo.curo_backend.repository.AuditLogRepository) ReflectionTestUtils.getField(auditService, "auditLogRepository");
        when(repo.save(any(AuditLog.class))).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("down"));
        rx("Amoxicillin", PrescriptionStatus.ACTIVE, 7);

        assertApiError(() -> service.activePrescriptions(CARE_ID), HttpStatus.INTERNAL_SERVER_ERROR, "AUDIT_UNAVAILABLE");
    }

    @Test
    void onlyAPharmacyAccountMayLookUp() {
        for (AppUser other : new AppUser[]{user("d1x", "DOCTOR", "Dr", "CID-4HN8XD"), user("px", "PATIENT", "P", "CID-5PP5PP"),
                user("hx", "HOSPITAL", "H", "CID-6HH6HH"), user("rx", "RECEPTION", "R", "CID-7RR7RR"),
                user("cx", "CAREGIVER", "C", "CID-3CC3CC"), user("ax", "ADMIN", "A", "CID-8AA8AA")}) {
            h.users.put(other.getId(), other);
            signIn(other);
            assertApiError(() -> service.activePrescriptions(CARE_ID), HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
        }
        assertThat(h.audit).isEmpty();
    }

    @Test
    void aPharmacyWithNoVerifiedRecordIsRefused() {
        pharmacy.setVerified(false);
        assertApiError(() -> service.activePrescriptions(CARE_ID), HttpStatus.FORBIDDEN, "PHARMACY_NOT_VERIFIED");
        h.pharmacy.setFacilityId(null);
        assertApiError(() -> service.activePrescriptions(CARE_ID), HttpStatus.FORBIDDEN, "NO_PHARMACY");
        assertThat(h.audit).isEmpty();
    }

    @Test
    void repeatedLookupsOfUnknownCareIdsAreRateLimited() {
        for (int i = 0; i < 3; i++) {
            assertApiError(() -> service.activePrescriptions("CID-ZZZZZ2"), HttpStatus.NOT_FOUND, "PATIENT_NOT_FOUND");
        }
        assertApiError(() -> service.activePrescriptions("CID-ZZZZZ2"), HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS");
        // even a real CareID waits while the limit is on
        assertApiError(() -> service.activePrescriptions(CARE_ID), HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS");
        // and the window passes
        h.clock.advance(Duration.ofMinutes(16));
        assertThat(service.activePrescriptions(CARE_ID).getCareId()).isEqualTo(CARE_ID);
    }
}
