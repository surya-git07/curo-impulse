package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.DiscontinueRequest;
import com.curo.curo_backend.dto.DrugItem;
import com.curo.curo_backend.dto.IssueRequest;
import com.curo.curo_backend.dto.ModifyRequest;
import com.curo.curo_backend.model.DoseLog;
import com.curo.curo_backend.model.DoseStatus;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.support.PrescriptionHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static com.curo.curo_backend.support.Fixtures.signOut;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * "Now" in these tests is 2026-10-23 10:00 UTC = 15:30 in India. So 08:00 local has passed today and 20:00 (14:30 UTC) has not.
 */
class DoseScheduleTest {

    private static final Instant TONIGHT_2000_IST = Instant.parse("2026-10-23T14:30:00Z");

    private PrescriptionHarness h;

    @BeforeEach
    void setUp() {
        h = new PrescriptionHarness();
        h.grantTo(h.doctor, GrantScope.PRESCRIPTIONS);
    }

    @AfterEach
    void tearDown() {
        signOut();
    }

    private DrugItem drug(String frequency, String duration) {
        DrugItem d = new DrugItem();
        d.setDrug("Amoxicillin");
        d.setDose("500 mg");
        d.setFrequency(frequency);
        d.setDuration(duration);
        return d;
    }

    private String issue(DrugItem item) {
        IssueRequest r = new IssueRequest();
        r.setCareId("CID-7K3MQ9");
        r.setDrugs(List.of(item));
        return h.prescriptionService.issue(r).getDrugs().get(0).getId();
    }

    private List<DoseLog> dosesOf(String prescriptionId) {
        return h.doseLogs.stream().filter(d -> d.getPrescriptionId().equals(prescriptionId))
                .sorted(java.util.Comparator.comparing(DoseLog::getScheduledAt)).toList();
    }

    private ModifyRequest modify(String reason) {
        ModifyRequest m = new ModifyRequest();
        m.setReason(reason);
        return m;
    }

    // ---- issue

    @Test
    void issuingCreatesUpcomingDosesFromTheScheduleSkippingTimesAlreadyPast() {
        String id = issue(drug("TWICE_DAILY", "7 days"));

        List<DoseLog> doses = dosesOf(id);

        // today only 20:00 is left, then 6 more days of 08:00 and 20:00
        assertThat(doses).hasSize(13);
        assertThat(doses).allSatisfy(d -> {
            assertThat(d.getStatus()).isEqualTo(DoseStatus.UPCOMING);
            assertThat(d.getPatientId()).isEqualTo("p1");
            assertThat(d.getTakenAt()).isNull();
            assertThat(d.getCreatedAt()).isEqualTo(PrescriptionHarness.T0);
        });
        assertThat(doses.get(0).getScheduledAt()).as("20:00 IST in UTC").isEqualTo(TONIGHT_2000_IST);
        assertThat(doses.get(1).getScheduledAt()).as("08:00 IST next day").isEqualTo(Instant.parse("2026-10-24T02:30:00Z"));
        assertThat(doses.get(12).getScheduledAt()).as("last dose is 20:00 on day 7").isEqualTo(Instant.parse("2026-10-29T14:30:00Z"));
    }

    @Test
    void customTimesAreHonouredInLocalTime() {
        DrugItem d = drug("TWICE_DAILY", "2 days");
        d.setTimes(List.of("07:15", "21:30"));

        List<DoseLog> doses = dosesOf(issue(d));

        assertThat(doses).extracting(DoseLog::getScheduledAt).containsExactly(
                Instant.parse("2026-10-23T16:00:00Z"),   // 21:30 IST today
                Instant.parse("2026-10-24T01:45:00Z"),   // 07:15 IST tomorrow
                Instant.parse("2026-10-24T16:00:00Z"));  // 21:30 IST tomorrow, the last day
    }

    @Test
    void aPrescriptionWithNoEndDateIsScheduledForARollingThirtyDays() {
        assertThat(dosesOf(issue(drug("ONCE_DAILY", null)))).hasSize(29); // today's 08:00 has passed
    }

    @Test
    void asNeededMedicationGetsNoDoses() {
        assertThat(dosesOf(issue(drug(null, "7 days")))).isEmpty();
        assertThat(h.doseLogs).isEmpty();
    }

    @Test
    void ifTheDosesCannotBeSavedTheDoctorStillGetsTheIssuedPrescription() {
        when(h.doseLogRepository.saveAll(any(Iterable.class))).thenThrow(new DataAccessResourceFailureException("down"));

        IssueRequest r = new IssueRequest();
        r.setCareId("CID-7K3MQ9");
        r.setDrugs(List.of(drug("TWICE_DAILY", "7 days")));

        var response = h.prescriptionService.issue(r);

        assertThat(response.getDrugs()).hasSize(1);
        assertThat(h.prescriptions).hasSize(1);
    }

    @Test
    void generatingAgainDoesNotDuplicateDoses() {
        String id = issue(drug("TWICE_DAILY", "7 days"));

        int again = h.doseScheduleService.generate(h.prescriptions.get(id), "p1");

        assertThat(again).isZero();
        assertThat(dosesOf(id)).hasSize(13);
    }

    // ---- modify

    @Test
    void changingOnlyTheDoseTextLeavesTheDosesAlone() {
        String id = issue(drug("TWICE_DAILY", "7 days"));
        List<String> before = dosesOf(id).stream().map(DoseLog::getId).toList();
        ModifyRequest m = modify("lower dose");
        m.setDose("250 mg");

        h.prescriptionService.modify(id, m);

        assertThat(dosesOf(id).stream().map(DoseLog::getId).toList()).isEqualTo(before);
    }

    @Test
    void changingTheFrequencyRebuildsTheDosesStillAhead() {
        String id = issue(drug("TWICE_DAILY", "3 days"));
        ModifyRequest m = modify("three times a day");
        m.setFrequency("THREE_TIMES_DAILY");

        h.prescriptionService.modify(id, m);

        // today: 14:00 has passed, 20:00 left = 1; then two full days of 3
        List<DoseLog> doses = dosesOf(id);
        assertThat(doses).hasSize(7);
        assertThat(doses.get(0).getScheduledAt()).isEqualTo(TONIGHT_2000_IST);
        assertThat(doses).extracting(DoseLog::getStatus).containsOnly(DoseStatus.UPCOMING);
    }

    @Test
    void shorteningTheCourseDropsTheDosesBeyondTheNewLastDay() {
        String id = issue(drug("ONCE_DAILY", "10 days"));
        ModifyRequest m = modify("shorter");
        m.setDurationDays(3);

        h.prescriptionService.modify(id, m);

        assertThat(dosesOf(id)).extracting(DoseLog::getScheduledAt).containsExactly(
                Instant.parse("2026-10-24T02:30:00Z"), Instant.parse("2026-10-25T02:30:00Z"));
    }

    @Test
    void aRebuildKeepsTodaysHistoryAndAnEarlyTakenDoseWithoutDuplicatingIt() {
        String id = issue(drug("TWICE_DAILY", "3 days"));
        DoseLog early = dosesOf(id).get(1); // tomorrow 08:00, taken ahead of time
        early.setStatus(DoseStatus.TAKEN);
        early.setTakenAt(PrescriptionHarness.T0);
        DoseLog past = new DoseLog();
        past.setId("old1");
        past.setPrescriptionId(id);
        past.setPatientId("p1");
        past.setScheduledAt(Instant.parse("2026-10-23T02:30:00Z")); // this morning, already missed
        past.setStatus(DoseStatus.MISSED);
        h.doseLogs.add(past);
        ModifyRequest m = modify("times changed");
        m.setFrequency("TWICE_DAILY");
        m.setTimes(List.of("08:00", "21:00"));

        h.prescriptionService.modify(id, m);

        assertThat(h.doseLogs).as("history stays").contains(past, early);
        assertThat(dosesOf(id).stream().filter(d -> d.getScheduledAt().equals(early.getScheduledAt()))).hasSize(1);
        assertThat(early.getStatus()).isEqualTo(DoseStatus.TAKEN);
    }

    // ---- discontinue

    @Test
    void discontinuingCancelsFutureDosesAndKeepsThePast() {
        String id = issue(drug("TWICE_DAILY", "7 days"));
        DoseLog taken = new DoseLog();
        taken.setId("old1");
        taken.setPrescriptionId(id);
        taken.setPatientId("p1");
        taken.setScheduledAt(Instant.parse("2026-10-23T02:30:00Z"));
        taken.setStatus(DoseStatus.TAKEN);
        DoseLog missed = new DoseLog();
        missed.setId("old2");
        missed.setPrescriptionId(id);
        missed.setPatientId("p1");
        missed.setScheduledAt(Instant.parse("2026-10-22T14:30:00Z"));
        missed.setStatus(DoseStatus.MISSED);
        h.doseLogs.addAll(List.of(taken, missed));
        h.clock.advance(Duration.ofHours(1));

        DiscontinueRequest r = new DiscontinueRequest();
        r.setReason("stopped");
        h.prescriptionService.discontinue(id, r);

        assertThat(dosesOf(id)).containsExactly(missed, taken);
    }

    @Test
    void discontinuingOnePrescriptionLeavesOthersDosesAlone() {
        String a = issue(drug("TWICE_DAILY", "7 days"));
        String b = issue(drug("ONCE_DAILY", "5 days"));
        int bBefore = dosesOf(b).size();

        DiscontinueRequest r = new DiscontinueRequest();
        r.setReason("stopped");
        h.prescriptionService.discontinue(a, r);

        assertThat(dosesOf(a)).isEmpty();
        assertThat(dosesOf(b)).hasSize(bBefore);
    }

    @Test
    void aFailureCancellingDosesDoesNotUndoTheDiscontinue() {
        String id = issue(drug("TWICE_DAILY", "7 days"));
        when(h.doseLogRepository.deleteByPrescriptionIdAndStatusAndScheduledAtAfter(any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("down"));
        DiscontinueRequest r = new DiscontinueRequest();
        r.setReason("stopped");

        var view = h.prescriptionService.discontinue(id, r);

        assertThat(view.getStatus().name()).isEqualTo("DISCONTINUED");
    }
}
