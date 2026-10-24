package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.DiscontinueRequest;
import com.curo.curo_backend.dto.DispenseOrderRequest;
import com.curo.curo_backend.dto.DispenseView;
import com.curo.curo_backend.dto.DrugItem;
import com.curo.curo_backend.dto.IssueRequest;
import com.curo.curo_backend.dto.ModifyRequest;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.AuditOutcome;
import com.curo.curo_backend.model.DispenseStatus;
import com.curo.curo_backend.model.DispensingLog;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.GrantStatus;
import com.curo.curo_backend.model.Pharmacy;
import com.curo.curo_backend.model.PrescriptionStatus;
import com.curo.curo_backend.model.VerificationStatus;
import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.model.PrescriptionStatus;
import com.curo.curo_backend.model.Severity;
import com.curo.curo_backend.repository.DispensingLogRepository;
import com.curo.curo_backend.repository.PharmacyRepository;
import com.curo.curo_backend.repository.UserRepository;
import com.curo.curo_backend.support.PrescriptionHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static com.curo.curo_backend.support.Fixtures.signIn;
import static com.curo.curo_backend.support.Fixtures.signOut;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Dispensing marks the prescription DISPENSED and writes a log entry. There is no stock: nothing is checked or reduced. */
class DispenseServiceTest {

    private static final Instant T0 = PrescriptionHarness.T0;

    private PrescriptionHarness h;
    private DispenseService service;
    private Pharmacy pharmacy;
    private final List<DispensingLog> logs = new ArrayList<>();
    private DispensingLogRepository logRepo;
    private Prescription rx;

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
        when(logRepo.existsByPrescriptionIdAndStatus(anyString(), any(DispenseStatus.class))).thenAnswer(inv -> logs.stream()
                .anyMatch(l -> l.getPrescriptionId().equals(inv.getArgument(0)) && l.getStatus() == inv.getArgument(1)));
        when(logRepo.save(any(DispensingLog.class))).thenAnswer(inv -> {
            DispensingLog l = inv.getArgument(0);
            l.setId("log" + (logs.size() + 1));
            logs.add(l);
            return l;
        });

        service = new DispenseService();
        ReflectionTestUtils.setField(service, "timeZone", "Asia/Kolkata");
        ReflectionTestUtils.setField(service, "currentUser", ReflectionTestUtils.getField(h.guard, "currentUser"));
        ReflectionTestUtils.setField(service, "pharmacyRepository", pharmacyRepo);
        ReflectionTestUtils.setField(service, "prescriptionRepository", h.prescriptionRepository);
        ReflectionTestUtils.setField(service, "dispensingLogRepository", logRepo);
        ReflectionTestUtils.setField(service, "accessGuard", h.guard);
        ReflectionTestUtils.setField(service, "drugCatalog", ReflectionTestUtils.getField(h.prescriptionService, "drugCatalog"));
        ReflectionTestUtils.setField(service, "clock", h.clock);
        ReflectionTestUtils.setField(service, "userRepository", h.userRepository);

        rx = h.existing("Amoxicillin", T0.minus(Duration.ofHours(1)), 7, PrescriptionStatus.ACTIVE);
        h.users.get("d1").setVerificationStatus(VerificationStatus.VERIFIED);
        h.grantTo(h.pharmacy, GrantScope.PRESCRIPTIONS);
        signIn(h.pharmacy);
    }

    @AfterEach
    void tearDown() {
        signOut();
    }

    private DispenseOrderRequest order(int quantity) {
        DispenseOrderRequest r = new DispenseOrderRequest();
        r.setPrescriptionId(rx.getId());
        r.setQuantity(quantity);
        return r;
    }

    private void assertApiError(Supplier<?> call, HttpStatus status, String code) {
        assertThatThrownBy(call::get).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    // ---- the dispense

    @Test
    void dispensingMarksThePrescriptionDispensedAndWritesTheLog() {
        DispenseView view = service.dispense(order(14));

        assertThat(view.getStatus()).isEqualTo(DispenseStatus.DISPENSED);
        assertThat(view.getQtyDispensed()).isEqualTo(14);
        assertThat(view.getPrescriptionId()).isEqualTo(rx.getId());
        assertThat(view.getPharmacyId()).isEqualTo("pharm1");
        assertThat(view.getDrugName()).isEqualTo("Amoxicillin");
        assertThat(view.getCareId()).isEqualTo("CID-7K3MQ9");
        assertThat(view.getDispensedAt()).isEqualTo(T0);

        assertThat(rx.getStatus()).isEqualTo(PrescriptionStatus.DISPENSED);
        assertThat(rx.getDispensedAt()).isEqualTo(T0);
        assertThat(rx.getDispensedByPharmacyId()).isEqualTo("pharm1");
        assertThat(logs).singleElement().satisfies(l -> {
            assertThat(l.getStatus()).isEqualTo(DispenseStatus.DISPENSED);
            assertThat(l.getDispensedByUserId()).isEqualTo("ph1");
            assertThat(l.getQuantity()).isEqualTo(14);
            assertThat(l.getAt()).isEqualTo(T0);
            assertThat(l.getPharmacyId()).isEqualTo("pharm1");
        });
    }

    @Test
    void thereIsNoStockToCheckOrReduceAndAnyQuantityInRangeIsFine() {
        // the service has no stock dependency at all
        assertThat(DispenseService.class.getDeclaredFields()).extracting(java.lang.reflect.Field::getName)
                .noneMatch(n -> n.toLowerCase().contains("stock"));

        assertThat(service.dispense(order(10_000)).getQtyDispensed()).isEqualTo(10_000);
    }

    @Test
    void dispensingDoesNotAddAVersionOrChangeTheDose() {
        service.dispense(order(1));

        assertThat(rx.getVersions()).hasSize(1);
        assertThat(rx.getCurrentVersion()).isEqualTo(1);
        assertThat(rx.getDose()).isEqualTo("5 mg");
    }

    @Test
    void aDispenseIsAuditedAsAWriteOnThatPrescription() {
        service.dispense(order(1));

        assertThat(h.audit).filteredOn(e -> e.getOutcome() == AuditOutcome.ALLOWED).singleElement().satisfies(e -> {
            assertThat(e.getAction()).isEqualTo(AuditAction.WRITE);
            assertThat(e.getResource()).isEqualTo("DISPENSE");
            assertThat(e.getResourceId()).isEqualTo(rx.getId());
            assertThat(e.getActorUserId()).isEqualTo("ph1");
            assertThat(e.getPatientId()).isEqualTo("p1");
        });
    }

    // ---- double dispense

    @Test
    void aSecondDispenseIs409AndChangesNothing() {
        service.dispense(order(2));
        Instant firstAt = rx.getDispensedAt();
        h.clock.advance(Duration.ofMinutes(5));

        assertApiError(() -> service.dispense(order(2)), HttpStatus.CONFLICT, "ALREADY_DISPENSED");

        assertThat(logs).hasSize(1);
        assertThat(rx.getDispensedAt()).isEqualTo(firstAt);
    }

    @Test
    void aLogEntryAloneAlsoBlocksASecondDispense() {
        DispensingLog existing = new DispensingLog();
        existing.setPrescriptionId(rx.getId());
        existing.setStatus(DispenseStatus.DISPENSED);
        logs.add(existing);

        assertApiError(() -> service.dispense(order(1)), HttpStatus.CONFLICT, "ALREADY_DISPENSED");
        assertThat(rx.getStatus()).isEqualTo(PrescriptionStatus.ACTIVE);
    }

    @Test
    void ifTwoRequestsRaceTheDatabaseRefusesTheSecond() {
        when(logRepo.save(any(DispensingLog.class))).thenThrow(new DuplicateKeyException("prescriptionId"));

        assertApiError(() -> service.dispense(order(3)), HttpStatus.CONFLICT, "ALREADY_DISPENSED");
        assertThat(rx.getStatus()).as("the loser changes nothing").isEqualTo(PrescriptionStatus.ACTIVE);
    }

    @Test
    void ifThePrescriptionCannotBeMarkedTheLogEntryIsTakenBack() {
        when(h.prescriptionRepository.save(any(Prescription.class))).thenThrow(new DataAccessResourceFailureException("down"));

        assertThatThrownBy(() -> service.dispense(order(3))).isInstanceOf(DataAccessResourceFailureException.class);

        verify(logRepo).delete(any(DispensingLog.class));
    }

    // a copy as the database would hand it back on a second read: a different object, not the one already changed in memory
    private Prescription freshCopy(PrescriptionStatus status) {
        Prescription c = new Prescription();
        c.setId(rx.getId());
        c.setCareId(rx.getCareId());
        c.setDrug(rx.getDrug());
        c.setDurationDays(rx.getDurationDays());
        c.setIssuedAt(rx.getIssuedAt());
        c.setStatus(status);
        return c;
    }

    private void secondReadReturns(Prescription copy) {
        when(h.prescriptionRepository.findById(rx.getId())).thenReturn(Optional.of(rx)).thenReturn(Optional.of(copy));
    }

    @Test
    void ifTheDoctorChangedItAtTheSameMomentTheDispenseRetriesOnTheFreshCopy() {
        Prescription copy = freshCopy(PrescriptionStatus.ACTIVE);
        secondReadReturns(copy);
        int[] saves = {0};
        when(h.prescriptionRepository.save(any(Prescription.class))).thenAnswer(inv -> {
            if (saves[0]++ == 0) {
                throw new OptimisticLockingFailureException("changed");
            }
            return inv.getArgument(0);
        });

        DispenseView view = service.dispense(order(3));

        assertThat(view.getStatus()).isEqualTo(DispenseStatus.DISPENSED);
        assertThat(saves[0]).isEqualTo(2);
        assertThat(copy.getStatus()).as("the fresh copy is the one that was saved as dispensed").isEqualTo(PrescriptionStatus.DISPENSED);
        assertThat(copy.getDispensedAt()).isEqualTo(T0);
        assertThat(logs).hasSize(1);
    }

    @Test
    void ifTheDoctorDiscontinuedItInTheMeantimeTheDispenseFailsAndIsTakenBack() {
        secondReadReturns(freshCopy(PrescriptionStatus.DISCONTINUED));
        when(h.prescriptionRepository.save(any(Prescription.class))).thenThrow(new OptimisticLockingFailureException("changed"));

        assertApiError(() -> service.dispense(order(3)), HttpStatus.CONFLICT, "INVALID_STATE");

        verify(logRepo).delete(any(DispensingLog.class));
    }

    // ---- issuer and course checks (new validation added before the existing drug match)

    private Prescription freshRx() {
        return h.existing("Amoxicillin", T0.minus(Duration.ofHours(1)), 7, PrescriptionStatus.ACTIVE);
    }

    @Test
    void aDiscontinuedPrescriptionThrowsNotActive() {
        rx = freshRx();
        h.users.get("d1").setVerificationStatus(VerificationStatus.VERIFIED);
        h.grantTo(h.pharmacy, GrantScope.PRESCRIPTIONS);
        signIn(h.pharmacy);
        rx.setStatus(PrescriptionStatus.DISCONTINUED);
        assertApiError(() -> service.dispense(order(1)), HttpStatus.CONFLICT, "PRESCRIPTION_NOT_ACTIVE");
        assertThat(logs).isEmpty();
    }

    @Test
    void anExpiredPrescriptionThrowsExpired() {
        rx = freshRx();
        h.clock.advance(Duration.ofDays(8)); // past the 7-day course
        h.users.get("d1").setVerificationStatus(VerificationStatus.VERIFIED);
        // the default test grant ends a day after the start, so grant again for long enough to reach the course check
        h.grantTo(h.pharmacy, GrantStatus.ACTIVE, T0.plus(Duration.ofDays(30)), GrantScope.PRESCRIPTIONS);
        signIn(h.pharmacy);
        assertApiError(() -> service.dispense(order(1)), HttpStatus.CONFLICT, "PRESCRIPTION_EXPIRED");
        assertThat(logs).isEmpty();
    }

    @Test
    void anUnverifiedIssuerThrowsDoctorNotVerified() {
        rx = freshRx();
        h.users.get("d1").setVerificationStatus(VerificationStatus.PENDING);
        h.grantTo(h.pharmacy, GrantScope.PRESCRIPTIONS);
        signIn(h.pharmacy);
        assertApiError(() -> service.dispense(order(1)), HttpStatus.CONFLICT, "DOCTOR_NOT_VERIFIED");
        assertThat(logs).isEmpty();
    }

    @Test
    void anIssuerWhoseLicenceIsVerifiedCanDispense() {
        rx = freshRx();
        h.users.get("d1").setVerificationStatus(VerificationStatus.VERIFIED);
        h.grantTo(h.pharmacy, GrantScope.PRESCRIPTIONS);
        signIn(h.pharmacy);

        DispenseView view = service.dispense(order(1));
        assertThat(view.getStatus()).isEqualTo(DispenseStatus.DISPENSED);
        assertThat(rx.getStatus()).isEqualTo(PrescriptionStatus.DISPENSED);

        assertApiError(() -> service.dispense(order(1)), HttpStatus.CONFLICT, "ALREADY_DISPENSED");
        assertThat(rx.getStatus()).isEqualTo(PrescriptionStatus.DISPENSED);
        assertThat(logs).hasSize(1);
    }

    @Test
    void anUnknownPrescriptionIsNotFound() {
        DispenseOrderRequest r = order(1);
        r.setPrescriptionId("no-such-id");
        assertApiError(() -> service.dispense(r), HttpStatus.NOT_FOUND, "PRESCRIPTION_NOT_FOUND");
        assertThat(logs).isEmpty();
    }

    @Test
    void aPrescriptionOfAnotherCareIdIsNotFoundAndNothingIsRecorded() {
        DispenseOrderRequest r = order(1);
        r.setCareId("CID-ZZZZZ2"); // not this prescription's patient
        int auditBefore = h.audit.size();
        assertApiError(() -> service.dispense(r), HttpStatus.NOT_FOUND, "PRESCRIPTION_NOT_FOUND");
        assertThat(logs).isEmpty();
        assertThat(rx.getStatus()).isEqualTo(PrescriptionStatus.ACTIVE);
        // refused before the grant check, so it reveals nothing and writes nothing
        assertThat(h.audit).hasSize(auditBefore);
    }

    @Test
    void theRightCareIdIsAcceptedInAnyCase() {
        DispenseOrderRequest r = order(1);
        r.setCareId(" cid-7k3mq9 ");
        assertThat(service.dispense(r).getStatus()).isEqualTo(DispenseStatus.DISPENSED);
    }

    @Test
    void anAlreadyDispensedPrescriptionSaysSoEvenIfTheIssuerIsNoLongerVerified() {
        service.dispense(order(1));
        h.users.get("d1").setVerificationStatus(VerificationStatus.PENDING);
        assertApiError(() -> service.dispense(order(1)), HttpStatus.CONFLICT, "ALREADY_DISPENSED");
        assertThat(logs).hasSize(1);
    }

    // ---- state and request checks (back to the original class)

    @Test
    void aDiscontinuedOrFinishedPrescriptionCannotBeDispensed() {
        rx.setStatus(PrescriptionStatus.DISCONTINUED);
        assertApiError(() -> service.dispense(order(1)), HttpStatus.CONFLICT, "PRESCRIPTION_NOT_ACTIVE");

        rx.setStatus(PrescriptionStatus.ACTIVE);
        h.clock.advance(Duration.ofDays(8)); // a 7-day course issued yesterday has ended
        h.grants.clear();
        h.grantTo(h.pharmacy, GrantStatus.ACTIVE, T0.plus(Duration.ofDays(30)), GrantScope.PRESCRIPTIONS);
        assertApiError(() -> service.dispense(order(1)), HttpStatus.CONFLICT, "PRESCRIPTION_EXPIRED");
        assertThat(logs).isEmpty();
    }

    @Test
    void theDrugSentMustBeTheDrugOnThePrescription() {
        DispenseOrderRequest wrong = order(1);
        wrong.setDrug("Warfarin");
        assertApiError(() -> service.dispense(wrong), HttpStatus.BAD_REQUEST, "DRUG_MISMATCH");

        DispenseOrderRequest alias = order(1);
        alias.setDrug("  AMOXICILLIN ");
        assertThat(service.dispense(alias).getStatus()).isEqualTo(DispenseStatus.DISPENSED);
    }

    @Test
    void requestsAreValidated() {
        assertApiError(() -> service.dispense(order(0)), HttpStatus.BAD_REQUEST, "INVALID_QUANTITY");
        assertApiError(() -> service.dispense(order(-3)), HttpStatus.BAD_REQUEST, "INVALID_QUANTITY");
        assertApiError(() -> service.dispense(order(10_001)), HttpStatus.BAD_REQUEST, "INVALID_QUANTITY");
        DispenseOrderRequest noQuantity = order(1);
        noQuantity.setQuantity(null);
        assertApiError(() -> service.dispense(noQuantity), HttpStatus.BAD_REQUEST, "INVALID_QUANTITY");
        DispenseOrderRequest noPrescription = order(1);
        noPrescription.setPrescriptionId(" ");
        assertApiError(() -> service.dispense(noPrescription), HttpStatus.BAD_REQUEST, "PRESCRIPTION_REQUIRED");
        assertApiError(() -> service.dispense(null), HttpStatus.BAD_REQUEST, "PRESCRIPTION_REQUIRED");
        DispenseOrderRequest unknown = order(1);
        unknown.setPrescriptionId("nope");
        assertApiError(() -> service.dispense(unknown), HttpStatus.NOT_FOUND, "PRESCRIPTION_NOT_FOUND");
        assertThat(rx.getStatus()).isEqualTo(PrescriptionStatus.ACTIVE);
        assertThat(logs).isEmpty();
    }

    // ---- own pharmacy only

    @Test
    void aPharmacyIdThatIsNotYoursIsRefusedWhetherOrNotItExists() {
        for (String other : new String[]{"pharm2", "nope", "pharm1x"}) {
            DispenseOrderRequest r = order(1);
            r.setPharmacyId(other);
            assertApiError(() -> service.dispense(r), HttpStatus.FORBIDDEN, "FORBIDDEN");
        }
        assertThat(rx.getStatus()).isEqualTo(PrescriptionStatus.ACTIVE);
        assertThat(logs).isEmpty();
    }

    @Test
    void yourOwnPharmacyIdIsAcceptedAndOmittingItMeansTheSame() {
        DispenseOrderRequest withId = order(1);
        withId.setPharmacyId(" pharm1 ");
        assertThat(service.dispense(withId).getPharmacyId()).isEqualTo("pharm1");
    }

    @Test
    void theLogAlwaysRecordsTheSignedInPharmacistsOwnPharmacy() {
        service.dispense(order(1));

        assertThat(logs.get(0).getPharmacyId()).isEqualTo(h.pharmacy.getFacilityId());
    }

    @Test
    void aPharmacistWithNoPharmacyOrAnUnverifiedOneIsRefused() {
        h.pharmacy.setFacilityId(null);
        assertApiError(() -> service.dispense(order(1)), HttpStatus.FORBIDDEN, "NO_PHARMACY");

        h.pharmacy.setFacilityId("pharm1");
        pharmacy.setVerified(false);
        assertApiError(() -> service.dispense(order(1)), HttpStatus.FORBIDDEN, "PHARMACY_NOT_VERIFIED");
        assertThat(logs).isEmpty();
    }

    @Test
    void onlyPharmaciesDispense() {
        for (AppUser other : List.of(h.doctor, h.patient, h.caregiver)) {
            signIn(other);
            assertApiError(() -> service.dispense(order(1)), HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
        }
        assertThat(rx.getStatus()).isEqualTo(PrescriptionStatus.ACTIVE);
        assertThat(logs).isEmpty();
    }

    // ---- the consent gate

    @Test
    void dispensingNeedsAGrantOnThePatientAndAnythingElseIs403NoActiveGrant() {
        for (Runnable arrange : List.<Runnable>of(
                () -> h.grants.clear(),
                () -> { h.grants.clear(); h.grantTo(h.pharmacy, GrantStatus.ACTIVE, T0.minusSeconds(1), GrantScope.PRESCRIPTIONS); },
                () -> { h.grants.clear(); h.grantTo(h.pharmacy, GrantStatus.REVOKED, T0.plus(Duration.ofDays(1)), GrantScope.PRESCRIPTIONS); },
                () -> { h.grants.clear(); h.grantTo(h.pharmacy, GrantStatus.PENDING, null, GrantScope.PRESCRIPTIONS); },
                () -> { h.grants.clear(); h.grantTo(h.pharmacy, GrantScope.PROFILE, GrantScope.MEDICATIONS); })) {
            arrange.run();
            assertApiError(() -> service.dispense(order(1)), HttpStatus.FORBIDDEN, "NO_ACTIVE_GRANT");
            assertThat(rx.getStatus()).isEqualTo(PrescriptionStatus.ACTIVE);
            assertThat(logs).isEmpty();
        }
        assertThat(h.audit).anySatisfy(e -> assertThat(e.getOutcome()).isEqualTo(AuditOutcome.DENIED));
    }

    @Test
    void anotherPharmacysGrantDoesNotCount() {
        h.grants.clear();
        h.users.put("ph2", com.curo.curo_backend.support.Fixtures.user("ph2", "PHARMACY", "Other Pharmacy", "CID-OTHPH2"));
        h.grantTo(h.users.get("ph2"), GrantScope.PRESCRIPTIONS);

        assertApiError(() -> service.dispense(order(1)), HttpStatus.FORBIDDEN, "NO_ACTIVE_GRANT");
    }

    // ---- DISPENSED is still a current medication

    @Test
    void aDispensedPrescriptionIsStillACurrentMedication() {
        service.dispense(order(14));
        h.grantTo(h.doctor, GrantScope.MEDICATIONS);
        signIn(h.doctor);

        var meds = h.medicationService.forPatient("CID-7K3MQ9").getMedications();

        assertThat(meds).singleElement().satisfies(m -> {
            assertThat(m.getDrug()).isEqualTo("Amoxicillin");
            assertThat(m.getStatus()).isEqualTo(PrescriptionStatus.DISPENSED);
        });
    }

    @Test
    void theInteractionCheckStillSeesADispensedDrug() {
        Prescription warfarin = h.existing("Warfarin", T0.minus(Duration.ofHours(1)), 30, PrescriptionStatus.ACTIVE);
        rx = warfarin;
        service.dispense(order(30));
        h.grantTo(h.doctor, GrantScope.PRESCRIPTIONS);
        signIn(h.doctor);
        DrugItem aspirin = new DrugItem();
        aspirin.setDrug("Aspirin");
        aspirin.setDose("75 mg");
        IssueRequest issue = new IssueRequest();
        issue.setCareId("CID-7K3MQ9");
        issue.setDrugs(List.of(aspirin));

        var response = h.prescriptionService.issue(issue);

        assertThat(response.getWarnings()).singleElement().satisfies(w -> assertThat(w.getSeverity()).isEqualTo(Severity.HIGH));
    }

    @Test
    void theDoctorCanStillChangeOrStopADispensedPrescription() {
        service.dispense(order(14));
        h.grantTo(h.doctor, GrantScope.PRESCRIPTIONS);
        signIn(h.doctor);

        ModifyRequest modify = new ModifyRequest();
        modify.setDose("250 mg");
        modify.setReason("lower dose");
        var modified = h.prescriptionService.modify(rx.getId(), modify);
        assertThat(modified.getDose()).isEqualTo("250 mg");
        assertThat(rx.getStatus()).as("stays dispensed").isEqualTo(PrescriptionStatus.DISPENSED);

        DiscontinueRequest stop = new DiscontinueRequest();
        stop.setReason("stopped");
        assertThat(h.prescriptionService.discontinue(rx.getId(), stop).getStatus()).isEqualTo(PrescriptionStatus.DISCONTINUED);
        assertThat(rx.getDispensedAt()).as("the fact it was dispensed is kept").isEqualTo(T0);
        verify(logRepo, never()).delete(any(DispensingLog.class));
    }

    @Test
    void aDiscontinuedPrescriptionIsNotACurrentMedication() {
        service.dispense(order(14));
        rx.setStatus(PrescriptionStatus.DISCONTINUED);
        h.grantTo(h.doctor, GrantScope.MEDICATIONS);
        signIn(h.doctor);

        assertThat(h.medicationService.forPatient("CID-7K3MQ9").getMedications()).isEmpty();
    }
}
