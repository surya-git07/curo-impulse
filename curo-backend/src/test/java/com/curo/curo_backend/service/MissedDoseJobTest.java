package com.curo.curo_backend.service;

import com.curo.curo_backend.model.DoseLog;
import com.curo.curo_backend.model.DoseStatus;
import com.curo.curo_backend.support.PrescriptionHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static com.curo.curo_backend.support.Fixtures.signIn;
import static com.curo.curo_backend.support.Fixtures.signOut;
import static org.assertj.core.api.Assertions.assertThat;

/** The server's own rule: an UPCOMING dose still untouched 30 minutes after it was due becomes MISSED. */
class MissedDoseJobTest {

    private static final Instant DUE = PrescriptionHarness.T0;

    private PrescriptionHarness h;
    private DoseScheduleService job;

    @BeforeEach
    void setUp() {
        h = new PrescriptionHarness();
        job = h.doseScheduleService;
    }

    @AfterEach
    void tearDown() {
        signOut();
    }

    private DoseLog dose(String id, Instant scheduledAt, DoseStatus status) {
        DoseLog d = new DoseLog();
        d.setId(id);
        d.setPrescriptionId("rx1");
        d.setPatientId("p1");
        d.setScheduledAt(scheduledAt);
        d.setStatus(status);
        h.doseLogs.add(d);
        return d;
    }

    private DoseLog byId(String id) {
        return h.doseLogs.stream().filter(d -> d.getId().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void aDoseWithinTheGracePeriodIsLeftAlone() {
        dose("d1", DUE, DoseStatus.UPCOMING);
        h.clock.set(DUE.plus(Duration.ofMinutes(29)).plusSeconds(59));

        assertThat(job.markMissed()).isZero();
        assertThat(byId("d1").getStatus()).isEqualTo(DoseStatus.UPCOMING);
    }

    @Test
    void aDoseBecomesMissedAtTheGraceLimitAndStaysMissed() {
        dose("d1", DUE, DoseStatus.UPCOMING);
        h.clock.set(DUE.plus(Duration.ofMinutes(30)));

        assertThat(job.markMissed()).isEqualTo(1);
        assertThat(byId("d1").getStatus()).isEqualTo(DoseStatus.MISSED);
        assertThat(byId("d1").getTakenAt()).isNull();
    }

    @Test
    void theJobBecomesTrueOnlyAsTimePasses() {
        dose("d1", DUE, DoseStatus.UPCOMING);

        h.clock.set(DUE.plus(Duration.ofMinutes(5)));
        assertThat(job.markMissed()).isZero();
        h.clock.set(DUE.plus(Duration.ofMinutes(25)));
        assertThat(job.markMissed()).isZero();
        h.clock.set(DUE.plus(Duration.ofMinutes(35)));
        assertThat(job.markMissed()).isEqualTo(1);
    }

    @Test
    void aDoseThatWasTakenIsNeverMarkedMissed() {
        DoseLog taken = dose("d1", DUE, DoseStatus.TAKEN);
        taken.setTakenAt(DUE.plus(Duration.ofMinutes(10)));
        h.clock.set(DUE.plus(Duration.ofHours(5)));

        assertThat(job.markMissed()).isZero();
        assertThat(byId("d1").getStatus()).isEqualTo(DoseStatus.TAKEN);
        assertThat(byId("d1").getTakenAt()).isEqualTo(DUE.plus(Duration.ofMinutes(10)));
    }

    @Test
    void aDoseTakenJustBeforeTheJobRunsStaysTaken() {
        dose("d1", DUE, DoseStatus.UPCOMING);
        signIn(h.patient);
        h.clock.set(DUE.plus(Duration.ofMinutes(31))); // overdue, but the patient gets there first
        h.doseService.markTaken("d1", null);

        assertThat(job.markMissed()).isZero();
        assertThat(byId("d1").getStatus()).isEqualTo(DoseStatus.TAKEN);
        assertThat(byId("d1").isLate()).isTrue();
    }

    @Test
    void futureDosesAreNotTouched() {
        dose("later", DUE.plus(Duration.ofHours(2)), DoseStatus.UPCOMING);
        h.clock.set(DUE.plus(Duration.ofHours(1)));

        assertThat(job.markMissed()).isZero();
        assertThat(byId("later").getStatus()).isEqualTo(DoseStatus.UPCOMING);
    }

    @Test
    void runningTheJobAgainChangesNothing() {
        dose("d1", DUE, DoseStatus.UPCOMING);
        dose("d2", DUE.minus(Duration.ofHours(1)), DoseStatus.UPCOMING);
        h.clock.set(DUE.plus(Duration.ofHours(1)));

        assertThat(job.markMissed()).isEqualTo(2);
        assertThat(job.markMissed()).isZero();
        assertThat(job.markMissed()).isZero();
    }

    @Test
    void everyOverdueDoseIsCaughtInOneRunAcrossPatients() {
        for (int i = 0; i < 5; i++) {
            DoseLog d = dose("d" + i, DUE.minus(Duration.ofHours(i)), DoseStatus.UPCOMING);
            d.setPatientId(i % 2 == 0 ? "p1" : "p2");
        }
        h.clock.set(DUE.plus(Duration.ofMinutes(31)));

        assertThat(job.markMissed()).isEqualTo(5);
        assertThat(h.doseLogs).extracting(DoseLog::getStatus).containsOnly(DoseStatus.MISSED);
    }

    @Test
    void theGraceIsConfigurable() {
        org.springframework.test.util.ReflectionTestUtils.setField(job, "graceMinutes", 60);
        dose("d1", DUE, DoseStatus.UPCOMING);

        h.clock.set(DUE.plus(Duration.ofMinutes(45)));
        assertThat(job.markMissed()).isZero();
        h.clock.set(DUE.plus(Duration.ofMinutes(60)));
        assertThat(job.markMissed()).isEqualTo(1);
    }

    @Test
    void aMissedDoseCanLaterBeTakenButNeverGoesBackToUpcoming() {
        dose("d1", DUE, DoseStatus.UPCOMING);
        h.clock.set(DUE.plus(Duration.ofHours(2)));
        job.markMissed();
        signIn(h.patient);

        assertThat(h.doseService.markTaken("d1", null).isLate()).isTrue();
        assertThat(job.markMissed()).isZero();
        assertThat(byId("d1").getStatus()).isEqualTo(DoseStatus.TAKEN);
    }

    @Test
    void theGraceMustBeBetweenOneMinuteAndOneDay() {
        for (int bad : new int[]{0, -5, 1441}) {
            org.springframework.test.util.ReflectionTestUtils.setField(job, "graceMinutes", bad);
            org.assertj.core.api.Assertions.assertThatThrownBy(job::checkGrace).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MISSED_GRACE_MINUTES");
        }
        for (int ok : new int[]{1, 30, 1440}) {
            org.springframework.test.util.ReflectionTestUtils.setField(job, "graceMinutes", ok);
            job.checkGrace();
        }
    }

    @Test
    void theShippedConfigurationReadsTheEnvVarWithADefaultOf30() throws Exception {
        String properties = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/application.properties"));

        assertThat(properties).contains("doses.missed-grace-minutes=${MISSED_GRACE_MINUTES:30}");
    }
}
