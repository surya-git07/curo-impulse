package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.DiscontinueRequest;
import com.curo.curo_backend.dto.DrugItem;
import com.curo.curo_backend.dto.HistoryResponse;
import com.curo.curo_backend.dto.InteractionResult;
import com.curo.curo_backend.dto.IssueRequest;
import com.curo.curo_backend.dto.IssueResponse;
import com.curo.curo_backend.dto.ModifyRequest;
import com.curo.curo_backend.dto.PrescriptionView;
import com.curo.curo_backend.dto.WarningView;
import com.curo.curo_backend.model.ChangeType;
import com.curo.curo_backend.model.Consent;
import com.curo.curo_backend.model.Frequency;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.model.PrescriptionStatus;
import com.curo.curo_backend.model.Severity;
import com.curo.curo_backend.support.PrescriptionHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static com.curo.curo_backend.support.Fixtures.signIn;
import static com.curo.curo_backend.support.Fixtures.signOut;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrescriptionServiceTest {

    private PrescriptionHarness h;
    private PrescriptionService service;

    @BeforeEach
    void setUp() {
        h = new PrescriptionHarness();
        service = h.prescriptionService;
        h.grantTo(h.doctor, GrantScope.PRESCRIPTIONS, GrantScope.PROFILE);
    }

    @AfterEach
    void tearDown() {
        signOut();
    }

    private DrugItem drug(String name, String dose) {
        DrugItem d = new DrugItem();
        d.setDrug(name);
        d.setDose(dose);
        return d;
    }

    private IssueRequest issueRequest(DrugItem... drugs) {
        IssueRequest r = new IssueRequest();
        r.setCareId("CID-7K3MQ9");
        r.setDrugs(List.of(drugs));
        return r;
    }

    private void assertApiError(Supplier<?> call, HttpStatus status, String code) {
        assertThatThrownBy(call::get).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    // ---- issue

    @Test
    void issueSavesAnActivePrescriptionWithVersionOneRecordingWhoAndWhen() {
        DrugItem warfarin = drug("warfarin", "5 mg");
        warfarin.setFrequency("TWICE_DAILY");
        warfarin.setDuration("7 days");

        IssueResponse response = service.issue(issueRequest(warfarin));

        assertThat(response.getDrugs()).hasSize(1);
        PrescriptionView view = response.getDrugs().get(0);
        assertThat(view.getDrug()).as("canonical name").isEqualTo("Warfarin");
        assertThat(view.getStatus()).isEqualTo(PrescriptionStatus.ACTIVE);
        assertThat(view.getCareId()).isEqualTo("CID-7K3MQ9");
        assertThat(view.getFrequency()).isEqualTo(Frequency.TWICE_DAILY);
        assertThat(view.getTimes()).containsExactly("08:00", "20:00");
        assertThat(view.getDurationDays()).isEqualTo(7);
        assertThat(view.getCurrentVersion()).isEqualTo(1);

        Prescription stored = h.prescriptions.get(view.getId());
        assertThat(stored.getDoctorId()).isEqualTo("d1");
        assertThat(stored.getIssuedAt()).isEqualTo(PrescriptionHarness.T0);
        assertThat(stored.getVersions()).hasSize(1);
        var v1 = stored.getVersions().get(0);
        assertThat(v1.getVersion()).isEqualTo(1);
        assertThat(v1.getChangeType()).isEqualTo(ChangeType.ISSUED);
        assertThat(v1.getDose()).isEqualTo("5 mg");
        assertThat(v1.getChangedByUserId()).isEqualTo("d1");
        assertThat(v1.getChangedByName()).isEqualTo("Dr Test");
        assertThat(v1.getChangedByRole()).isEqualTo("DOCTOR");
        assertThat(v1.getChangedAt()).isEqualTo(PrescriptionHarness.T0);
        assertThat(v1.getReason()).isNull();
    }

    @Test
    void doctorComesFromTheSignedInUserNotFromTheRequest() {
        IssueResponse response = service.issue(issueRequest(drug("paracetamol", "500 mg")));

        assertThat(response.getDrugs().get(0).getDoctorId()).isEqualTo("d1");
    }

    @Test
    void aCleanPrescriptionHasNoWarnings() {
        assertThat(service.issue(issueRequest(drug("paracetamol", "500 mg"))).getWarnings()).isEmpty();
    }

    // ---- the interaction check: warns, never blocks

    @Test
    void warfarinThenAspirinWarnsAboutBleedingButIsStillIssued() {
        h.existing("Warfarin");

        IssueResponse response = service.issue(issueRequest(drug("Aspirin", "75 mg")));

        assertThat(response.getWarnings()).hasSize(1);
        WarningView w = response.getWarnings().get(0);
        assertThat(w.getDrug()).isEqualTo("Aspirin");
        assertThat(w.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(w.getWarning()).contains("Aspirin with Warfarin");
        assertThat(w.getExplanation()).containsIgnoringCase("bleeding");
        assertThat(response.getDrugs()).as("issued anyway").hasSize(1);
        assertThat(h.prescriptions.values()).extracting(Prescription::getDrug).containsExactlyInAnyOrder("Warfarin", "Aspirin");
    }

    @Test
    void aspirinThenWarfarinWarnsToo() {
        h.existing("Aspirin");

        IssueResponse response = service.issue(issueRequest(drug("Warfarin", "5 mg")));

        assertThat(response.getWarnings()).singleElement().satisfies(w -> assertThat(w.getSeverity()).isEqualTo(Severity.HIGH));
    }

    @Test
    void allergyConflictWarnsButIsStillIssued() {
        h.patient.setAllergies(List.of("Penicillin"));

        IssueResponse response = service.issue(issueRequest(drug("Amoxicillin", "500 mg")));

        assertThat(response.getWarnings()).singleElement().satisfies(w -> {
            assertThat(w.getSeverity()).isEqualTo(Severity.HIGH);
            assertThat(w.getWarning()).contains("Recorded allergy").contains("Penicillin");
        });
        assertThat(response.getDrugs()).hasSize(1);
        assertThat(h.prescriptions).hasSize(1);
    }

    @Test
    void anAllergyWarningIsGivenEvenWithoutTheProfileScope() {
        h.grants.clear();
        h.grantTo(h.doctor, GrantScope.PRESCRIPTIONS);
        h.patient.setAllergies(List.of("Penicillin"));

        assertThat(service.issue(issueRequest(drug("Amoxicillin", "500 mg"))).getWarnings()).hasSize(1);
    }

    @Test
    void interactionAndAllergyWarningsAppearTogether() {
        h.existing("Warfarin");
        h.patient.setAllergies(List.of("NSAIDs"));

        IssueResponse response = service.issue(issueRequest(drug("Ibuprofen", "400 mg")));

        WarningView w = response.getWarnings().get(0);
        assertThat(w.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(w.getExplanation()).containsIgnoringCase("allergy").contains("Warfarin");
        assertThat(response.getDrugs()).hasSize(1);
    }

    @Test
    void drugsInTheSameRequestAreCheckedAgainstEachOtherOncePerPair() {
        IssueResponse response = service.issue(issueRequest(drug("Warfarin", "5 mg"), drug("Aspirin", "75 mg")));

        assertThat(response.getDrugs()).hasSize(2);
        assertThat(response.getWarnings()).singleElement().satisfies(w -> assertThat(w.getDrug()).isEqualTo("Aspirin"));
    }

    @Test
    void discontinuedAndFinishedCoursesDoNotTriggerWarnings() {
        h.existing("Warfarin", PrescriptionHarness.T0.minus(Duration.ofDays(30)), null, PrescriptionStatus.DISCONTINUED);
        h.existing("Warfarin", PrescriptionHarness.T0.minus(Duration.ofDays(10)), 7, PrescriptionStatus.ACTIVE);

        assertThat(service.issue(issueRequest(drug("Aspirin", "75 mg"))).getWarnings()).isEmpty();
    }

    @Test
    void aCourseStillRunningOnItsLastDayStillCounts() {
        // issued 6 days ago for 7 days: today is the last day
        h.existing("Warfarin", PrescriptionHarness.T0.minus(Duration.ofDays(6)), 7, PrescriptionStatus.ACTIVE);

        assertThat(service.issue(issueRequest(drug("Aspirin", "75 mg"))).getWarnings()).hasSize(1);
    }

    @Test
    void otherPatientsDrugsAreNotConsidered() {
        var other = h.existing("Warfarin");
        other.setCareId("CID-OTHER1");

        assertThat(service.issue(issueRequest(drug("Aspirin", "75 mg"))).getWarnings()).isEmpty();
    }

    @Test
    void aBrokenCheckBecomesAnUnknownWarningAndTheDrugIsStillIssued() {
        InteractionChecker broken = mock(InteractionChecker.class);
        when(broken.check(any(), anyList(), any())).thenThrow(new IllegalStateException("model unavailable"));
        h.useChecker(broken);

        IssueResponse response = service.issue(issueRequest(drug("Warfarin", "5 mg")));

        assertThat(response.getDrugs()).hasSize(1);
        assertThat(response.getWarnings()).singleElement().satisfies(w -> {
            assertThat(w.getSeverity()).isEqualTo(Severity.UNKNOWN);
            assertThat(w.getWarning()).contains("could not run");
        });
    }

    @Test
    void theCheckerIsSwappableBehindTheInterface() {
        InteractionChecker custom = mock(InteractionChecker.class);
        when(custom.check(any(), anyList(), any())).thenReturn(new InteractionResult(Severity.LOW, "Custom warning.", "Custom."));
        h.useChecker(custom);

        IssueResponse response = service.issue(issueRequest(drug("Warfarin", "5 mg")));

        assertThat(response.getWarnings()).singleElement().satisfies(w -> assertThat(w.getWarning()).isEqualTo("Custom warning."));
    }

    @Test
    void aDrugOutsideTheListIsIssuedAsTypedWithAnUnknownWarning() {
        IssueResponse response = service.issue(issueRequest(drug("Zorbix", "1 tablet")));

        assertThat(response.getDrugs().get(0).getDrug()).isEqualTo("Zorbix");
        assertThat(response.getWarnings()).singleElement().satisfies(w -> assertThat(w.getSeverity()).isEqualTo(Severity.UNKNOWN));
    }

    @Test
    void anAliasIsStoredUnderTheCanonicalName() {
        assertThat(service.issue(issueRequest(drug("ASA", "75 mg"))).getDrugs().get(0).getDrug()).isEqualTo("Aspirin");
    }

    // ---- request validation

    @Test
    void drugsAreRequired() {
        IssueRequest none = issueRequest();
        assertApiError(() -> service.issue(none), HttpStatus.BAD_REQUEST, "DRUGS_REQUIRED");
        IssueRequest tooMany = new IssueRequest();
        tooMany.setCareId("CID-7K3MQ9");
        tooMany.setDrugs(java.util.Collections.nCopies(21, drug("paracetamol", "500 mg")));
        assertApiError(() -> service.issue(tooMany), HttpStatus.BAD_REQUEST, "DRUGS_REQUIRED");
    }

    @Test
    void eachDrugNeedsANameAndADose() {
        assertApiError(() -> service.issue(issueRequest(drug(" ", "5 mg"))), HttpStatus.BAD_REQUEST, "DRUG_REQUIRED");
        assertApiError(() -> service.issue(issueRequest(drug("Warfarin", null))), HttpStatus.BAD_REQUEST, "DOSE_REQUIRED");
        assertApiError(() -> service.issue(issueRequest(drug("Warfarin", " "))), HttpStatus.BAD_REQUEST, "DOSE_REQUIRED");
    }

    @Test
    void scheduleRulesAreEnforced() {
        DrugItem badFrequency = drug("Warfarin", "5 mg");
        badFrequency.setFrequency("SOMETIMES");
        assertApiError(() -> service.issue(issueRequest(badFrequency)), HttpStatus.BAD_REQUEST, "INVALID_FREQUENCY");

        DrugItem timesAlone = drug("Warfarin", "5 mg");
        timesAlone.setTimes(List.of("08:00"));
        assertApiError(() -> service.issue(issueRequest(timesAlone)), HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE");

        DrugItem wrongCount = drug("Warfarin", "5 mg");
        wrongCount.setFrequency("TWICE_DAILY");
        wrongCount.setTimes(List.of("08:00"));
        assertApiError(() -> service.issue(issueRequest(wrongCount)), HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE");

        DrugItem repeated = drug("Warfarin", "5 mg");
        repeated.setFrequency("TWICE_DAILY");
        repeated.setTimes(List.of("08:00", "08:00"));
        assertApiError(() -> service.issue(issueRequest(repeated)), HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE");

        DrugItem badTime = drug("Warfarin", "5 mg");
        badTime.setFrequency("ONCE_DAILY");
        badTime.setTimes(List.of("25:00"));
        assertApiError(() -> service.issue(issueRequest(badTime)), HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE");
    }

    @Test
    void customTimesOverrideTheDefaultsAndAreSorted() {
        DrugItem d = drug("Warfarin", "5 mg");
        d.setFrequency("twice_daily");
        d.setTimes(List.of("21:30", "07:15"));

        assertThat(service.issue(issueRequest(d)).getDrugs().get(0).getTimes()).containsExactly("07:15", "21:30");
    }

    @Test
    void durationIsReadFromTextOrTakenFromDays() {
        assertThat(durationDaysOf("7 days", null)).isEqualTo(7);
        assertThat(durationDaysOf("2 Weeks", null)).isEqualTo(14);
        assertThat(durationDaysOf("10d", null)).isEqualTo(10);
        assertThat(durationDaysOf("ongoing", null)).as("kept as written, no end date").isNull();
        assertThat(durationDaysOf("7 days", 3)).as("explicit days win").isEqualTo(3);
        assertThat(durationDaysOf(null, 30)).isEqualTo(30);
        assertThat(durationDaysOf(null, null)).isNull();
    }

    private Integer durationDaysOf(String text, Integer days) {
        DrugItem d = drug("Paracetamol", "500 mg");
        d.setDuration(text);
        d.setDurationDays(days);
        return service.issue(issueRequest(d)).getDrugs().get(0).getDurationDays();
    }

    @Test
    void impossibleDurationsAreRejected() {
        for (Integer days : new Integer[]{0, -1, 366}) {
            DrugItem d = drug("Paracetamol", "500 mg");
            d.setDurationDays(days);
            assertApiError(() -> service.issue(issueRequest(d)), HttpStatus.BAD_REQUEST, "INVALID_DURATION");
        }
        DrugItem tooLong = drug("Paracetamol", "500 mg");
        tooLong.setDuration("60 weeks");
        assertApiError(() -> service.issue(issueRequest(tooLong)), HttpStatus.BAD_REQUEST, "INVALID_DURATION");
    }

    // ---- who may issue

    @Test
    void onlyDoctorsIssue() {
        signIn(h.pharmacy);
        assertApiError(() -> service.issue(issueRequest(drug("Paracetamol", "500 mg"))), HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
        signIn(h.patient);
        assertApiError(() -> service.issue(issueRequest(drug("Paracetamol", "500 mg"))), HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
    }

    @Test
    void aBadRequestIsRefusedBeforeAnyPatientDataIsTouched() {
        h.grants.clear();

        assertApiError(() -> service.issue(issueRequest(drug(" ", "5 mg"))), HttpStatus.BAD_REQUEST, "DRUG_REQUIRED");
        assertThat(h.audit).as("no access was attempted").isEmpty();
    }

    @Test
    void withdrawnConsentBlocksNewPrescriptions() {
        Consent withdrawn = new Consent();
        withdrawn.setAccepted(false);
        h.patient.setConsent(withdrawn);

        assertApiError(() -> service.issue(issueRequest(drug("Paracetamol", "500 mg"))), HttpStatus.FORBIDDEN, "CONSENT_WITHDRAWN");
        assertThat(h.prescriptions).isEmpty();
    }

    // ---- modify

    private Prescription issuedWarfarin() {
        DrugItem d = drug("Warfarin", "5 mg");
        d.setFrequency("ONCE_DAILY");
        d.setDuration("30 days");
        String id = service.issue(issueRequest(d)).getDrugs().get(0).getId();
        h.clock.advance(Duration.ofHours(2));
        return h.prescriptions.get(id);
    }

    private ModifyRequest change(String dose, String reason) {
        ModifyRequest r = new ModifyRequest();
        r.setDose(dose);
        r.setReason(reason);
        return r;
    }

    @Test
    void modifyAppendsAVersionWithWhoWhenAndWhyAndKeepsTheOldOne() {
        Prescription p = issuedWarfarin();

        PrescriptionView view = service.modify(p.getId(), change("2.5 mg", "INR was high"));

        assertThat(view.getDose()).isEqualTo("2.5 mg");
        assertThat(view.getCurrentVersion()).isEqualTo(2);
        assertThat(view.getVersions()).hasSize(2);
        var v2 = p.getVersions().get(1);
        assertThat(v2.getVersion()).isEqualTo(2);
        assertThat(v2.getChangeType()).isEqualTo(ChangeType.MODIFIED);
        assertThat(v2.getDose()).isEqualTo("2.5 mg");
        assertThat(v2.getReason()).isEqualTo("INR was high");
        assertThat(v2.getChangedByUserId()).isEqualTo("d1");
        assertThat(v2.getChangedAt()).isEqualTo(PrescriptionHarness.T0.plus(Duration.ofHours(2)));
        var v1 = p.getVersions().get(0);
        assertThat(v1.getDose()).as("history is not rewritten").isEqualTo("5 mg");
        assertThat(v1.getChangeType()).isEqualTo(ChangeType.ISSUED);
        assertThat(p.getStatus()).isEqualTo(PrescriptionStatus.ACTIVE);
    }

    @Test
    void successiveChangesNumberTheVersionsInOrder() {
        Prescription p = issuedWarfarin();

        service.modify(p.getId(), change("4 mg", "step down"));
        service.modify(p.getId(), change("3 mg", "step down again"));

        assertThat(p.getVersions()).extracting(v -> v.getVersion()).containsExactly(1, 2, 3);
        assertThat(p.getVersions()).extracting(v -> v.getDose()).containsExactly("5 mg", "4 mg", "3 mg");
        assertThat(p.getCurrentVersion()).isEqualTo(3);
    }

    @Test
    void changingTheFrequencyResetsToItsDefaultTimes() {
        Prescription p = issuedWarfarin();
        ModifyRequest r = new ModifyRequest();
        r.setFrequency("THREE_TIMES_DAILY");
        r.setReason("more often");

        PrescriptionView view = service.modify(p.getId(), r);

        assertThat(view.getFrequency()).isEqualTo(Frequency.THREE_TIMES_DAILY);
        assertThat(view.getTimes()).containsExactly("08:00", "14:00", "20:00");
        assertThat(p.getDose()).as("untouched").isEqualTo("5 mg");
    }

    @Test
    void newTimesKeepTheCurrentFrequency() {
        Prescription p = issuedWarfarin();
        ModifyRequest r = new ModifyRequest();
        r.setTimes(List.of("21:00"));
        r.setReason("evening is easier");

        assertThat(service.modify(p.getId(), r).getTimes()).containsExactly("21:00");
        assertThat(p.getFrequency()).isEqualTo(Frequency.ONCE_DAILY);
    }

    @Test
    void changingTheDurationUpdatesTheDays() {
        Prescription p = issuedWarfarin();
        ModifyRequest r = new ModifyRequest();
        r.setDuration("2 weeks");
        r.setReason("shorter course");

        assertThat(service.modify(p.getId(), r).getDurationDays()).isEqualTo(14);
    }

    @Test
    void everyChangeNeedsAReason() {
        Prescription p = issuedWarfarin();

        assertApiError(() -> service.modify(p.getId(), change("2 mg", null)), HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        assertApiError(() -> service.modify(p.getId(), change("2 mg", "  ")), HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        assertThat(p.getCurrentVersion()).isEqualTo(1);
    }

    @Test
    void aChangeThatChangesNothingIsRejected() {
        Prescription p = issuedWarfarin();

        assertApiError(() -> service.modify(p.getId(), change("5 mg", "same")), HttpStatus.BAD_REQUEST, "NO_CHANGE");
        assertApiError(() -> service.modify(p.getId(), change(null, "nothing")), HttpStatus.BAD_REQUEST, "NO_CHANGE");
        assertThat(p.getVersions()).hasSize(1);
    }

    @Test
    void onlyTheIssuingDoctorCanModify() {
        Prescription p = issuedWarfarin();
        h.grantTo(h.otherDoctor, GrantScope.PRESCRIPTIONS);
        signIn(h.otherDoctor);

        assertApiError(() -> service.modify(p.getId(), change("1 mg", "no")), HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertThat(p.getDose()).isEqualTo("5 mg");
    }

    @Test
    void aDiscontinuedPrescriptionCannotBeModified() {
        Prescription p = issuedWarfarin();
        service.discontinue(p.getId(), reason("stopped"));

        assertApiError(() -> service.modify(p.getId(), change("1 mg", "late")), HttpStatus.CONFLICT, "INVALID_STATE");
    }

    @Test
    void modifyOfAnUnknownPrescriptionIs404() {
        assertApiError(() -> service.modify("nope", change("1 mg", "x")), HttpStatus.NOT_FOUND, "PRESCRIPTION_NOT_FOUND");
    }

    @Test
    void onlyDoctorsModify() {
        Prescription p = issuedWarfarin();
        signIn(h.pharmacy);
        assertApiError(() -> service.modify(p.getId(), change("1 mg", "x")), HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
    }

    // ---- discontinue

    private DiscontinueRequest reason(String text) {
        DiscontinueRequest r = new DiscontinueRequest();
        r.setReason(text);
        return r;
    }

    @Test
    void discontinueEndsThePrescriptionAndRecordsAFinalVersion() {
        Prescription p = issuedWarfarin();

        PrescriptionView view = service.discontinue(p.getId(), reason("Course complete early"));

        assertThat(view.getStatus()).isEqualTo(PrescriptionStatus.DISCONTINUED);
        assertThat(view.getDiscontinuedAt()).isEqualTo(PrescriptionHarness.T0.plus(Duration.ofHours(2)));
        assertThat(view.getCurrentVersion()).isEqualTo(2);
        var last = p.getVersions().get(1);
        assertThat(last.getChangeType()).isEqualTo(ChangeType.DISCONTINUED);
        assertThat(last.getReason()).isEqualTo("Course complete early");
        assertThat(last.getChangedByUserId()).isEqualTo("d1");
        assertThat(last.getDose()).as("final version still shows the dose that was stopped").isEqualTo("5 mg");
    }

    @Test
    void discontinueNeedsAReasonAndWorksOnlyOnce() {
        Prescription p = issuedWarfarin();

        assertApiError(() -> service.discontinue(p.getId(), null), HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        assertApiError(() -> service.discontinue(p.getId(), reason(" ")), HttpStatus.BAD_REQUEST, "REASON_REQUIRED");
        service.discontinue(p.getId(), reason("done"));
        assertApiError(() -> service.discontinue(p.getId(), reason("again")), HttpStatus.CONFLICT, "INVALID_STATE");
        assertThat(p.getVersions()).hasSize(2);
    }

    @Test
    void onlyTheIssuingDoctorCanDiscontinue() {
        Prescription p = issuedWarfarin();
        h.grantTo(h.otherDoctor, GrantScope.PRESCRIPTIONS);
        signIn(h.otherDoctor);

        assertApiError(() -> service.discontinue(p.getId(), reason("no")), HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertThat(p.getStatus()).isEqualTo(PrescriptionStatus.ACTIVE);
    }

    @Test
    void aDiscontinuedDrugStopsCountingForNewChecks() {
        Prescription p = issuedWarfarin();
        service.discontinue(p.getId(), reason("stopped"));

        assertThat(service.issue(issueRequest(drug("Aspirin", "75 mg"))).getWarnings()).isEmpty();
    }

    // ---- history

    @Test
    void historyListsEveryVersionInOrder() {
        Prescription p = issuedWarfarin();
        service.modify(p.getId(), change("4 mg", "step down"));
        service.discontinue(p.getId(), reason("stopped"));

        HistoryResponse history = service.history(p.getId());

        assertThat(history.getPrescriptionId()).isEqualTo(p.getId());
        assertThat(history.getCareId()).isEqualTo("CID-7K3MQ9");
        assertThat(history.getDrug()).isEqualTo("Warfarin");
        assertThat(history.getCurrentVersion()).isEqualTo(3);
        assertThat(history.getVersions()).extracting(v -> v.getChangeType())
                .containsExactly(ChangeType.ISSUED, ChangeType.MODIFIED, ChangeType.DISCONTINUED);
        assertThat(history.getVersions().get(1).getChangedBy().getName()).isEqualTo("Dr Test");
        assertThat(history.getVersions().get(1).getReason()).isEqualTo("step down");
    }

    @Test
    void anyDoctorOrPharmacyWithAGrantMayReadHistory() {
        Prescription p = issuedWarfarin();
        h.grantTo(h.pharmacy, GrantScope.PRESCRIPTIONS);
        signIn(h.pharmacy);

        assertThat(service.history(p.getId()).getVersions()).hasSize(1);
    }

    @Test
    void patientsAndCaregiversDoNotUseTheHistoryEndpoint() {
        Prescription p = issuedWarfarin();
        signIn(h.patient);
        assertApiError(() -> service.history(p.getId()), HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
        signIn(h.caregiver);
        assertApiError(() -> service.history(p.getId()), HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
    }

    @Test
    void historyOfAnUnknownPrescriptionIs404() {
        assertApiError(() -> service.history("nope"), HttpStatus.NOT_FOUND, "PRESCRIPTION_NOT_FOUND");
    }
}
