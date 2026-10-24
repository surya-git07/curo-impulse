package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.DoctorActivePrescriptionsResponse;
import com.curo.curo_backend.dto.DoctorPrescriptionItem;
import com.curo.curo_backend.dto.DrugItem;
import com.curo.curo_backend.dto.ExtendRequest;
import com.curo.curo_backend.dto.IssueRequest;
import com.curo.curo_backend.dto.PrescriptionView;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.AuditOutcome;
import com.curo.curo_backend.model.ChangeType;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.model.PrescriptionStatus;
import com.curo.curo_backend.support.PrescriptionHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import static com.curo.curo_backend.support.Fixtures.signIn;
import static com.curo.curo_backend.support.Fixtures.signOut;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Extending a course: the end date moves later, the change is a new version, the doctor needs an active grant, and every
 * extension is on the patient access log. "Today" is 23 Oct 2026 in Indian time.
 */
class PrescriptionExtendTest {

    private static final String CARE_ID = "CID-7K3MQ9";

    private PrescriptionHarness h;
    private PrescriptionService service;
    private Prescription rx;

    @BeforeEach
    void setUp() {
        h = new PrescriptionHarness();
        service = h.prescriptionService;
        h.grantTo(h.doctor, GrantScope.PRESCRIPTIONS);
        // issued today, 7 days: the last day is 29 Oct
        rx = h.existing("Amoxicillin", PrescriptionHarness.T0.minus(Duration.ofHours(1)), 7, PrescriptionStatus.ACTIVE);
    }

    @AfterEach
    void tearDown() {
        signOut();
    }

    private ExtendRequest days(int extraDays) {
        ExtendRequest r = new ExtendRequest();
        r.setExtraDays(extraDays);
        return r;
    }

    private ExtendRequest until(String date) {
        ExtendRequest r = new ExtendRequest();
        r.setNewEndDate(date);
        return r;
    }

    private void assertApiError(Supplier<?> call, HttpStatus status, String code) {
        assertThatThrownBy(call::get).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    // ---- it works with a grant

    @Test
    void extraDaysMoveTheEndDateAndKeepTheHistory() {
        PrescriptionView view = service.extend(rx.getId(), days(3));

        assertThat(view.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(view.getDurationDays()).isEqualTo(10);
        assertThat(view.getDuration()).isEqualTo("10 days");
        assertThat(view.getDaysLeft()).isEqualTo(9);
        assertThat(view.getStatus()).isEqualTo(PrescriptionStatus.ACTIVE);
        assertThat(view.getCurrentVersion()).isEqualTo(2);

        assertThat(view.getVersions()).hasSize(2);
        var v2 = view.getVersions().get(1);
        assertThat(v2.getChangeType()).isEqualTo(ChangeType.EXTENDED);
        assertThat(v2.getPreviousEndDate()).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(v2.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(v2.getChangedBy().getUserId()).isEqualTo("d1");
        assertThat(v2.getChangedBy().getName()).isEqualTo("Dr Test");
        assertThat(v2.getChangedAt()).isEqualTo(PrescriptionHarness.T0);
        // the first version is left exactly as it was
        assertThat(view.getVersions().get(0).getChangeType()).isEqualTo(ChangeType.ISSUED);
        assertThat(view.getVersions().get(0).getPreviousEndDate()).isNull();
        assertThat(h.prescriptions.get(rx.getId()).getVersions()).hasSize(2);
    }

    @Test
    void aNewEndDateWorksToo() {
        PrescriptionView view = service.extend(rx.getId(), until("2026-11-05"));

        assertThat(view.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(view.getDurationDays()).isEqualTo(14);
        assertThat(view.getVersions().get(1).getReason()).isNull();
    }

    @Test
    void theReasonIsKeptWhenGiven() {
        ExtendRequest r = days(2);
        r.setReason("  Review on day 5: still infected  ");

        assertThat(service.extend(rx.getId(), r).getVersions().get(1).getReason()).isEqualTo("Review on day 5: still infected");
    }

    @Test
    void theDosesStillAheadRunToTheNewEndDate() {
        service.extend(rx.getId(), days(3));

        // one dose a day from 24 Oct to 1 Nov (today's 08:00 dose has already passed)
        assertThat(h.doseLogs).hasSize(9);
    }

    @Test
    void aPrescriptionWithNoEndDateTakesTheNewEndDateAsItsEndDate() {
        Prescription open = h.existing("Metformin", PrescriptionHarness.T0.minus(Duration.ofHours(1)), null, PrescriptionStatus.ACTIVE);

        PrescriptionView view = service.extend(open.getId(), until("2026-11-05"));

        assertThat(view.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(view.getDurationDays()).isEqualTo(14);
        var v = view.getVersions().get(1);
        assertThat(v.getPreviousEndDate()).isNull();
        assertThat(v.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 5));
    }

    @Test
    void extraDaysNeedAnEndDateToAddTo() {
        Prescription open = h.existing("Metformin", PrescriptionHarness.T0.minus(Duration.ofHours(1)), null, PrescriptionStatus.ACTIVE);

        assertApiError(() -> service.extend(open.getId(), days(3)), HttpStatus.BAD_REQUEST, "END_DATE_REQUIRED");
        assertThat(open.getVersions()).hasSize(1);
    }

    @Test
    void anotherDoctorWithAGrantMayExtendItToo() {
        h.grantTo(h.otherDoctor, GrantScope.PRESCRIPTIONS);
        signIn(h.otherDoctor);

        PrescriptionView view = service.extend(rx.getId(), days(1));

        assertThat(view.getVersions().get(1).getChangedBy().getUserId()).isEqualTo("d2");
    }

    // ---- no grant, no extension

    @Test
    void withoutAGrantItIsRefusedAndNothingChanges() {
        h.grants.clear();

        assertApiError(() -> service.extend(rx.getId(), days(3)), HttpStatus.FORBIDDEN, "NO_ACTIVE_GRANT");

        assertThat(rx.getDurationDays()).isEqualTo(7);
        assertThat(rx.getVersions()).hasSize(1);
        assertThat(h.audit).extracting(a -> a.getAction(), a -> a.getOutcome())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(AuditAction.WRITE, AuditOutcome.DENIED));
    }

    @Test
    void aGrantThatDoesNotCoverPrescriptionsIsNotEnough() {
        h.grants.clear();
        h.grantTo(h.doctor, GrantScope.MEDICATIONS);

        assertApiError(() -> service.extend(rx.getId(), days(3)), HttpStatus.FORBIDDEN, "NO_ACTIVE_GRANT");
    }

    @Test
    void anExpiredGrantIsNotEnough() {
        h.clock.advance(Duration.ofDays(2));

        assertApiError(() -> service.extend(rx.getId(), days(3)), HttpStatus.FORBIDDEN, "NO_ACTIVE_GRANT");
    }

    // ---- the new end date must be later

    @Test
    void anEarlierEndDateIsRefused() {
        assertApiError(() -> service.extend(rx.getId(), until("2026-10-28")), HttpStatus.BAD_REQUEST, "END_DATE_NOT_LATER");
        // the current end date itself is not later either
        assertApiError(() -> service.extend(rx.getId(), until("2026-10-29")), HttpStatus.BAD_REQUEST, "END_DATE_NOT_LATER");
        assertThat(rx.getDurationDays()).isEqualTo(7);
        assertThat(rx.getVersions()).hasSize(1);
    }

    @Test
    void todayOrThePastIsRefusedEvenWithoutACurrentEndDate() {
        Prescription open = h.existing("Metformin", PrescriptionHarness.T0.minus(Duration.ofHours(1)), null, PrescriptionStatus.ACTIVE);

        assertApiError(() -> service.extend(open.getId(), until("2026-10-23")), HttpStatus.BAD_REQUEST, "END_DATE_NOT_LATER");
        assertApiError(() -> service.extend(open.getId(), until("2026-10-01")), HttpStatus.BAD_REQUEST, "END_DATE_NOT_LATER");
        assertThat(open.getDurationDays()).isNull();
    }

    @Test
    void aCourseThatAlreadyEndedMustReachPastTodayToBeExtended() {
        h.grants.clear();
        h.grantTo(h.doctor, com.curo.curo_backend.model.GrantStatus.ACTIVE, PrescriptionHarness.T0.plus(Duration.ofDays(30)),
                GrantScope.PRESCRIPTIONS);
        h.clock.advance(Duration.ofDays(10)); // 2 Nov: the course ended on 29 Oct

        assertApiError(() -> service.extend(rx.getId(), days(2)), HttpStatus.BAD_REQUEST, "END_DATE_NOT_LATER"); // 31 Oct
        assertThat(service.extend(rx.getId(), until("2026-11-10")).getEndDate()).isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    void theRequestMustBeOneOfTheTwoForms() {
        assertApiError(() -> service.extend(rx.getId(), new ExtendRequest()), HttpStatus.BAD_REQUEST, "INVALID_EXTENSION");
        ExtendRequest both = days(3);
        both.setNewEndDate("2026-11-05");
        assertApiError(() -> service.extend(rx.getId(), both), HttpStatus.BAD_REQUEST, "INVALID_EXTENSION");
        assertApiError(() -> service.extend(rx.getId(), days(0)), HttpStatus.BAD_REQUEST, "INVALID_EXTENSION");
        assertApiError(() -> service.extend(rx.getId(), days(-2)), HttpStatus.BAD_REQUEST, "INVALID_EXTENSION");
        assertApiError(() -> service.extend(rx.getId(), until("next week")), HttpStatus.BAD_REQUEST, "INVALID_END_DATE");
        assertApiError(() -> service.extend(rx.getId(), until("2027-12-31")), HttpStatus.BAD_REQUEST, "COURSE_TOO_LONG");
        assertThat(rx.getVersions()).hasSize(1);
    }

    // ---- states that cannot be extended

    @Test
    void aDiscontinuedPrescriptionIsRefused() {
        rx.setStatus(PrescriptionStatus.DISCONTINUED);

        assertApiError(() -> service.extend(rx.getId(), days(3)), HttpStatus.CONFLICT, "PRESCRIPTION_DISCONTINUED");
        assertThat(rx.getVersions()).hasSize(1);
    }

    @Test
    void aDispensedPrescriptionIsRefused() {
        rx.setStatus(PrescriptionStatus.DISPENSED);

        assertApiError(() -> service.extend(rx.getId(), days(3)), HttpStatus.CONFLICT, "PRESCRIPTION_DISPENSED");
        assertThat(rx.getVersions()).hasSize(1);
    }

    @Test
    void anUnknownPrescriptionIs404() {
        assertApiError(() -> service.extend("nope", days(3)), HttpStatus.NOT_FOUND, "PRESCRIPTION_NOT_FOUND");
    }

    // ---- other roles

    @Test
    void onlyADoctorMayExtendOrListForExtension() {
        for (AppUser other : List.of(h.pharmacy, h.patient, h.caregiver)) {
            signIn(other);
            assertApiError(() -> service.extend(rx.getId(), days(3)), HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
            assertApiError(() -> service.activeForDoctor(CARE_ID), HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
        }
        assertThat(rx.getVersions()).hasSize(1);
        assertThat(h.audit).isEmpty();
    }

    // ---- the audit entry

    @Test
    void everyExtensionIsOnThePatientAccessLogWithTheOldAndNewEndDate() {
        service.extend(rx.getId(), days(3));

        assertThat(h.audit).filteredOn(a -> a.getAction() == AuditAction.PRESCRIPTION_EXTEND).singleElement().satisfies(a -> {
            assertThat(a.getOutcome()).isEqualTo(AuditOutcome.ALLOWED);
            assertThat(a.getResource()).isEqualTo("PRESCRIPTION");
            assertThat(a.getResourceId()).isEqualTo(rx.getId());
            assertThat(a.getActorUserId()).isEqualTo("d1");
            assertThat(a.getActorName()).isEqualTo("Dr Test");
            assertThat(a.getActorRole()).isEqualTo("DOCTOR");
            assertThat(a.getPatientId()).isEqualTo("p1");
            assertThat(a.getCareId()).isEqualTo(CARE_ID);
            assertThat(a.getDetail()).isEqualTo("end date 2026-10-29 to 2026-11-01");
        });
    }

    @Test
    void aRefusedExtensionWritesNoExtensionEntry() {
        assertApiError(() -> service.extend(rx.getId(), until("2026-10-28")), HttpStatus.BAD_REQUEST, "END_DATE_NOT_LATER");

        assertThat(h.audit).noneMatch(a -> a.getAction() == AuditAction.PRESCRIPTION_EXTEND);
    }

    // ---- the doctor's list

    @Test
    void theDoctorSeesTheCurrentPrescriptionsWithDatesAndDaysLeft() {
        Prescription open = h.existing("Metformin", PrescriptionHarness.T0.minus(Duration.ofDays(5)), null, PrescriptionStatus.ACTIVE);
        h.existing("Ibuprofen", PrescriptionHarness.T0.minus(Duration.ofDays(2)), 7, PrescriptionStatus.DISCONTINUED);
        h.existing("Cetirizine", PrescriptionHarness.T0.minus(Duration.ofDays(20)), 7, PrescriptionStatus.ACTIVE); // ended
        Prescription dispensed = h.existing("Warfarin", PrescriptionHarness.T0.minus(Duration.ofDays(1)), 7, PrescriptionStatus.DISPENSED);

        DoctorActivePrescriptionsResponse res = service.activeForDoctor(" cid-7k3mq9 ");

        assertThat(res.getCareId()).isEqualTo(CARE_ID);
        assertThat(res.getPrescriptions()).extracting(DoctorPrescriptionItem::getId)
                .containsExactly(rx.getId(), dispensed.getId(), open.getId()); // newest first
        DoctorPrescriptionItem item = res.getPrescriptions().get(0);
        assertThat(item.getDrug()).isEqualTo("Amoxicillin");
        assertThat(item.getStrength()).isEqualTo("5 mg");
        assertThat(item.getIssuedAt()).isEqualTo(PrescriptionHarness.T0.minus(Duration.ofHours(1)));
        assertThat(item.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(item.getDaysLeft()).isEqualTo(6);
        assertThat(item.isCanExtend()).isTrue();
        assertThat(res.getPrescriptions().get(1).isCanExtend()).as("dispensed").isFalse();
        DoctorPrescriptionItem noEnd = res.getPrescriptions().get(2);
        assertThat(noEnd.getEndDate()).isNull();
        assertThat(noEnd.getDaysLeft()).isNull();
        assertThat(noEnd.getIssuedAt()).isNotNull();
    }

    @Test
    void theListNeedsAnActiveGrantAndTheReadIsLogged() {
        service.activeForDoctor(CARE_ID);
        assertThat(h.audit).singleElement().satisfies(a -> {
            assertThat(a.getAction()).isEqualTo(AuditAction.READ);
            assertThat(a.getResource()).isEqualTo("PRESCRIPTION");
        });

        h.grants.clear();
        assertApiError(() -> service.activeForDoctor(CARE_ID), HttpStatus.FORBIDDEN, "NO_ACTIVE_GRANT");
    }

    // ---- issue date and end date on a new prescription

    private IssueRequest issue(String duration) {
        DrugItem d = new DrugItem();
        d.setDrug("Paracetamol");
        d.setDose("500 mg");
        d.setDuration(duration);
        IssueRequest r = new IssueRequest();
        r.setCareId(CARE_ID);
        r.setDrugs(List.of(d));
        return r;
    }

    @Test
    void anIssuedPrescriptionGetsAnIssueDateAndAnEndDateFromItsDuration() {
        PrescriptionView view = service.issue(issue("7 days")).getDrugs().get(0);

        assertThat(view.getIssuedAt()).isEqualTo(PrescriptionHarness.T0);
        assertThat(view.getEndDate()).isEqualTo(LocalDate.of(2026, 10, 29));
        assertThat(view.getDaysLeft()).isEqualTo(6);
    }

    @Test
    void anIssuedPrescriptionWithoutADurationStillHasAnIssueDate() {
        PrescriptionView view = service.issue(issue(null)).getDrugs().get(0);

        assertThat(view.getIssuedAt()).isEqualTo(PrescriptionHarness.T0);
        assertThat(h.prescriptions.get(view.getId()).getIssuedAt()).isEqualTo(PrescriptionHarness.T0);
        assertThat(view.getEndDate()).isNull();
        assertThat(view.getDaysLeft()).isNull();
    }

    @Test
    void weeksAreCountedInTheEndDate() {
        assertThat(service.issue(issue("2 weeks")).getDrugs().get(0).getEndDate()).isEqualTo(LocalDate.of(2026, 11, 5));
    }
}
