package com.curo.curo_backend.service;

import com.curo.curo_backend.model.AccessGrant;
import com.curo.curo_backend.model.Alert;
import com.curo.curo_backend.model.AlertStatus;
import com.curo.curo_backend.model.AlertType;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.DoseLog;
import com.curo.curo_backend.model.DoseStatus;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.GrantStatus;
import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.repository.AccessGrantRepository;
import com.curo.curo_backend.repository.AlertRepository;
import com.curo.curo_backend.repository.UserRepository;
import com.curo.curo_backend.support.FakeDoseUpdates;
import com.curo.curo_backend.support.PrescriptionHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * "Now" is 2026-10-23 10:00 UTC (15:30 in India). A dose becomes MISSED when it is 30 minutes overdue, and gets its reminder when
 * it is due within 10 minutes. Consent decides who is told: caregivers through an active caregiver grant, the doctor only while
 * they hold an active grant covering DOSES.
 */
class DoseAlertServiceTest {

    private static final Instant T0 = PrescriptionHarness.T0;
    private static final Instant OVERDUE = T0.minus(Duration.ofMinutes(30));

    private PrescriptionHarness h;
    private DoseAlertService service;
    private Prescription rx;
    private final List<Alert> alerts = new ArrayList<>();
    private final List<NotificationSender.Notification> notifications = new ArrayList<>();
    private boolean deliveryWorks = true;
    private int doseCounter = 0;

    @BeforeEach
    void setUp() {
        h = new PrescriptionHarness();

        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(h.users.get(inv.<String>getArgument(0))));
        AccessGrantRepository grantRepo = mock(AccessGrantRepository.class);
        when(grantRepo.findByPatientIdAndRoleAndStatus(anyString(), anyString(), any(GrantStatus.class))).thenAnswer(inv ->
                h.grants.stream().filter(g -> g.getPatientId().equals(inv.getArgument(0)) && g.getRole().equals(inv.getArgument(1))
                        && g.getStatus() == inv.getArgument(2)).toList());
        when(grantRepo.findByPatientIdAndGranteeIdAndStatus(anyString(), anyString(), any(GrantStatus.class))).thenAnswer(inv ->
                h.grants.stream().filter(g -> g.getPatientId().equals(inv.getArgument(0)) && g.getGranteeId().equals(inv.getArgument(1))
                        && g.getStatus() == inv.getArgument(2)).toList());

        AlertRepository alertRepo = mock(AlertRepository.class);
        when(alertRepo.save(any(Alert.class))).thenAnswer(inv -> {
            Alert a = inv.getArgument(0);
            a.setId("a" + (alerts.size() + 1));
            alerts.add(a);
            return a;
        });
        NotificationSender sender = new NotificationSender() {
            @Override
            public String channel() {
                return "LOG";
            }

            @Override
            public boolean send(Notification n) {
                notifications.add(n);
                return deliveryWorks;
            }
        };
        AlertService alertService = new AlertService();
        ReflectionTestUtils.setField(alertService, "alertRepository", alertRepo);
        ReflectionTestUtils.setField(alertService, "notificationSender", sender);
        ReflectionTestUtils.setField(alertService, "clock", h.clock);

        service = new DoseAlertService();
        ReflectionTestUtils.setField(service, "reminderLeadMinutes", 10);
        ReflectionTestUtils.setField(service, "escalationMisses", 3);
        ReflectionTestUtils.setField(service, "timeZone", "Asia/Kolkata");
        ReflectionTestUtils.setField(service, "doseLogRepository", h.doseLogRepository);
        ReflectionTestUtils.setField(service, "doseUpdates", new FakeDoseUpdates(h.doseLogs));
        ReflectionTestUtils.setField(service, "doseScheduleService", h.doseScheduleService);
        ReflectionTestUtils.setField(service, "prescriptionRepository", h.prescriptionRepository);
        ReflectionTestUtils.setField(service, "userRepository", userRepo);
        ReflectionTestUtils.setField(service, "grantRepository", grantRepo);
        ReflectionTestUtils.setField(service, "alertService", alertService);
        ReflectionTestUtils.setField(service, "clock", h.clock);

        rx = h.existing("Warfarin", T0.minus(Duration.ofDays(2)), null, com.curo.curo_backend.model.PrescriptionStatus.ACTIVE);
        rx.setDose("5 mg");
    }

    private DoseLog dose(Instant scheduledAt, DoseStatus status) {
        return dose(rx, scheduledAt, status);
    }

    private DoseLog dose(Prescription p, Instant scheduledAt, DoseStatus status) {
        DoseLog d = new DoseLog();
        d.setId("dose" + (++doseCounter));
        d.setPrescriptionId(p.getId());
        d.setPatientId("p1");
        d.setScheduledAt(scheduledAt);
        d.setStatus(status);
        h.doseLogs.add(d);
        return d;
    }

    private void caregiverLinked() {
        h.grantTo(h.caregiver, GrantScope.MEDICATIONS, GrantScope.DOSES);
    }

    private void doctorMayKnow() {
        h.grantTo(h.doctor, GrantScope.PRESCRIPTIONS, GrantScope.DOSES);
    }

    private List<Alert> to(AppUser user, AlertType type) {
        return alerts.stream().filter(a -> a.getRecipientId().equals(user.getId()) && a.getType() == type).toList();
    }

    // ---- upcoming-dose reminders

    @Test
    void aDoseDueWithinTenMinutesGetsAReminderToThePatient() {
        DoseLog d = dose(T0.plus(Duration.ofMinutes(8)), DoseStatus.UPCOMING);

        long sent = service.sendReminders();

        assertThat(sent).isEqualTo(1);
        assertThat(alerts).singleElement().satisfies(a -> {
            assertThat(a.getType()).isEqualTo(AlertType.UPCOMING_DOSE);
            assertThat(a.getRecipientId()).isEqualTo("p1");
            assertThat(a.getDoseId()).isEqualTo(d.getId());
            assertThat(a.getPrescriptionId()).isEqualTo(rx.getId());
            assertThat(a.getPatientCareId()).isEqualTo("CID-7K3MQ9");
            assertThat(a.getStatus()).isEqualTo(AlertStatus.SENT);
            assertThat(a.getChannel()).isEqualTo("LOG");
            assertThat(a.getMessage()).contains("Warfarin 5 mg").contains("15:38"); // 10:08 UTC is 15:38 in India
        });
    }

    @Test
    void theReminderWindowIsAfterNowUpToAndIncludingTenMinutes() {
        dose(T0, DoseStatus.UPCOMING);                                   // due right now: too late for a reminder
        dose(T0.minusSeconds(1), DoseStatus.UPCOMING);                   // already past
        DoseLog atEdge = dose(T0.plus(Duration.ofMinutes(10)), DoseStatus.UPCOMING);
        dose(T0.plus(Duration.ofMinutes(10)).plusSeconds(1), DoseStatus.UPCOMING); // not yet

        assertThat(service.sendReminders()).isEqualTo(1);
        assertThat(alerts).singleElement().satisfies(a -> assertThat(a.getDoseId()).isEqualTo(atEdge.getId()));
    }

    @Test
    void aDoseIsRemindedOnlyOnceEvenIfTheTickRunsAgain() {
        DoseLog d = dose(T0.plus(Duration.ofMinutes(8)), DoseStatus.UPCOMING);

        service.sendReminders();
        h.clock.advance(Duration.ofMinutes(5)); // the next tick
        long again = service.sendReminders();

        assertThat(again).isZero();
        assertThat(alerts).hasSize(1);
        assertThat(d.getReminderSentAt()).isEqualTo(T0);
    }

    @Test
    void ifAnotherRunClaimedTheReminderFirstNothingIsSent() {
        dose(T0.plus(Duration.ofMinutes(8)), DoseStatus.UPCOMING);
        DoseUpdates lost = mock(DoseUpdates.class);
        when(lost.markReminderSent(anyString(), any(Instant.class))).thenReturn(false);
        ReflectionTestUtils.setField(service, "doseUpdates", lost);

        assertThat(service.sendReminders()).isZero();
        assertThat(alerts).isEmpty();
    }

    @Test
    void takenAndMissedDosesGetNoReminder() {
        dose(T0.plus(Duration.ofMinutes(5)), DoseStatus.TAKEN);
        dose(T0.plus(Duration.ofMinutes(6)), DoseStatus.MISSED);

        assertThat(service.sendReminders()).isZero();
    }

    @Test
    void onlyThePatientIsRemindedNotCaregiversOrDoctors() {
        caregiverLinked();
        doctorMayKnow();
        dose(T0.plus(Duration.ofMinutes(8)), DoseStatus.UPCOMING);

        service.sendReminders();

        assertThat(alerts).extracting(Alert::getRecipientId).containsExactly("p1");
    }

    @Test
    void theReminderTextLeavingTheAppNamesNoDrug() {
        dose(T0.plus(Duration.ofMinutes(8)), DoseStatus.UPCOMING);

        service.sendReminders();

        assertThat(notifications).singleElement().satisfies(n -> {
            assertThat(n.type()).isEqualTo("UPCOMING_DOSE");
            assertThat(n.recipientCareId()).isEqualTo("CID-7K3MQ9");
            assertThat(n.subject() + n.body()).doesNotContain("Warfarin").doesNotContain("5 mg").doesNotContain("Test Patient");
        });
    }

    @Test
    void everyDueDoseIsRemindedAndOneFailingDoesNotStopTheRest() {
        dose(T0.plus(Duration.ofMinutes(3)), DoseStatus.UPCOMING);
        DoseLog orphan = dose(T0.plus(Duration.ofMinutes(4)), DoseStatus.UPCOMING);
        orphan.setPatientId("nobody");
        dose(T0.plus(Duration.ofMinutes(5)), DoseStatus.UPCOMING);

        assertThat(service.sendReminders()).isEqualTo(2);
    }

    // ---- missed doses

    @Test
    void aMissedDoseAlertsThePatientTheCaregiverAndThePrescribingDoctor() {
        caregiverLinked();
        doctorMayKnow();
        DoseLog d = dose(OVERDUE, DoseStatus.UPCOMING);

        DoseAlertService.MissedResult result = service.processMissed();

        assertThat(result.markedMissed()).isEqualTo(1);
        assertThat(result.alertsCreated()).isEqualTo(3);
        assertThat(d.getStatus()).isEqualTo(DoseStatus.MISSED);
        assertThat(to(h.patient, AlertType.MISSED_DOSE)).hasSize(1);
        assertThat(to(h.caregiver, AlertType.MISSED_DOSE)).hasSize(1);
        assertThat(to(h.doctor, AlertType.MISSED_DOSE)).hasSize(1);
        assertThat(alerts).allSatisfy(a -> {
            assertThat(a.getDoseId()).isEqualTo(d.getId());
            assertThat(a.getPatientCareId()).isEqualTo("CID-7K3MQ9");
            assertThat(a.getStatus()).isEqualTo(AlertStatus.SENT);
        });
    }

    @Test
    void theInboxTextSaysWhoWhatAndWhenInIndianTime() {
        caregiverLinked();
        dose(Instant.parse("2026-10-23T02:30:00Z"), DoseStatus.UPCOMING); // 08:00 IST

        service.processMissed();

        assertThat(to(h.patient, AlertType.MISSED_DOSE).get(0).getMessage()).isEqualTo("You missed your Warfarin 5 mg dose scheduled at 08:00.");
        assertThat(to(h.caregiver, AlertType.MISSED_DOSE).get(0).getMessage())
                .isEqualTo("Test Patient (CID-7K3MQ9) missed Warfarin 5 mg at 08:00.");
    }

    @Test
    void whatLeavesTheAppNeverNamesTheDrugOrThePatient() {
        caregiverLinked();
        doctorMayKnow();
        dose(OVERDUE, DoseStatus.UPCOMING);

        service.processMissed();

        assertThat(notifications).hasSize(3);
        assertThat(notifications).allSatisfy(n -> {
            assertThat(n.type()).isEqualTo("MISSED_DOSE");
            assertThat(n.subject() + " " + n.body()).doesNotContain("Warfarin").doesNotContain("5 mg").doesNotContain("Test Patient");
        });
        assertThat(notifications).extracting(NotificationSender.Notification::recipientCareId)
                .containsExactlyInAnyOrder("CID-7K3MQ9", "CID-3CC3CC", "CID-4HN8XD");
    }

    @Test
    void withNoCaregiverOrDoctorGrantOnlyThePatientIsTold() {
        dose(OVERDUE, DoseStatus.UPCOMING);

        DoseAlertService.MissedResult result = service.processMissed();

        assertThat(result.alertsCreated()).isEqualTo(1);
        assertThat(alerts).extracting(Alert::getRecipientId).containsExactly("p1");
    }

    @Test
    void aRevokedExpiredOrNarrowCaregiverGrantIsNotTold() {
        h.grantTo(h.caregiver, GrantStatus.REVOKED, null, GrantScope.MEDICATIONS, GrantScope.DOSES);
        dose(OVERDUE, DoseStatus.UPCOMING);
        service.processMissed();
        assertThat(to(h.caregiver, AlertType.MISSED_DOSE)).as("revoked").isEmpty();

        h.grants.clear();
        h.grantTo(h.caregiver, GrantStatus.ACTIVE, T0.minusSeconds(1), GrantScope.MEDICATIONS, GrantScope.DOSES);
        dose(OVERDUE.minusSeconds(60), DoseStatus.UPCOMING);
        service.processMissed();
        assertThat(to(h.caregiver, AlertType.MISSED_DOSE)).as("expired").isEmpty();

        h.grants.clear();
        h.grantTo(h.caregiver, GrantScope.MEDICATIONS); // no DOSES scope
        dose(OVERDUE.minusSeconds(120), DoseStatus.UPCOMING);
        service.processMissed();
        assertThat(to(h.caregiver, AlertType.MISSED_DOSE)).as("no DOSES scope").isEmpty();
    }

    @Test
    void aDoctorWithoutConsentIsNotToldEvenThoughTheyPrescribed() {
        dose(OVERDUE, DoseStatus.UPCOMING);                      // no grant at all
        service.processMissed();
        assertThat(to(h.doctor, AlertType.MISSED_DOSE)).as("no grant").isEmpty();

        h.grantTo(h.doctor, GrantScope.PRESCRIPTIONS, GrantScope.MEDICATIONS);   // a grant, but not for DOSES
        dose(OVERDUE.minusSeconds(60), DoseStatus.UPCOMING);
        service.processMissed();
        assertThat(to(h.doctor, AlertType.MISSED_DOSE)).as("grant without DOSES").isEmpty();

        h.grants.clear();
        h.grantTo(h.doctor, GrantStatus.REVOKED, null, GrantScope.DOSES);
        dose(OVERDUE.minusSeconds(120), DoseStatus.UPCOMING);
        service.processMissed();
        assertThat(to(h.doctor, AlertType.MISSED_DOSE)).as("revoked grant").isEmpty();
    }

    @Test
    void onlyThePrescribingDoctorIsToldNotAnotherDoctorWithAccess() {
        h.grantTo(h.otherDoctor, GrantScope.PRESCRIPTIONS, GrantScope.DOSES);
        dose(OVERDUE, DoseStatus.UPCOMING);

        service.processMissed();

        assertThat(to(h.otherDoctor, AlertType.MISSED_DOSE)).isEmpty();
        assertThat(to(h.doctor, AlertType.MISSED_DOSE)).isEmpty();
    }

    @Test
    void aDoseWithinTheGraceOrAlreadyTakenRaisesNothing() {
        dose(T0.minus(Duration.ofMinutes(29)), DoseStatus.UPCOMING);
        dose(OVERDUE, DoseStatus.TAKEN);
        caregiverLinked();

        DoseAlertService.MissedResult result = service.processMissed();

        assertThat(result.markedMissed()).isZero();
        assertThat(alerts).isEmpty();
    }

    @Test
    void aDoseThePatientTookJustBeforeTheJobIsNotAlerted() {
        DoseLog d = dose(OVERDUE, DoseStatus.UPCOMING);
        DoseUpdates raced = mock(DoseUpdates.class);
        when(raced.markMissed(anyString())).thenAnswer(inv -> {
            d.setStatus(DoseStatus.TAKEN); // the patient got there first
            return false;
        });
        ReflectionTestUtils.setField(service, "doseUpdates", raced);
        ReflectionTestUtils.setField(h.doseScheduleService, "doseUpdates", raced);

        DoseAlertService.MissedResult result = service.processMissed();

        assertThat(result.markedMissed()).isZero();
        assertThat(alerts).isEmpty();
    }

    @Test
    void runningTheTickAgainNeverAlertsTwiceForTheSameMiss() {
        caregiverLinked();
        dose(OVERDUE, DoseStatus.UPCOMING);

        service.processMissed();
        int first = alerts.size();
        DoseAlertService.MissedResult second = service.processMissed();
        service.processMissed();

        assertThat(second.markedMissed()).isZero();
        assertThat(second.alertsCreated()).isZero();
        assertThat(alerts).hasSize(first);
    }

    @Test
    void aDeletedPatientIsNotAlerted() {
        h.patient.setDeletedAt(T0.minusSeconds(60));
        dose(OVERDUE, DoseStatus.UPCOMING);

        DoseAlertService.MissedResult result = service.processMissed();

        assertThat(result.markedMissed()).isEqualTo(1);
        assertThat(alerts).isEmpty();
    }

    @Test
    void oneDoseThatFailsToNotifyDoesNotStopTheOthers() {
        DoseLog bad = dose(OVERDUE.minusSeconds(60), DoseStatus.UPCOMING);
        bad.setPatientId("p1");
        dose(OVERDUE, DoseStatus.UPCOMING);
        // the first alert save blows up, the rest work
        AlertRepository flaky = mock(AlertRepository.class);
        int[] calls = {0};
        when(flaky.save(any(Alert.class))).thenAnswer(inv -> {
            if (calls[0]++ == 0) {
                throw new DataAccessResourceFailureException("down");
            }
            Alert a = inv.getArgument(0);
            alerts.add(a);
            return a;
        });
        AlertService alertService = (AlertService) ReflectionTestUtils.getField(service, "alertService");
        ReflectionTestUtils.setField(alertService, "alertRepository", flaky);

        DoseAlertService.MissedResult result = service.processMissed();

        assertThat(result.markedMissed()).isEqualTo(2);
        assertThat(alerts).as("the second dose still produced its alert").hasSize(1);
    }

    @Test
    void aFailedDeliveryStillStoresTheAlertAsFailed() {
        deliveryWorks = false;
        caregiverLinked();
        dose(OVERDUE, DoseStatus.UPCOMING);

        DoseAlertService.MissedResult result = service.processMissed();

        assertThat(result.alertsCreated()).isEqualTo(2);
        assertThat(alerts).allSatisfy(a -> assertThat(a.getStatus()).isEqualTo(AlertStatus.FAILED));
    }

    // ---- escalation after consecutive misses

    private void missedInARow(int count, Instant lastDue) {
        for (int i = count - 1; i >= 0; i--) {
            dose(lastDue.minus(Duration.ofHours(8L * i)), DoseStatus.UPCOMING);
        }
    }

    @Test
    void theThirdConsecutiveMissRaisesAnAdherenceAlertToEveryone() {
        caregiverLinked();
        doctorMayKnow();
        dose(OVERDUE.minus(Duration.ofHours(16)), DoseStatus.UPCOMING);
        service.processMissed();
        dose(OVERDUE.minus(Duration.ofHours(8)), DoseStatus.UPCOMING);
        service.processMissed();
        assertThat(alerts).as("two misses are not an escalation").noneMatch(a -> a.getType() == AlertType.ADHERENCE);

        dose(OVERDUE, DoseStatus.UPCOMING);
        service.processMissed();

        assertThat(to(h.patient, AlertType.ADHERENCE)).hasSize(1);
        assertThat(to(h.caregiver, AlertType.ADHERENCE)).hasSize(1);
        assertThat(to(h.doctor, AlertType.ADHERENCE)).hasSize(1);
        assertThat(to(h.patient, AlertType.ADHERENCE).get(0).getMessage()).isEqualTo("You have missed 3 Warfarin doses in a row.");
        assertThat(to(h.caregiver, AlertType.ADHERENCE).get(0).getMessage())
                .isEqualTo("Test Patient (CID-7K3MQ9) has missed 3 Warfarin doses in a row.");
    }

    @Test
    void theEscalationEmailNamesNeitherDrugNorPatient() {
        caregiverLinked();
        missedInARow(3, OVERDUE);

        service.processMissed();

        assertThat(notifications).filteredOn(n -> n.type().equals("ADHERENCE")).hasSize(2).allSatisfy(n ->
                assertThat(n.subject() + n.body()).doesNotContain("Warfarin").doesNotContain("Test Patient"));
    }

    @Test
    void missesNumberFourAndFiveDoNotRepeatTheEscalationButSixDoes() {
        missedInARow(3, OVERDUE.minus(Duration.ofHours(24)));
        service.processMissed();
        long afterThree = alerts.stream().filter(a -> a.getType() == AlertType.ADHERENCE).count();
        assertThat(afterThree).isEqualTo(1);

        dose(OVERDUE.minus(Duration.ofHours(16)), DoseStatus.UPCOMING);
        service.processMissed();
        dose(OVERDUE.minus(Duration.ofHours(8)), DoseStatus.UPCOMING);
        service.processMissed();
        assertThat(alerts.stream().filter(a -> a.getType() == AlertType.ADHERENCE).count()).as("4th and 5th").isEqualTo(1);

        dose(OVERDUE, DoseStatus.UPCOMING);
        service.processMissed();
        assertThat(alerts.stream().filter(a -> a.getType() == AlertType.ADHERENCE).count()).as("6th").isEqualTo(2);
        assertThat(alerts.get(alerts.size() - 1).getMessage()).contains("6 Warfarin doses");
    }

    @Test
    void aTakenDoseBreaksTheStreak() {
        dose(OVERDUE.minus(Duration.ofHours(32)), DoseStatus.MISSED);
        dose(OVERDUE.minus(Duration.ofHours(24)), DoseStatus.MISSED);
        dose(OVERDUE.minus(Duration.ofHours(16)), DoseStatus.TAKEN);
        dose(OVERDUE.minus(Duration.ofHours(8)), DoseStatus.UPCOMING);
        dose(OVERDUE, DoseStatus.UPCOMING);

        service.processMissed();

        assertThat(alerts).as("only two in a row since the taken dose").noneMatch(a -> a.getType() == AlertType.ADHERENCE);
    }

    @Test
    void anotherPrescriptionsMissesDoNotCountTowardThisOne() {
        Prescription other = h.existing("Metformin", T0.minus(Duration.ofDays(2)), null, com.curo.curo_backend.model.PrescriptionStatus.ACTIVE);
        dose(other, OVERDUE.minus(Duration.ofHours(16)), DoseStatus.MISSED);
        dose(other, OVERDUE.minus(Duration.ofHours(8)), DoseStatus.MISSED);
        dose(rx, OVERDUE, DoseStatus.UPCOMING);

        service.processMissed();

        assertThat(alerts).noneMatch(a -> a.getType() == AlertType.ADHERENCE);
    }

    @Test
    void severalMissesFoundInOneTickEscalateExactlyOnce() {
        // the job was down: three doses are overdue by the time it runs
        missedInARow(3, OVERDUE);

        DoseAlertService.MissedResult result = service.processMissed();

        assertThat(result.markedMissed()).isEqualTo(3);
        assertThat(alerts.stream().filter(a -> a.getType() == AlertType.ADHERENCE)).hasSize(1);
        assertThat(alerts.stream().filter(a -> a.getType() == AlertType.MISSED_DOSE)).hasSize(3);
        assertThat(result.alertsCreated()).isEqualTo(4);
    }

    @Test
    void theThresholdIsConfigurable() {
        ReflectionTestUtils.setField(service, "escalationMisses", 2);
        dose(OVERDUE.minus(Duration.ofHours(8)), DoseStatus.UPCOMING);
        dose(OVERDUE, DoseStatus.UPCOMING);

        service.processMissed();

        assertThat(alerts.stream().filter(a -> a.getType() == AlertType.ADHERENCE)).hasSize(1);
        assertThat(to(h.patient, AlertType.ADHERENCE).get(0).getMessage()).contains("2 Warfarin doses");
    }

    @Test
    void aThresholdOfOneEscalatesOnEveryMiss() {
        ReflectionTestUtils.setField(service, "escalationMisses", 1);
        dose(OVERDUE, DoseStatus.UPCOMING);

        service.processMissed();

        assertThat(alerts.stream().filter(a -> a.getType() == AlertType.ADHERENCE)).hasSize(1);
    }

    // ---- the fallback sender end to end

    @Test
    void withTheLoggingFallbackTheWholeFlowStillWorks() {
        AlertService alertService = (AlertService) ReflectionTestUtils.getField(service, "alertService");
        ReflectionTestUtils.setField(alertService, "notificationSender", new LoggingNotificationSender());
        caregiverLinked();
        dose(OVERDUE, DoseStatus.UPCOMING);

        DoseAlertService.MissedResult result = service.processMissed();

        assertThat(result.alertsCreated()).isEqualTo(2);
        assertThat(alerts).allSatisfy(a -> {
            assertThat(a.getChannel()).isEqualTo("LOG");
            assertThat(a.getStatus()).isEqualTo(AlertStatus.SENT);
        });
    }
}
