package com.curo.curo_backend.service;

import com.curo.curo_backend.model.AccessGrant;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.AuditLog;
import com.curo.curo_backend.model.AuditOutcome;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.GrantStatus;
import com.curo.curo_backend.repository.AccessGrantRepository;
import com.curo.curo_backend.repository.AuditLogRepository;
import com.curo.curo_backend.repository.UserRepository;
import com.curo.curo_backend.support.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.curo.curo_backend.support.Fixtures.grant;
import static com.curo.curo_backend.support.Fixtures.signIn;
import static com.curo.curo_backend.support.Fixtures.signOut;
import static com.curo.curo_backend.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The check behind every patient-data endpoint: role plus an active, unexpired grant, otherwise 403 NO_ACTIVE_GRANT. */
class AccessGuardTest {

    private static final Instant T0 = Instant.parse("2026-10-23T10:00:00Z");
    private static final String CARE_ID = "CID-7K3MQ9";

    private final MutableClock clock = new MutableClock(T0);
    private final Map<String, AppUser> users = new HashMap<>();
    private final List<AccessGrant> grants = new ArrayList<>();
    private final List<AuditLog> audit = new ArrayList<>();
    private final AppUser patient = user("p1", "PATIENT", "Test Patient", CARE_ID);
    private final AppUser doctor = user("d1", "DOCTOR", "Dr Test", "CID-4HN8XD");

    private AuditLogRepository auditRepo;
    private AccessGuard guard;

    @BeforeEach
    void setUp() {
        for (AppUser u : List.of(patient, doctor)) {
            users.put(u.getId(), u);
        }
        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(users.get(inv.<String>getArgument(0))));
        when(userRepo.findByCareId(anyString())).thenAnswer(inv -> users.values().stream()
                .filter(u -> inv.getArgument(0).equals(u.getCareId())).findFirst());

        AccessGrantRepository grantRepo = mock(AccessGrantRepository.class);
        when(grantRepo.findByPatientIdAndGranteeIdAndStatus(anyString(), anyString(), any(GrantStatus.class))).thenAnswer(inv ->
                grants.stream().filter(g -> g.getPatientId().equals(inv.getArgument(0))
                        && g.getGranteeId().equals(inv.getArgument(1))
                        && g.getStatus() == inv.getArgument(2)).toList());
        when(grantRepo.save(any(AccessGrant.class))).thenAnswer(inv -> inv.getArgument(0));

        auditRepo = mock(AuditLogRepository.class);
        when(auditRepo.save(any(AuditLog.class))).thenAnswer(inv -> {
            audit.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        AuditService auditService = new AuditService();
        ReflectionTestUtils.setField(auditService, "auditLogRepository", auditRepo);
        ReflectionTestUtils.setField(auditService, "mongoTemplate", mock(MongoTemplate.class));
        ReflectionTestUtils.setField(auditService, "clock", clock);

        CurrentUserService currentUser = new CurrentUserService();
        ReflectionTestUtils.setField(currentUser, "userRepository", userRepo);

        guard = new AccessGuard();
        ReflectionTestUtils.setField(guard, "currentUser", currentUser);
        ReflectionTestUtils.setField(guard, "userRepository", userRepo);
        ReflectionTestUtils.setField(guard, "grantRepository", grantRepo);
        ReflectionTestUtils.setField(guard, "auditService", auditService);
        ReflectionTestUtils.setField(guard, "clock", clock);

        signIn(doctor);
    }

    @AfterEach
    void tearDown() {
        signOut();
    }

    private AccessGrant addGrant(GrantStatus status, Instant expiresAt, GrantScope... scope) {
        AccessGrant g = grant("g" + (grants.size() + 1), patient, doctor, status, expiresAt, scope);
        grants.add(g);
        return g;
    }

    private AccessGuard.Granted read(GrantScope scope) {
        return guard.require(CARE_ID, scope, AuditAction.READ, "PROFILE", CARE_ID);
    }

    private void assertNoActiveGrant(Supplier<?> call) {
        assertThatThrownBy(call::get).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(e.getCode()).isEqualTo("NO_ACTIVE_GRANT");
        });
    }

    // ---- the four cases

    @Test
    void noGrantIsRefused() {
        assertNoActiveGrant(() -> read(GrantScope.PROFILE));

        assertThat(audit).hasSize(1);
        AuditLog entry = audit.get(0);
        assertThat(entry.getOutcome()).isEqualTo(AuditOutcome.DENIED);
        assertThat(entry.getPatientId()).isEqualTo("p1");
        assertThat(entry.getActorUserId()).isEqualTo("d1");
        assertThat(entry.getActorRole()).isEqualTo("DOCTOR");
        assertThat(entry.getAction()).isEqualTo(AuditAction.READ);
        assertThat(entry.getGrantId()).isNull();
        assertThat(entry.getAt()).isEqualTo(T0);
    }

    @Test
    void expiredGrantIsRefusedAndMarkedExpired() {
        AccessGrant g = addGrant(GrantStatus.ACTIVE, T0.minusSeconds(1), GrantScope.PROFILE);

        assertNoActiveGrant(() -> read(GrantScope.PROFILE));

        assertThat(g.getStatus()).as("stored status catches up").isEqualTo(GrantStatus.EXPIRED);
        assertThat(audit).extracting(AuditLog::getAction).containsExactly(AuditAction.GRANT_EXPIRED, AuditAction.READ);
        assertThat(audit.get(0).getActorRole()).isEqualTo("SYSTEM");
        assertThat(audit.get(1).getOutcome()).isEqualTo(AuditOutcome.DENIED);
    }

    @Test
    void aGrantIsExpiredAtTheExactInstantItEnds() {
        addGrant(GrantStatus.ACTIVE, T0, GrantScope.PROFILE);
        assertNoActiveGrant(() -> read(GrantScope.PROFILE));
    }

    @Test
    void revokedGrantIsRefused() {
        addGrant(GrantStatus.REVOKED, T0.plus(Duration.ofHours(5)), GrantScope.PROFILE);

        assertNoActiveGrant(() -> read(GrantScope.PROFILE));
        assertThat(audit.get(0).getOutcome()).isEqualTo(AuditOutcome.DENIED);
    }

    @Test
    void activeGrantIsAllowedAndTheAccessIsLogged() {
        AccessGrant g = addGrant(GrantStatus.ACTIVE, T0.plus(Duration.ofHours(1)), GrantScope.PROFILE);

        AccessGuard.Granted granted = read(GrantScope.PROFILE);

        assertThat(granted.patient().getId()).isEqualTo("p1");
        assertThat(granted.grant()).isSameAs(g);
        assertThat(audit).hasSize(1);
        AuditLog entry = audit.get(0);
        assertThat(entry.getOutcome()).isEqualTo(AuditOutcome.ALLOWED);
        assertThat(entry.getAction()).isEqualTo(AuditAction.READ);
        assertThat(entry.getGrantId()).isEqualTo("g1");
        assertThat(entry.getActorUserId()).isEqualTo("d1");
        assertThat(entry.getPatientId()).isEqualTo("p1");
        assertThat(entry.getCareId()).isEqualTo(CARE_ID);
        assertThat(entry.getResource()).isEqualTo("PROFILE");
    }

    // ---- around the four cases

    @Test
    void aGrantStopsWorkingWhenTimeRunsOut() {
        addGrant(GrantStatus.ACTIVE, T0.plus(Duration.ofHours(1)), GrantScope.PROFILE);
        read(GrantScope.PROFILE);

        clock.advance(Duration.ofMinutes(59));
        read(GrantScope.PROFILE);

        clock.advance(Duration.ofMinutes(1));
        assertNoActiveGrant(() -> read(GrantScope.PROFILE));
    }

    @Test
    void aPendingRequestGivesNoAccess() {
        addGrant(GrantStatus.PENDING, null, GrantScope.PROFILE);
        assertNoActiveGrant(() -> read(GrantScope.PROFILE));
    }

    @Test
    void aGrantOnlyCoversItsScope() {
        addGrant(GrantStatus.ACTIVE, T0.plus(Duration.ofHours(1)), GrantScope.PROFILE);

        read(GrantScope.PROFILE);
        assertNoActiveGrant(() -> read(GrantScope.PRESCRIPTIONS));
        assertNoActiveGrant(() -> read(GrantScope.DOSES));
    }

    @Test
    void aGrantForAnotherPatientOrAnotherUserDoesNotCount() {
        AppUser otherPatient = user("p2", "PATIENT", "Other Patient", "CID-9PW2TC");
        AppUser otherDoctor = user("d2", "DOCTOR", "Dr Other", "CID-2DD2DD");
        users.put("p2", otherPatient);
        users.put("d2", otherDoctor);
        grants.add(grant("gx", otherPatient, doctor, GrantStatus.ACTIVE, T0.plus(Duration.ofHours(1)), GrantScope.PROFILE));
        grants.add(grant("gy", patient, otherDoctor, GrantStatus.ACTIVE, T0.plus(Duration.ofHours(1)), GrantScope.PROFILE));

        assertNoActiveGrant(() -> read(GrantScope.PROFILE));
    }

    @Test
    void aGrantWithNoExpiryStaysActive() {
        AppUser caregiver = user("c1", "CAREGIVER", "Test Caregiver", "CID-3CC3CC");
        users.put("c1", caregiver);
        grants.add(grant("gc", patient, caregiver, GrantStatus.ACTIVE, null, GrantScope.MEDICATIONS));
        signIn(caregiver);

        clock.advance(Duration.ofDays(400));

        assertThat(guard.require(CARE_ID, GrantScope.MEDICATIONS, AuditAction.READ, "MEDICATIONS", null).grant().getId()).isEqualTo("gc");
    }

    @Test
    void adminsSuperAdminsAndOtherPatientsNeverPassEvenWithAGrantRow() {
        for (String role : List.of("ADMIN", "SUPER_ADMIN", "PATIENT")) {
            AppUser outsider = user("o-" + role, role, "Outsider " + role, "CID-OUT" + role.charAt(0) + "AA");
            users.put(outsider.getId(), outsider);
            grants.add(grant("g-" + role, patient, outsider, GrantStatus.ACTIVE, T0.plus(Duration.ofHours(1)), GrantScope.PROFILE));
            signIn(outsider);

            assertNoActiveGrant(() -> read(GrantScope.PROFILE));
        }
    }

    @Test
    void thePatientThemselvesGoesThroughTheirOwnEndpointsNotTheGuard() {
        signIn(patient);
        assertNoActiveGrant(() -> read(GrantScope.PROFILE));
    }

    @Test
    void anUnknownCareIdLooksExactlyLikeAnUngrantedOne() {
        ApiException unknown = catchApi(() -> guard.require("CID-ZZZZZZ", GrantScope.PROFILE, AuditAction.READ, "PROFILE", null));
        ApiException ungranted = catchApi(() -> read(GrantScope.PROFILE));

        assertThat(unknown.getStatus()).isEqualTo(ungranted.getStatus());
        assertThat(unknown.getCode()).isEqualTo(ungranted.getCode());
        assertThat(unknown.getMessage()).isEqualTo(ungranted.getMessage());
        assertThat(audit).as("nothing to log against for an unknown patient").hasSize(1);
    }

    @Test
    void aDeletedPatientIsOutOfReach() {
        addGrant(GrantStatus.ACTIVE, T0.plus(Duration.ofHours(1)), GrantScope.PROFILE);
        patient.setDeletedAt(T0.minusSeconds(60));

        assertNoActiveGrant(() -> read(GrantScope.PROFILE));
    }

    @Test
    void careIdMatchingIgnoresCaseAndSpaces() {
        addGrant(GrantStatus.ACTIVE, T0.plus(Duration.ofHours(1)), GrantScope.PROFILE);

        assertThat(guard.require("  cid-7k3mq9 ", GrantScope.PROFILE, AuditAction.READ, "PROFILE", null).patient().getId()).isEqualTo("p1");
    }

    @Test
    void notSignedInIs401() {
        signOut();
        assertThatThrownBy(() -> read(GrantScope.PROFILE)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void aTokenForADeletedUserIs401() {
        doctor.setDeletedAt(T0.minusSeconds(5));
        assertThatThrownBy(() -> read(GrantScope.PROFILE)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    // ---- audit is part of the rule

    @Test
    void ifAnAllowedAccessCannotBeRecordedItIsNotAllowed() {
        addGrant(GrantStatus.ACTIVE, T0.plus(Duration.ofHours(1)), GrantScope.PROFILE);
        when(auditRepo.save(any(AuditLog.class))).thenThrow(new DataAccessResourceFailureException("down"));

        assertThatThrownBy(() -> read(GrantScope.PROFILE)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(e.getCode()).isEqualTo("AUDIT_UNAVAILABLE");
        });
    }

    @Test
    void aFailedAuditWriteOnARefusalStillGives403() {
        when(auditRepo.save(any(AuditLog.class))).thenThrow(new DataAccessResourceFailureException("down"));

        assertNoActiveGrant(() -> read(GrantScope.PROFILE));
    }

    @Test
    void writesAreLoggedAsWrites() {
        addGrant(GrantStatus.ACTIVE, T0.plus(Duration.ofHours(1)), GrantScope.PRESCRIPTIONS);

        guard.require(CARE_ID, GrantScope.PRESCRIPTIONS, AuditAction.WRITE, "PRESCRIPTION", "rx1");

        assertThat(audit.get(0).getAction()).isEqualTo(AuditAction.WRITE);
        assertThat(audit.get(0).getResourceId()).isEqualTo("rx1");
    }

    @Test
    void theAuditEntryNeverCarriesPatientData() {
        addGrant(GrantStatus.ACTIVE, T0.plus(Duration.ofHours(1)), GrantScope.PROFILE);
        read(GrantScope.PROFILE);

        assertThat(audit.get(0).toString()).doesNotContain("Test Patient").doesNotContain(patient.getPhone());
    }

    private ApiException catchApi(Supplier<?> call) {
        try {
            call.get();
        } catch (ApiException e) {
            return e;
        }
        throw new AssertionError("expected an ApiException");
    }
}
