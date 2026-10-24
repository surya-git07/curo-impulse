package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.DoctorOption;
import com.curo.curo_backend.dto.QueueEntryView;
import com.curo.curo_backend.dto.QueueView;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.AuditOutcome;
import com.curo.curo_backend.model.QueueEntry;
import com.curo.curo_backend.model.QueueStatus;
import com.curo.curo_backend.model.VerificationStatus;
import com.curo.curo_backend.repository.QueueEntryRepository;
import com.curo.curo_backend.repository.QueueQueries;
import com.curo.curo_backend.repository.UserRepository;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueueServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T05:00:00Z"); // 10:30 in India

    private final QueueEntryRepository queue = mock(QueueEntryRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final AuditService audit = mock(AuditService.class);
    private final QueueService service = new QueueService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "queueRepository", queue);
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "mongoTemplate", mongo);
        ReflectionTestUtils.setField(service, "currentUser", currentUser);
        ReflectionTestUtils.setField(service, "auditService", audit);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "timeZone", "Asia/Kolkata");
        when(queue.save(any(QueueEntry.class))).thenAnswer(inv -> {
            QueueEntry e = inv.getArgument(0);
            if (e.getId() == null) {
                e.setId("q1");
            }
            return e;
        });
    }

    private AppUser user(String id, String role) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setRole(role);
        u.setName("Test " + role);
        return u;
    }

    private void signedInAs(String role) {
        when(currentUser.require()).thenReturn(user("u-" + role, role));
    }

    private void patientExists() {
        AppUser p = user("p1", "PATIENT");
        p.setName("Test Patient");
        p.setCareId("CID-7K3MQ9");
        when(users.findByCareId("CID-7K3MQ9")).thenReturn(Optional.of(p));
    }

    private AppUser verifiedDoctor(String id, String name) {
        AppUser d = user(id, "DOCTOR");
        d.setName(name);
        d.setCareId("CID-DCTR22");
        d.setVerificationStatus(VerificationStatus.VERIFIED);
        d.setPhone("9000000002");
        d.setLicenceNumber("TN-48213");
        when(users.findById(id)).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    void receptionChecksAKnownPatientIn() {
        signedInAs("RECEPTION");
        patientExists();
        verifiedDoctor("d1", "Dr Test Doctor");

        QueueEntryView view = service.checkIn(" cid-7k3mq9 ", "d1");

        assertThat(view.getCareId()).isEqualTo("CID-7K3MQ9");
        assertThat(view.getPatientName()).isEqualTo("Test Patient");
        assertThat(view.getDoctorId()).isEqualTo("d1");
        assertThat(view.getDoctorName()).isEqualTo("Dr Test Doctor");
        assertThat(view.getStatus()).isEqualTo(QueueStatus.WAITING);
        assertThat(view.getCheckedInAt()).isEqualTo(NOW);
        ArgumentCaptor<QueueEntry> saved = ArgumentCaptor.forClass(QueueEntry.class);
        verify(queue).save(saved.capture());
        assertThat(saved.getValue().getCheckedInBy()).isEqualTo("u-RECEPTION");
        assertThat(saved.getValue().getDoctorId()).isEqualTo("d1");
        assertThat(saved.getValue().getDoctorName()).isEqualTo("Dr Test Doctor");
        assertThat(saved.getValue().getCheckedInAt()).isEqualTo(NOW);
        verify(audit).recordQuietly(any(AppUser.class), any(AppUser.class), eq(AuditAction.WRITE), eq("QUEUE_ENTRY"), eq("q1"),
                eq((String) null), eq(AuditOutcome.ALLOWED));
    }

    @Test
    void anUnknownOrNonPatientCareIdIsNotFound() {
        signedInAs("RECEPTION");
        verifiedDoctor("d1", "Dr Test Doctor");
        when(users.findByCareId("CID-7K3MQ9")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.checkIn("CID-7K3MQ9", "d1")).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(e.getCode()).isEqualTo("PATIENT_NOT_FOUND");
        });
        AppUser doctor = user("d1", "DOCTOR");
        when(users.findByCareId("CID-7K3MQ9")).thenReturn(Optional.of(doctor));
        assertThatThrownBy(() -> service.checkIn("CID-7K3MQ9", "d1")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("PATIENT_NOT_FOUND"));
        assertThatThrownBy(() -> service.checkIn("not a careid", "d1")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("PATIENT_NOT_FOUND"));
        verify(queue, never()).save(any(QueueEntry.class));
    }

    @Test
    void aPatientAlreadyWaitingForThatDoctorTodayIsA409() {
        signedInAs("RECEPTION");
        patientExists();
        verifiedDoctor("d1", "Dr Test Doctor");
        when(mongo.exists(any(Query.class), eq(QueueEntry.class))).thenReturn(true);
        assertThatThrownBy(() -> service.checkIn("CID-7K3MQ9", "d1")).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(e.getCode()).isEqualTo("ALREADY_IN_QUEUE");
        });
        verify(queue, never()).save(any(QueueEntry.class));
    }

    @Test
    void theDuplicateCheckIsPerDoctor() {
        signedInAs("RECEPTION");
        patientExists();
        verifiedDoctor("d2", "Dr Other");
        service.checkIn("CID-7K3MQ9", "d2");
        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongo).exists(q.capture(), eq(QueueEntry.class));
        // waiting for this doctor only: waiting for another doctor does not block the check-in
        assertThat(q.getValue().getQueryObject().get("doctorId")).isEqualTo("d2");
        assertThat(q.getValue().getQueryObject().get("careId")).isEqualTo("CID-7K3MQ9");
    }

    @Test
    void theDoctorMustBeAVerifiedLiveDoctorElseInvalidDoctor() {
        signedInAs("RECEPTION");
        patientExists();
        AppUser pending = verifiedDoctor("d-pending", "Dr Pending");
        pending.setVerificationStatus(VerificationStatus.PENDING);
        AppUser rejected = verifiedDoctor("d-rejected", "Dr Rejected");
        rejected.setVerificationStatus(VerificationStatus.REJECTED);
        AppUser deleted = verifiedDoctor("d-deleted", "Dr Deleted");
        deleted.setDeletedAt(NOW);
        AppUser pharmacy = verifiedDoctor("pharm", "Not A Doctor");
        pharmacy.setRole("PHARMACY");
        for (String doctorId : new String[]{null, "", "  ", "nobody", "d-pending", "d-rejected", "d-deleted", "pharm"}) {
            assertThatThrownBy(() -> service.checkIn("CID-7K3MQ9", doctorId)).as(String.valueOf(doctorId))
                    .isInstanceOfSatisfying(ApiException.class, e -> {
                        assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                        assertThat(e.getCode()).isEqualTo("INVALID_DOCTOR");
                    });
        }
        verify(queue, never()).save(any(QueueEntry.class));
    }

    @Test
    void receptionListsOnlyVerifiedDoctorsWithNameAndIdsOnly() {
        signedInAs("RECEPTION");
        AppUser b = verifiedDoctor("d2", "dr bravo");
        AppUser a = verifiedDoctor("d1", "Dr Alpha");
        when(users.findByRoleAndVerificationStatusAndDeletedAtIsNull("DOCTOR", VerificationStatus.VERIFIED)).thenReturn(List.of(b, a));

        List<DoctorOption> doctors = service.verifiedDoctors();

        assertThat(doctors).extracting(DoctorOption::getName).containsExactly("Dr Alpha", "dr bravo");
        assertThat(doctors.get(0).getId()).isEqualTo("d1");
        assertThat(doctors.get(0).getCareId()).isEqualTo(a.getCareId());
        // the option has no phone or licence details to leak
        assertThat(DoctorOption.class.getDeclaredFields()).extracting(java.lang.reflect.Field::getName)
                .containsExactlyInAnyOrder("id", "careId", "name");
    }

    @Test
    void onlyReceptionListsDoctors() {
        for (String role : new String[]{"DOCTOR", "PATIENT", "PHARMACY", "HOSPITAL", "CAREGIVER", "ADMIN"}) {
            signedInAs(role);
            assertThatThrownBy(service::verifiedDoctors).as(role).isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        }
    }

    @Test
    void onlyReceptionChecksInAndOnlyDoctorsReadOrCloseTheQueue() {
        for (String role : new String[]{"DOCTOR", "PATIENT", "PHARMACY", "HOSPITAL", "CAREGIVER", "ADMIN"}) {
            signedInAs(role);
            assertThatThrownBy(() -> service.checkIn("CID-7K3MQ9", "d1")).as(role).isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        }
        for (String role : new String[]{"RECEPTION", "PATIENT", "PHARMACY", "HOSPITAL", "CAREGIVER", "ADMIN"}) {
            signedInAs(role);
            assertThatThrownBy(service::todaysQueue).as(role).isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
            assertThatThrownBy(() -> service.markDone("q1")).as(role).isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        }
    }

    private QueueEntry entry(String id, String careId, QueueStatus status) {
        QueueEntry e = new QueueEntry();
        e.setId(id);
        e.setCareId(careId);
        e.setPatientName("Name " + id);
        e.setDoctorId("u-DOCTOR");
        e.setDoctorName("Test DOCTOR");
        e.setStatus(status);
        e.setCheckedInAt(NOW);
        e.setCheckedInBy("r1");
        return e;
    }

    @Test
    void theDoctorQueueIsFilteredToTheSignedInDoctor() {
        signedInAs("DOCTOR");
        when(mongo.find(any(Query.class), eq(QueueEntry.class))).thenReturn(List.of());
        service.todaysQueue();
        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(q.capture(), eq(QueueEntry.class));
        assertThat(q.getValue().getQueryObject().get("doctorId")).isEqualTo("u-DOCTOR");
    }

    @Test
    void theDoctorQueueListsTodayWithCounts() {
        signedInAs("DOCTOR");
        when(mongo.find(any(Query.class), eq(QueueEntry.class))).thenReturn(List.of(
                entry("q1", "CID-AAAAA2", QueueStatus.WAITING),
                entry("q2", "CID-BBBBB2", QueueStatus.DONE),
                entry("q3", "CID-CCCCC2", QueueStatus.WAITING)));

        QueueView view = service.todaysQueue();

        assertThat(view.getCounts().getTotal()).isEqualTo(3);
        assertThat(view.getCounts().getDone()).isEqualTo(1);
        assertThat(view.getCounts().getPending()).isEqualTo(2);
        assertThat(view.getEntries()).extracting(QueueEntryView::getId).containsExactly("q1", "q2", "q3");
        // nothing but identity, name, status and time is exposed
        assertThat(view.getEntries().get(0)).hasNoNullFieldsOrProperties();
    }

    @Test
    void todayIsTheIndianDay() {
        signedInAs("DOCTOR");
        when(mongo.find(any(Query.class), eq(QueueEntry.class))).thenReturn(List.of());
        service.todaysQueue();
        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(q.capture(), eq(QueueEntry.class));
        Document range = (Document) q.getValue().getQueryObject().get("checkedInAt");
        // 23 Oct 2026 in India starts at 22 Oct 18:30 UTC
        assertThat(range.get("$gte")).isEqualTo(Instant.parse("2026-10-22T18:30:00Z"));
        assertThat(range.get("$lt")).isEqualTo(Instant.parse("2026-10-23T18:30:00Z"));
    }

    @Test
    void aDifferentDoctorDoesNotSeeTheEntry() {
        // reception checks the patient in for d1; the store answers queries by the doctorId in them
        java.util.List<QueueEntry> store = new java.util.ArrayList<>();
        when(queue.save(any(QueueEntry.class))).thenAnswer(inv -> {
            QueueEntry e = inv.getArgument(0);
            e.setId("q-" + store.size());
            store.add(e);
            return e;
        });
        when(mongo.find(any(Query.class), eq(QueueEntry.class))).thenAnswer(inv -> {
            Object wanted = ((Query) inv.getArgument(0)).getQueryObject().get("doctorId");
            return store.stream().filter(e -> wanted.equals(e.getDoctorId())).toList();
        });
        when(queue.findById("q-0")).thenAnswer(inv -> store.stream().findFirst());
        signedInAs("RECEPTION");
        patientExists();
        AppUser d1 = verifiedDoctor("d1", "Dr Test Doctor");
        service.checkIn("CID-7K3MQ9", "d1");

        when(currentUser.require()).thenReturn(d1);
        assertThat(service.todaysQueue().getEntries()).hasSize(1);
        assertThat(service.todaysQueue().getCounts().getPending()).isEqualTo(1);

        signedInAs("DOCTOR"); // a different doctor, id u-DOCTOR
        QueueView other = service.todaysQueue();
        assertThat(other.getEntries()).isEmpty();
        assertThat(other.getCounts().getTotal()).isZero();
        assertThatThrownBy(() -> service.markDone("q-0")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void aDoctorCannotMarkAnotherDoctorsEntryDone() {
        signedInAs("DOCTOR");
        QueueEntry other = entry("q9", "CID-AAAAA2", QueueStatus.WAITING);
        other.setDoctorId("someone-else");
        when(queue.findById("q9")).thenReturn(Optional.of(other));
        QueueEntry legacy = entry("q8", "CID-BBBBB2", QueueStatus.WAITING);
        legacy.setDoctorId(null);
        when(queue.findById("q8")).thenReturn(Optional.of(legacy));
        for (String id : new String[]{"q9", "q8"}) {
            assertThatThrownBy(() -> service.markDone(id)).as(id).isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        }
        assertThat(other.getStatus()).isEqualTo(QueueStatus.WAITING);
        verify(queue, never()).save(any(QueueEntry.class));
    }

    @Test
    void markDoneSetsDoneAndIsRepeatable() {
        signedInAs("DOCTOR");
        QueueEntry e = entry("q1", "CID-AAAAA2", QueueStatus.WAITING);
        when(queue.findById("q1")).thenReturn(Optional.of(e));
        assertThat(service.markDone("q1").getStatus()).isEqualTo(QueueStatus.DONE);
        assertThat(service.markDone("q1").getStatus()).isEqualTo(QueueStatus.DONE);
        when(queue.findById("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.markDone("nope")).isInstanceOfSatisfying(ApiException.class,
                x -> assertThat(x.getCode()).isEqualTo("QUEUE_ENTRY_NOT_FOUND"));
    }

    @Test
    void theQueueQueriesChainBothBoundsOnOneCheckedInAt() {
        Instant a = Instant.parse("2026-10-22T18:30:00Z");
        Instant b = Instant.parse("2026-10-23T18:30:00Z");
        Document between = QueueQueries.betweenFor("d1", a, b).getQueryObject();
        assertThat(((Document) between.get("checkedInAt")).keySet()).isEqualTo(Set.of("$gte", "$lt"));
        Document waiting = QueueQueries.waitingFor("CID-7K3MQ9", "d1", a, b).getQueryObject();
        assertThat(((Document) waiting.get("checkedInAt")).keySet()).isEqualTo(Set.of("$gte", "$lt"));
        assertThat(waiting.get("status")).isEqualTo(QueueStatus.WAITING);
        assertThat(waiting.get("doctorId")).isEqualTo("d1");
        assertThat(between.get("doctorId")).isEqualTo("d1");
    }
}
