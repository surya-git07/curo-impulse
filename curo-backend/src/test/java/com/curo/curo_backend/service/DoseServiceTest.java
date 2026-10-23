package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.DoseView;
import com.curo.curo_backend.dto.PagedResponse;
import com.curo.curo_backend.dto.TakenRequest;
import com.curo.curo_backend.model.DoseLog;
import com.curo.curo_backend.model.DoseStatus;
import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.support.PrescriptionHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static com.curo.curo_backend.support.Fixtures.signIn;
import static com.curo.curo_backend.support.Fixtures.signOut;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** "Now" is 2026-10-23 10:00 UTC, which is 15:30 on the 23rd in India. */
class DoseServiceTest {

    private static final Instant T0 = PrescriptionHarness.T0;

    private PrescriptionHarness h;
    private DoseService service;
    private Prescription rx;

    @BeforeEach
    void setUp() {
        h = new PrescriptionHarness();
        service = h.doseService;
        rx = h.existing("Amoxicillin");
        rx.setDose("500 mg");
        signIn(h.patient);
    }

    @AfterEach
    void tearDown() {
        signOut();
    }

    private DoseLog dose(String id, Instant scheduledAt, DoseStatus status) {
        return dose(id, scheduledAt, status, "p1");
    }

    private DoseLog dose(String id, Instant scheduledAt, DoseStatus status, String patientId) {
        DoseLog d = new DoseLog();
        d.setId(id);
        d.setPrescriptionId(rx.getId());
        d.setPatientId(patientId);
        d.setScheduledAt(scheduledAt);
        d.setStatus(status);
        h.doseLogs.add(d);
        return d;
    }

    private void assertApiError(Supplier<?> call, HttpStatus status, String code) {
        assertThatThrownBy(call::get).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    private TakenRequest takenAt(Instant at) {
        TakenRequest r = new TakenRequest();
        r.setTakenAt(at);
        return r;
    }

    // ---- today

    @Test
    void todayListsOnlyTodaysDosesInIndianTimeEarliestFirstWithDrugAndDose() {
        dose("evening", Instant.parse("2026-10-23T14:30:00Z"), DoseStatus.UPCOMING);        // 20:00 IST today
        dose("morning", Instant.parse("2026-10-23T02:30:00Z"), DoseStatus.TAKEN);           // 08:00 IST today
        dose("justAfterMidnight", Instant.parse("2026-10-22T18:31:00Z"), DoseStatus.MISSED); // 00:01 IST today
        dose("yesterday", Instant.parse("2026-10-22T18:29:00Z"), DoseStatus.MISSED);         // 23:59 IST yesterday
        dose("tomorrow", Instant.parse("2026-10-23T18:30:00Z"), DoseStatus.UPCOMING);        // 00:00 IST tomorrow

        List<DoseView> today = service.today();

        assertThat(today).extracting(DoseView::getId).containsExactly("justAfterMidnight", "morning", "evening");
        assertThat(today.get(2).getDrug()).isEqualTo("Amoxicillin");
        assertThat(today.get(2).getDose()).isEqualTo("500 mg");
        assertThat(today.get(2).getPrescriptionId()).isEqualTo(rx.getId());
        assertThat(today.get(2).getScheduledAt()).isEqualTo(Instant.parse("2026-10-23T14:30:00Z"));
        assertThat(today.get(2).getStatus()).isEqualTo(DoseStatus.UPCOMING);
    }

    @Test
    void todayNeverShowsAnotherPatientsDoses() {
        dose("mine", Instant.parse("2026-10-23T14:30:00Z"), DoseStatus.UPCOMING);
        dose("theirs", Instant.parse("2026-10-23T14:30:00Z"), DoseStatus.UPCOMING, "p2");

        assertThat(service.today()).extracting(DoseView::getId).containsExactly("mine");
    }

    @Test
    void todayIsEmptyWhenNothingIsDue() {
        assertThat(service.today()).isEmpty();
    }

    // ---- taken

    @Test
    void markingADoseTakenRecordsWhenAndByWhom() {
        dose("d1", Instant.parse("2026-10-23T14:30:00Z"), DoseStatus.UPCOMING);
        h.clock.set(Instant.parse("2026-10-23T14:35:00Z"));

        DoseView view = service.markTaken("d1", null);

        assertThat(view.getStatus()).isEqualTo(DoseStatus.TAKEN);
        assertThat(view.getTakenAt()).isEqualTo(Instant.parse("2026-10-23T14:35:00Z"));
        assertThat(view.isLate()).isFalse();
        assertThat(view.getMarkedBy().getUserId()).isEqualTo("p1");
        assertThat(view.getMarkedBy().getRole()).isEqualTo("PATIENT");
        assertThat(view.getDrug()).isEqualTo("Amoxicillin");
        DoseLog stored = h.doseLogs.get(0);
        assertThat(stored.getStatus()).isEqualTo(DoseStatus.TAKEN);
        assertThat(stored.getTakenByUserId()).isEqualTo("p1");
    }

    @Test
    void anExplicitEarlierTakenAtIsAccepted() {
        dose("d1", Instant.parse("2026-10-23T02:30:00Z"), DoseStatus.UPCOMING);

        DoseView view = service.markTaken("d1", takenAt(Instant.parse("2026-10-23T02:40:00Z")));

        assertThat(view.getTakenAt()).isEqualTo(Instant.parse("2026-10-23T02:40:00Z"));
    }

    @Test
    void aSecondTapOnTheSameDoseIs409AndChangesNothing() {
        dose("d1", Instant.parse("2026-10-23T14:30:00Z"), DoseStatus.UPCOMING);
        Instant first = Instant.parse("2026-10-23T10:00:00Z");
        service.markTaken("d1", takenAt(first));
        h.clock.advance(Duration.ofMinutes(5));

        assertApiError(() -> service.markTaken("d1", null), HttpStatus.CONFLICT, "DOSE_ALREADY_TAKEN");
        assertThat(h.doseLogs.get(0).getTakenAt()).as("the first record is kept").isEqualTo(first);
    }

    @Test
    void aDoseAlreadyTakenIs409EvenWhenSeededThatWay() {
        DoseLog d = dose("d1", Instant.parse("2026-10-23T02:30:00Z"), DoseStatus.TAKEN);
        d.setTakenAt(Instant.parse("2026-10-23T02:31:00Z"));

        assertApiError(() -> service.markTaken("d1", null), HttpStatus.CONFLICT, "DOSE_ALREADY_TAKEN");
    }

    @Test
    void ifSomeoneElseWinsTheRaceTheLoserGets409() {
        dose("d1", Instant.parse("2026-10-23T14:30:00Z"), DoseStatus.UPCOMING);
        DoseUpdates racing = mock(DoseUpdates.class);
        when(racing.markTaken(anyString(), any(Instant.class), anyBoolean(), anyString())).thenReturn(false);
        ReflectionTestUtils.setField(service, "doseUpdates", racing);

        assertApiError(() -> service.markTaken("d1", null), HttpStatus.CONFLICT, "DOSE_ALREADY_TAKEN");
    }

    @Test
    void anUnknownDoseIs404() {
        assertApiError(() -> service.markTaken("nope", null), HttpStatus.NOT_FOUND, "DOSE_NOT_FOUND");
    }

    @Test
    void anotherPatientsDoseLooksUnknown() {
        dose("theirs", Instant.parse("2026-10-23T14:30:00Z"), DoseStatus.UPCOMING, "p2");

        assertApiError(() -> service.markTaken("theirs", null), HttpStatus.NOT_FOUND, "DOSE_NOT_FOUND");
        assertThat(h.doseLogs.get(0).getStatus()).isEqualTo(DoseStatus.UPCOMING);
    }

    @Test
    void takenAtCannotBeInTheFuture() {
        dose("d1", Instant.parse("2026-10-23T14:30:00Z"), DoseStatus.UPCOMING);

        assertApiError(() -> service.markTaken("d1", takenAt(T0.plusSeconds(1))), HttpStatus.BAD_REQUEST, "INVALID_TIME");
        assertThat(h.doseLogs.get(0).getStatus()).isEqualTo(DoseStatus.UPCOMING);
    }

    @Test
    void aDoseTakenMoreThanTheGracePeriodAfterItWasDueIsLate() {
        dose("onTime", T0.minus(Duration.ofMinutes(30)), DoseStatus.UPCOMING);
        dose("late", T0.minus(Duration.ofMinutes(31)), DoseStatus.UPCOMING);

        assertThat(service.markTaken("onTime", null).isLate()).as("exactly at the grace limit").isFalse();
        assertThat(service.markTaken("late", null).isLate()).as("job has not run yet, but it is late").isTrue();
    }

    @Test
    void aMissedDoseCanStillBeMarkedTakenAndIsFlaggedLate() {
        dose("d1", T0.minus(Duration.ofHours(3)), DoseStatus.MISSED);

        DoseView view = service.markTaken("d1", null);

        assertThat(view.getStatus()).isEqualTo(DoseStatus.TAKEN);
        assertThat(view.isLate()).isTrue();
    }

    @Test
    void onlyPatientsMarkTheirOwnDoses() {
        dose("d1", Instant.parse("2026-10-23T14:30:00Z"), DoseStatus.UPCOMING);
        for (var user : List.of(h.doctor, h.pharmacy, h.caregiver)) {
            signIn(user);
            assertApiError(() -> service.markTaken("d1", null), HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
            assertApiError(service::today, HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
        }
        assertThat(h.doseLogs.get(0).getStatus()).isEqualTo(DoseStatus.UPCOMING);
    }

    @Test
    void notSignedInIs401() {
        signOut();
        assertApiError(service::today, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
    }

    @Test
    void thereIsNoWayForAClientToSetMissed() {
        // the only dose-changing method a patient can call is markTaken, and the request carries nothing but takenAt
        assertThat(TakenRequest.class.getDeclaredFields()).extracting(java.lang.reflect.Field::getName).containsExactly("takenAt");
        dose("d1", Instant.parse("2026-10-23T14:30:00Z"), DoseStatus.UPCOMING);
        service.markTaken("d1", null);
        assertThat(h.doseLogs.get(0).getStatus()).isNotEqualTo(DoseStatus.MISSED);
    }

    // ---- the log

    private Query logQuery(Instant from, Instant to, String status, int page, int size) {
        MongoTemplate template = mock(MongoTemplate.class);
        when(template.count(any(Query.class), eq(DoseLog.class))).thenReturn(0L);
        when(template.find(any(Query.class), eq(DoseLog.class))).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "mongoTemplate", template);
        service.log(from, to, status, page, size);
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(template).find(captor.capture(), eq(DoseLog.class));
        return captor.getValue();
    }

    @Test
    void theLogIsThePatientsOwnAndShowsWhatHappenedNewestFirst() {
        Query q = logQuery(null, null, null, 1, 10);
        String where = q.getQueryObject().toString();

        assertThat(where).contains("patientId").contains("p1").contains("TAKEN").contains("MISSED").doesNotContain("UPCOMING");
        assertThat(q.getSkip()).isEqualTo(10);
        assertThat(q.getLimit()).isEqualTo(10);
        assertThat(q.getSortObject().toJson()).contains("scheduledAt").contains("-1");
    }

    @Test
    void theLogCanBeFilteredByStatusAndTime() {
        String where = logQuery(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"), "upcoming", 0, 20)
                .getQueryObject().toString();

        assertThat(where).contains("UPCOMING").contains("$gte").contains("$lt").doesNotContain("TAKEN");
    }

    @Test
    void theLogShowsDrugAndDosePerEntry() {
        DoseLog d = dose("d1", Instant.parse("2026-10-22T14:30:00Z"), DoseStatus.MISSED);
        MongoTemplate template = mock(MongoTemplate.class);
        when(template.count(any(Query.class), eq(DoseLog.class))).thenReturn(1L);
        when(template.find(any(Query.class), eq(DoseLog.class))).thenReturn(List.of(d));
        ReflectionTestUtils.setField(service, "mongoTemplate", template);

        PagedResponse<DoseView> page = service.log(null, null, null, 0, 20);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getItems().get(0).getDrug()).isEqualTo("Amoxicillin");
        assertThat(page.getItems().get(0).getStatus()).isEqualTo(DoseStatus.MISSED);
    }

    @Test
    void theLogValidatesStatusAndPaging() {
        assertApiError(() -> service.log(null, null, "SOMETIMES", 0, 20), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertApiError(() -> service.log(null, null, null, -1, 20), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertApiError(() -> service.log(null, null, null, 0, 0), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertApiError(() -> service.log(null, null, null, 0, 101), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }

    @Test
    void onlyPatientsHaveALog() {
        signIn(h.doctor);
        assertApiError(() -> service.log(null, null, null, 0, 20), HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
    }
}
