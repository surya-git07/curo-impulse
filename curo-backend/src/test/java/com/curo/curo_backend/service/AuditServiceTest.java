package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.AuditEntryView;
import com.curo.curo_backend.dto.PagedResponse;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.AuditLog;
import com.curo.curo_backend.model.AuditOutcome;
import com.curo.curo_backend.repository.AuditLogRepository;
import com.curo.curo_backend.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static com.curo.curo_backend.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-23T10:00:00Z");

    private final AppUser patient = user("p1", "PATIENT", "Test Patient", "CID-7K3MQ9");
    private final AppUser doctor = user("d1", "DOCTOR", "Dr Test", "CID-4HN8XD");
    private AuditLogRepository repo;
    private MongoTemplate template;
    private AuditService service;

    @BeforeEach
    void setUp() {
        repo = mock(AuditLogRepository.class);
        template = mock(MongoTemplate.class);
        service = new AuditService();
        ReflectionTestUtils.setField(service, "auditLogRepository", repo);
        ReflectionTestUtils.setField(service, "mongoTemplate", template);
        ReflectionTestUtils.setField(service, "clock", new MutableClock(T0));
        doctor.setFacilityName("Test Hospital");
    }

    @Test
    void recordsWhoWhatWhenAndOutcome() {
        service.record(patient, doctor, AuditAction.READ, "PROFILE", "CID-7K3MQ9", "g1", AuditOutcome.ALLOWED);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repo).save(captor.capture());
        AuditLog e = captor.getValue();
        assertThat(e.getPatientId()).isEqualTo("p1");
        assertThat(e.getCareId()).isEqualTo("CID-7K3MQ9");
        assertThat(e.getActorUserId()).isEqualTo("d1");
        assertThat(e.getActorName()).isEqualTo("Dr Test");
        assertThat(e.getActorRole()).isEqualTo("DOCTOR");
        assertThat(e.getActorFacilityName()).isEqualTo("Test Hospital");
        assertThat(e.getAction()).isEqualTo(AuditAction.READ);
        assertThat(e.getResource()).isEqualTo("PROFILE");
        assertThat(e.getGrantId()).isEqualTo("g1");
        assertThat(e.getOutcome()).isEqualTo(AuditOutcome.ALLOWED);
        assertThat(e.getAt()).isEqualTo(T0);
    }

    @Test
    void automaticEventsAreAttributedToTheSystem() {
        service.record(patient, null, AuditAction.GRANT_EXPIRED, "ACCESS_GRANT", "g1", "g1", AuditOutcome.ALLOWED);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getActorRole()).isEqualTo("SYSTEM");
        assertThat(captor.getValue().getActorUserId()).isNull();
    }

    @Test
    void strictRecordFailsLoudlyAndQuietRecordDoesNot() {
        when(repo.save(any(AuditLog.class))).thenThrow(new DataAccessResourceFailureException("down"));

        assertThatThrownBy(() -> service.record(patient, doctor, AuditAction.READ, "PROFILE", null, null, AuditOutcome.ALLOWED))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThatCode(() -> service.recordQuietly(patient, doctor, AuditAction.READ, "PROFILE", null, null, AuditOutcome.DENIED))
                .doesNotThrowAnyException();
    }

    // ---- reading the log

    private Query captureQuery(Instant from, Instant to, String role, int page, int size) {
        when(template.count(any(Query.class), eq(AuditLog.class))).thenReturn(0L);
        when(template.find(any(Query.class), eq(AuditLog.class))).thenReturn(List.of());
        service.listForPatient("p1", from, to, role, page, size);
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(template).find(captor.capture(), eq(AuditLog.class));
        return captor.getValue();
    }

    @Test
    void logIsScopedToTheOnePatientAndNewestFirst() {
        Query q = captureQuery(null, null, null, 2, 10);

        assertThat(q.getQueryObject().toString()).contains("patientId").contains("p1");
        assertThat(q.getSkip()).isEqualTo(20);
        assertThat(q.getLimit()).isEqualTo(10);
        assertThat(q.getSortObject().toJson()).contains("at").contains("-1");
    }

    @Test
    void logFiltersByTimeRangeAndActorRole() {
        Query q = captureQuery(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"), "doctor", 0, 20);
        String json = q.getQueryObject().toString();

        assertThat(json).contains("$gte").contains("$lt").contains("DOCTOR");
    }

    @Test
    void logEntriesAreShownInTheContractShape() {
        AuditLog entry = new AuditLog();
        entry.setId("e1");
        entry.setPatientId("p1");
        entry.setActorUserId("d1");
        entry.setActorName("Dr Test");
        entry.setActorRole("DOCTOR");
        entry.setActorFacilityName("Test Hospital");
        entry.setAction(AuditAction.READ);
        entry.setResource("PROFILE");
        entry.setResourceId("CID-7K3MQ9");
        entry.setGrantId("g1");
        entry.setOutcome(AuditOutcome.ALLOWED);
        entry.setAt(T0);
        when(template.count(any(Query.class), eq(AuditLog.class))).thenReturn(41L);
        when(template.find(any(Query.class), eq(AuditLog.class))).thenReturn(List.of(entry));

        PagedResponse<AuditEntryView> page = service.listForPatient("p1", null, null, null, 0, 20);

        assertThat(page.getTotal()).isEqualTo(41);
        AuditEntryView v = page.getItems().get(0);
        assertThat(v.getActor().getName()).isEqualTo("Dr Test");
        assertThat(v.getActor().getFacilityName()).isEqualTo("Test Hospital");
        assertThat(v.getAction()).isEqualTo(AuditAction.READ);
        assertThat(v.getGrantId()).isEqualTo("g1");
        assertThat(v.getOutcome()).isEqualTo(AuditOutcome.ALLOWED);
        assertThat(v.getAt()).isEqualTo(T0);
    }

    @Test
    void pagingIsValidated() {
        for (int[] bad : new int[][]{{-1, 20}, {0, 0}, {0, 101}}) {
            assertThatThrownBy(() -> service.listForPatient("p1", null, null, null, bad[0], bad[1]))
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }
}
