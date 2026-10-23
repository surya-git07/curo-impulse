package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.AccessGrantView;
import com.curo.curo_backend.dto.AccessRequest;
import com.curo.curo_backend.dto.ApproveRequest;
import com.curo.curo_backend.dto.PagedResponse;
import com.curo.curo_backend.dto.ShareCodeRequest;
import com.curo.curo_backend.dto.ShareCodeResponse;
import com.curo.curo_backend.model.AccessGrant;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.AuditLog;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.GrantStatus;
import com.curo.curo_backend.model.ShareCode;
import com.curo.curo_backend.repository.AccessGrantRepository;
import com.curo.curo_backend.repository.AuditLogRepository;
import com.curo.curo_backend.repository.ShareCodeRepository;
import com.curo.curo_backend.repository.UserRepository;
import com.curo.curo_backend.support.MutableClock;
import org.bson.Document;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccessGrantServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-23T10:00:00Z");

    private final MutableClock clock = new MutableClock(T0);
    private final Map<String, AppUser> users = new HashMap<>();
    private final Map<String, AccessGrant> grants = new HashMap<>();
    private final Map<String, ShareCode> codes = new HashMap<>();
    private final List<AuditLog> audit = new ArrayList<>();
    private int nextId = 1;

    private final AppUser patient = user("p1", "PATIENT", "Test Patient", "CID-7K3MQ9");
    private final AppUser doctor = user("d1", "DOCTOR", "Dr Test", "CID-4HN8XD");
    private final AppUser pharmacy = user("ph1", "PHARMACY", "Test Pharmacy", "CID-9PW2TC");

    private MongoTemplate template;
    private ShareCodeRepository codeRepo;
    private AccessGrantService service;

    @BeforeEach
    void setUp() {
        for (AppUser u : List.of(patient, doctor, pharmacy)) {
            users.put(u.getId(), u);
        }
        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(users.get(inv.<String>getArgument(0))));
        when(userRepo.findByCareId(anyString())).thenAnswer(inv -> users.values().stream()
                .filter(u -> inv.getArgument(0).equals(u.getCareId())).findFirst());

        AccessGrantRepository grantRepo = mock(AccessGrantRepository.class);
        when(grantRepo.save(any(AccessGrant.class))).thenAnswer(inv -> {
            AccessGrant g = inv.getArgument(0);
            if (g.getId() == null) {
                g.setId("g" + nextId++);
            }
            grants.put(g.getId(), g);
            return g;
        });
        when(grantRepo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(grants.get(inv.<String>getArgument(0))));
        when(grantRepo.findByPatientIdAndGranteeIdAndStatusIn(anyString(), anyString(), anyCollection())).thenAnswer(inv ->
                grants.values().stream().filter(g -> g.getPatientId().equals(inv.getArgument(0))
                        && g.getGranteeId().equals(inv.getArgument(1))
                        && ((java.util.Collection<?>) inv.getArgument(2)).contains(g.getStatus())).toList());

        codeRepo = mock(ShareCodeRepository.class);
        when(codeRepo.save(any(ShareCode.class))).thenAnswer(inv -> {
            ShareCode c = inv.getArgument(0);
            codes.put(c.getCode(), c);
            return c;
        });
        when(codeRepo.findByCode(anyString())).thenAnswer(inv -> Optional.ofNullable(codes.get(inv.<String>getArgument(0))));

        template = mock(MongoTemplate.class);
        when(template.findAndRemove(any(Query.class), eq(ShareCode.class))).thenAnswer(inv ->
                codes.remove(((Query) inv.getArgument(0)).getQueryObject().getString("code")));

        AuditLogRepository auditRepo = mock(AuditLogRepository.class);
        when(auditRepo.save(any(AuditLog.class))).thenAnswer(inv -> {
            audit.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        AuditService auditService = new AuditService();
        ReflectionTestUtils.setField(auditService, "auditLogRepository", auditRepo);
        ReflectionTestUtils.setField(auditService, "mongoTemplate", template);
        ReflectionTestUtils.setField(auditService, "clock", clock);

        CurrentUserService currentUser = new CurrentUserService();
        ReflectionTestUtils.setField(currentUser, "userRepository", userRepo);

        ShareCodeService shareCodeService = new ShareCodeService();
        ReflectionTestUtils.setField(shareCodeService, "validMinutes", 15);
        ReflectionTestUtils.setField(shareCodeService, "shareCodeRepository", codeRepo);
        ReflectionTestUtils.setField(shareCodeService, "mongoTemplate", template);
        ReflectionTestUtils.setField(shareCodeService, "clock", clock);

        ShareCodeAttemptLimiter limiter = new ShareCodeAttemptLimiter();
        ReflectionTestUtils.setField(limiter, "maxFailures", 5);
        ReflectionTestUtils.setField(limiter, "windowMinutes", 15);
        ReflectionTestUtils.setField(limiter, "clock", clock);

        service = new AccessGrantService();
        ReflectionTestUtils.setField(service, "defaultHours", 24);
        ReflectionTestUtils.setField(service, "maxHours", 720);
        ReflectionTestUtils.setField(service, "grantRepository", grantRepo);
        ReflectionTestUtils.setField(service, "userRepository", userRepo);
        ReflectionTestUtils.setField(service, "currentUser", currentUser);
        ReflectionTestUtils.setField(service, "shareCodeService", shareCodeService);
        ReflectionTestUtils.setField(service, "attemptLimiter", limiter);
        ReflectionTestUtils.setField(service, "auditService", auditService);
        ReflectionTestUtils.setField(service, "mongoTemplate", template);
        ReflectionTestUtils.setField(service, "clock", clock);

        signIn(doctor);
    }

    @AfterEach
    void tearDown() {
        signOut();
    }

    private void assertApiError(Supplier<?> call, HttpStatus status, String code) {
        assertThatThrownBy(call::get).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    private AccessRequest byCareId(String careId, String... scope) {
        AccessRequest r = new AccessRequest();
        r.setCareId(careId);
        r.setScope(List.of(scope));
        r.setReason("Follow-up visit");
        r.setRequestedDurationHours(48);
        return r;
    }

    private AccessRequest byCode(String code) {
        AccessRequest r = new AccessRequest();
        r.setShareCode(code);
        return r;
    }

    private String patientMakesShareCode(int hours, String... scope) {
        signIn(patient);
        ShareCodeRequest req = new ShareCodeRequest();
        req.setScope(List.of(scope));
        req.setDurationHours(hours);
        String code = service.createShareCode(req).getShareCode();
        signIn(doctor);
        return code;
    }

    // ---- share codes

    @Test
    void patientGetsASixDigitCodeValidForFifteenMinutes() {
        signIn(patient);
        ShareCodeRequest req = new ShareCodeRequest();
        req.setScope(List.of("PROFILE", "PRESCRIPTIONS"));

        ShareCodeResponse response = service.createShareCode(req);

        assertThat(response.getShareCode()).matches("^[0-9]{6}$");
        assertThat(response.getExpiresAt()).isEqualTo(T0.plus(Duration.ofMinutes(15)));
        ShareCode stored = codes.get(response.getShareCode());
        assertThat(stored.getPatientId()).isEqualTo("p1");
        assertThat(stored.getScope()).containsExactly(GrantScope.PROFILE, GrantScope.PRESCRIPTIONS);
        assertThat(stored.getDurationHours()).isEqualTo(24);
    }

    @Test
    void onlyAPatientCanMakeAShareCode() {
        ShareCodeRequest req = new ShareCodeRequest();
        req.setScope(List.of("PROFILE"));
        assertApiError(() -> service.createShareCode(req), HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
    }

    @Test
    void shareCodeRejectsBadScopeAndDuration() {
        signIn(patient);
        ShareCodeRequest none = new ShareCodeRequest();
        assertApiError(() -> service.createShareCode(none), HttpStatus.BAD_REQUEST, "INVALID_SCOPE");

        ShareCodeRequest unknown = new ShareCodeRequest();
        unknown.setScope(List.of("EVERYTHING"));
        assertApiError(() -> service.createShareCode(unknown), HttpStatus.BAD_REQUEST, "INVALID_SCOPE");

        ShareCodeRequest tooLong = new ShareCodeRequest();
        tooLong.setScope(List.of("PROFILE"));
        tooLong.setDurationHours(721);
        assertApiError(() -> service.createShareCode(tooLong), HttpStatus.BAD_REQUEST, "INVALID_DURATION");
        tooLong.setDurationHours(0);
        assertApiError(() -> service.createShareCode(tooLong), HttpStatus.BAD_REQUEST, "INVALID_DURATION");
    }

    @Test
    void redeemingACodeGivesAnActiveGrantWithTheCodesScopeAndDuration() {
        String code = patientMakesShareCode(6, "PROFILE", "MEDICATIONS");

        AccessGrantView view = service.requestAccess(byCode(code));

        assertThat(view.getStatus()).isEqualTo(GrantStatus.ACTIVE);
        assertThat(view.getScope()).containsExactly(GrantScope.PROFILE, GrantScope.MEDICATIONS);
        assertThat(view.getCareId()).isEqualTo("CID-7K3MQ9");
        assertThat(view.getType()).isEqualTo("CLINICAL");
        assertThat(view.getApprovedAt()).isEqualTo(T0);
        assertThat(view.getExpiresAt()).isEqualTo(T0.plus(Duration.ofHours(6)));
        assertThat(view.getRequester().getUserId()).isEqualTo("d1");
        assertThat(grants.get(view.getId()).getPatientId()).isEqualTo("p1");
        assertThat(audit).extracting(AuditLog::getAction).containsExactly(AuditAction.GRANT_REQUESTED, AuditAction.GRANT_APPROVED);
    }

    @Test
    void aCodeWorksOnlyOnce() {
        String code = patientMakesShareCode(6, "PROFILE");
        service.requestAccess(byCode(code));

        signIn(pharmacy);
        assertApiError(() -> service.requestAccess(byCode(code)), HttpStatus.NOT_FOUND, "INVALID_SHARE_CODE");
    }

    @Test
    void codeScopeWinsOverAnythingTheRequesterSends() {
        String code = patientMakesShareCode(6, "PROFILE");
        AccessRequest req = byCode(code);
        req.setScope(List.of("PRESCRIPTIONS", "DOSES"));
        req.setRequestedDurationHours(700);

        AccessGrantView view = service.requestAccess(req);

        assertThat(view.getScope()).containsExactly(GrantScope.PROFILE);
        assertThat(view.getExpiresAt()).isEqualTo(T0.plus(Duration.ofHours(6)));
    }

    @Test
    void anExpiredCodeIs410AndIsGone() {
        String code = patientMakesShareCode(6, "PROFILE");
        clock.advance(Duration.ofMinutes(15));

        assertApiError(() -> service.requestAccess(byCode(code)), HttpStatus.GONE, "SHARE_CODE_EXPIRED");
        assertThat(codes).doesNotContainKey(code);
    }

    @Test
    void unknownAndMalformedCodesAre404() {
        assertApiError(() -> service.requestAccess(byCode("123456")), HttpStatus.NOT_FOUND, "INVALID_SHARE_CODE");
        assertApiError(() -> service.requestAccess(byCode("12345")), HttpStatus.NOT_FOUND, "INVALID_SHARE_CODE");
        assertApiError(() -> service.requestAccess(byCode("abcdef")), HttpStatus.NOT_FOUND, "INVALID_SHARE_CODE");
    }

    @Test
    void guessingIsStoppedAfterFiveWrongCodes() {
        for (int i = 0; i < 5; i++) {
            String guess = String.format("%06d", i);
            assertApiError(() -> service.requestAccess(byCode(guess)), HttpStatus.NOT_FOUND, "INVALID_SHARE_CODE");
        }
        String real = patientMakesShareCode(6, "PROFILE");

        assertApiError(() -> service.requestAccess(byCode(real)), HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS");
        assertThat(codes).as("the real code was not touched").containsKey(real);

        // the lock outlasts the code (both 15 minutes), so a new code is needed once it lifts
        clock.advance(Duration.ofMinutes(16));
        String fresh = patientMakesShareCode(6, "PROFILE");
        assertThat(service.requestAccess(byCode(fresh)).getStatus()).isEqualTo(GrantStatus.ACTIVE);
    }

    @Test
    void wrongCodesFromOneUserDoNotLockAnother() {
        for (int i = 0; i < 5; i++) {
            String guess = String.format("%06d", i);
            assertApiError(() -> service.requestAccess(byCode(guess)), HttpStatus.NOT_FOUND, "INVALID_SHARE_CODE");
        }
        String real = patientMakesShareCode(6, "PROFILE");
        signIn(pharmacy);

        assertThat(service.requestAccess(byCode(real)).getStatus()).isEqualTo(GrantStatus.ACTIVE);
    }

    @Test
    void aCodeIsNotUsedUpWhenTheRequesterAlreadyHasAGrant() {
        grants.put("g0", grant("g0", patient, doctor, GrantStatus.ACTIVE, T0.plus(Duration.ofHours(1)), GrantScope.PROFILE));
        String code = patientMakesShareCode(6, "PROFILE");

        assertThatThrownBy(() -> service.requestAccess(byCode(code))).isInstanceOfSatisfying(GrantExistsException.class,
                e -> assertThat(e.getGrantId()).isEqualTo("g0"));
        assertThat(codes).containsKey(code);
    }

    // ---- request by CareID

    @Test
    void doctorRequestByCareIdCreatesAPendingGrantAndLogsIt() {
        AccessGrantView view = service.requestAccess(byCareId("cid-7k3mq9", "PROFILE", "PRESCRIPTIONS"));

        assertThat(view.getStatus()).isEqualTo(GrantStatus.PENDING);
        assertThat(view.getCareId()).isEqualTo("CID-7K3MQ9");
        assertThat(view.getScope()).containsExactly(GrantScope.PROFILE, GrantScope.PRESCRIPTIONS);
        assertThat(view.getReason()).isEqualTo("Follow-up visit");
        assertThat(view.getRequestedAt()).isEqualTo(T0);
        assertThat(view.getExpiresAt()).isNull();
        AccessGrant stored = grants.get(view.getId());
        assertThat(stored.getPatientId()).isEqualTo("p1");
        assertThat(stored.getGranteeId()).isEqualTo("d1");
        assertThat(stored.getRole()).isEqualTo("DOCTOR");
        assertThat(stored.getRequestedDurationHours()).isEqualTo(48);
        assertThat(audit).extracting(AuditLog::getAction).containsExactly(AuditAction.GRANT_REQUESTED);
    }

    @Test
    void pharmacyRequestIsTypedPharmacy() {
        signIn(pharmacy);
        assertThat(service.requestAccess(byCareId("CID-7K3MQ9", "PRESCRIPTIONS")).getType()).isEqualTo("PHARMACY");
    }

    @Test
    void anUnknownCareIdGetsTheSameAnswerButNothingIsStored() {
        AccessGrantView view = service.requestAccess(byCareId("CID-ZZZZZZ", "PROFILE"));

        assertThat(view.getStatus()).isEqualTo(GrantStatus.PENDING);
        assertThat(view.getId()).isNotBlank();
        assertThat(grants).isEmpty();
        assertThat(audit).isEmpty();
    }

    @Test
    void aSecondRequestWhilePendingOrActiveIs409WithTheExistingId() {
        AccessGrantView first = service.requestAccess(byCareId("CID-7K3MQ9", "PROFILE"));

        assertThatThrownBy(() -> service.requestAccess(byCareId("CID-7K3MQ9", "PROFILE")))
                .isInstanceOfSatisfying(GrantExistsException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getCode()).isEqualTo("GRANT_EXISTS");
                    assertThat(e.getGrantId()).isEqualTo(first.getId());
                });
    }

    @Test
    void aRevokedOrLapsedGrantDoesNotBlockANewRequest() {
        grants.put("old1", grant("old1", patient, doctor, GrantStatus.REVOKED, null, GrantScope.PROFILE));
        grants.put("old2", grant("old2", patient, doctor, GrantStatus.ACTIVE, T0.minusSeconds(1), GrantScope.PROFILE));

        assertThat(service.requestAccess(byCareId("CID-7K3MQ9", "PROFILE")).getStatus()).isEqualTo(GrantStatus.PENDING);
    }

    @Test
    void requestNeedsADoctorOrPharmacyAndExactlyOneOfCareIdOrCode() {
        signIn(patient);
        assertApiError(() -> service.requestAccess(byCareId("CID-4HN8XD", "PROFILE")), HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
        AppUser admin = user("a1", "ADMIN", "Admin", "CID-ADMINA");
        users.put("a1", admin);
        signIn(admin);
        assertApiError(() -> service.requestAccess(byCareId("CID-7K3MQ9", "PROFILE")), HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");

        signIn(doctor);
        assertApiError(() -> service.requestAccess(new AccessRequest()), HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        AccessRequest both = byCareId("CID-7K3MQ9", "PROFILE");
        both.setShareCode("123456");
        assertApiError(() -> service.requestAccess(both), HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }

    @Test
    void requestByCareIdNeedsAValidScopeAndDuration() {
        assertApiError(() -> service.requestAccess(byCareId("CID-7K3MQ9")), HttpStatus.BAD_REQUEST, "INVALID_SCOPE");
        assertApiError(() -> service.requestAccess(byCareId("CID-7K3MQ9", "NOPE")), HttpStatus.BAD_REQUEST, "INVALID_SCOPE");
        AccessRequest tooLong = byCareId("CID-7K3MQ9", "PROFILE");
        tooLong.setRequestedDurationHours(1000);
        assertApiError(() -> service.requestAccess(tooLong), HttpStatus.BAD_REQUEST, "INVALID_DURATION");
    }

    // ---- approve

    private String pendingGrantId(String... scope) {
        return service.requestAccess(byCareId("CID-7K3MQ9", scope)).getId();
    }

    @Test
    void patientApprovesAndTheGrantBecomesActiveForTheRequestedHours() {
        String id = pendingGrantId("PROFILE", "PRESCRIPTIONS");
        signIn(patient);

        AccessGrantView view = service.approve(id, null);

        assertThat(view.getStatus()).isEqualTo(GrantStatus.ACTIVE);
        assertThat(view.getApprovedAt()).isEqualTo(T0);
        assertThat(view.getExpiresAt()).isEqualTo(T0.plus(Duration.ofHours(48)));
        assertThat(view.getScope()).containsExactly(GrantScope.PROFILE, GrantScope.PRESCRIPTIONS);
        assertThat(audit.get(audit.size() - 1).getAction()).isEqualTo(AuditAction.GRANT_APPROVED);
        assertThat(audit.get(audit.size() - 1).getActorUserId()).isEqualTo("p1");
    }

    @Test
    void approvalCanNarrowTheScopeAndSetTheDuration() {
        String id = pendingGrantId("PROFILE", "PRESCRIPTIONS");
        signIn(patient);
        ApproveRequest body = new ApproveRequest();
        body.setScope(List.of("PROFILE"));
        body.setDurationHours(2);

        AccessGrantView view = service.approve(id, body);

        assertThat(view.getScope()).containsExactly(GrantScope.PROFILE);
        assertThat(view.getExpiresAt()).isEqualTo(T0.plus(Duration.ofHours(2)));
    }

    @Test
    void approvalCannotWidenTheScopeOrExceedTheMaximum() {
        String id = pendingGrantId("PROFILE");
        signIn(patient);
        ApproveRequest wider = new ApproveRequest();
        wider.setScope(List.of("PROFILE", "DOSES"));
        assertApiError(() -> service.approve(id, wider), HttpStatus.BAD_REQUEST, "SCOPE_NOT_SUBSET");

        ApproveRequest tooLong = new ApproveRequest();
        tooLong.setDurationHours(721);
        assertApiError(() -> service.approve(id, tooLong), HttpStatus.BAD_REQUEST, "INVALID_DURATION");
        assertThat(grants.get(id).getStatus()).isEqualTo(GrantStatus.PENDING);
    }

    @Test
    void onlyTheOwningPatientCanApprove() {
        String id = pendingGrantId("PROFILE");
        AppUser otherPatient = user("p2", "PATIENT", "Other Patient", "CID-OTHERP");
        users.put("p2", otherPatient);

        signIn(otherPatient);
        assertApiError(() -> service.approve(id, null), HttpStatus.FORBIDDEN, "FORBIDDEN");
        signIn(doctor);
        assertApiError(() -> service.approve(id, null), HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
        assertThat(grants.get(id).getStatus()).as("a doctor cannot approve their own request").isEqualTo(GrantStatus.PENDING);
    }

    @Test
    void approveNeedsAPendingGrantThatExists() {
        String id = pendingGrantId("PROFILE");
        signIn(patient);
        service.approve(id, null);

        assertApiError(() -> service.approve(id, null), HttpStatus.CONFLICT, "NOT_PENDING");
        assertApiError(() -> service.approve("nope", null), HttpStatus.NOT_FOUND, "GRANT_NOT_FOUND");
    }

    // ---- revoke

    @Test
    void patientRevokesAnActiveGrantAndItStopsBeingUsable() {
        String id = pendingGrantId("PROFILE");
        signIn(patient);
        service.approve(id, null);

        AccessGrantView view = service.revoke(id);

        assertThat(view.getStatus()).isEqualTo(GrantStatus.REVOKED);
        assertThat(view.getRevokedAt()).isEqualTo(T0);
        assertThat(grants.get(id).isUsableAt(T0)).isFalse();
        assertThat(audit.get(audit.size() - 1).getAction()).isEqualTo(AuditAction.GRANT_REVOKED);
    }

    @Test
    void revokingAPendingRequestIsTheDenyAction() {
        String id = pendingGrantId("PROFILE");
        signIn(patient);

        assertThat(service.revoke(id).getStatus()).isEqualTo(GrantStatus.REVOKED);
    }

    @Test
    void theGranteeCanGiveUpTheirOwnGrant() {
        String id = pendingGrantId("PROFILE");

        assertThat(service.revoke(id).getStatus()).isEqualTo(GrantStatus.REVOKED);
    }

    @Test
    void strangersCannotRevoke() {
        String id = pendingGrantId("PROFILE");
        signIn(pharmacy);
        assertApiError(() -> service.revoke(id), HttpStatus.FORBIDDEN, "FORBIDDEN");
        assertThat(grants.get(id).getStatus()).isEqualTo(GrantStatus.PENDING);
    }

    @Test
    void revokingTwiceChangesNothingTheSecondTime() {
        String id = pendingGrantId("PROFILE");
        service.revoke(id);
        int entries = audit.size();
        Instant firstRevokedAt = grants.get(id).getRevokedAt();
        clock.advance(Duration.ofMinutes(5));

        AccessGrantView again = service.revoke(id);

        assertThat(again.getStatus()).isEqualTo(GrantStatus.REVOKED);
        assertThat(again.getRevokedAt()).isEqualTo(firstRevokedAt);
        assertThat(audit).hasSize(entries);
    }

    @Test
    void anExpiredGrantShowsAsExpiredNotRevoked() {
        String id = pendingGrantId("PROFILE");
        signIn(patient);
        service.approve(id, null);
        clock.advance(Duration.ofHours(49));

        AccessGrantView view = service.revoke(id);

        assertThat(view.getStatus()).isEqualTo(GrantStatus.EXPIRED);
        assertThat(view.getRevokedAt()).isNull();
    }

    // ---- list

    private Query listQuery(String status, String careId) {
        signIn(patient);
        when(template.count(any(Query.class), eq(AccessGrant.class))).thenReturn(0L);
        when(template.find(any(Query.class), eq(AccessGrant.class))).thenReturn(List.of());
        service.list(status, careId, 0, 20);
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(template).find(captor.capture(), eq(AccessGrant.class));
        return captor.getValue();
    }

    @Test
    void patientListsGrantsOnTheirOwnData() {
        Document q = listQuery(null, null).getQueryObject();

        assertThat(q.toString()).contains("patientId").contains("p1").doesNotContain("granteeId");
    }

    @Test
    void doctorListsOnlyGrantsTheyHold() {
        signIn(doctor);
        when(template.count(any(Query.class), eq(AccessGrant.class))).thenReturn(0L);
        when(template.find(any(Query.class), eq(AccessGrant.class))).thenReturn(List.of());

        service.list(null, null, 0, 20);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(template).find(captor.capture(), eq(AccessGrant.class));
        assertThat(captor.getValue().getQueryObject().toString()).contains("granteeId").contains("d1").doesNotContain("patientId");
    }

    @Test
    void listFiltersByCareIdAndStatusAndPagesNewestFirst() {
        Query q = listQuery("active", "cid-7k3mq9");
        String json = q.getQueryObject().toString();

        assertThat(json).contains("CID-7K3MQ9").contains("ACTIVE").contains("expiresAt");
        assertThat(q.getLimit()).isEqualTo(20);
        assertThat(q.getSkip()).isZero();
        assertThat(q.getSortObject().toJson()).contains("createdAt").contains("-1");
    }

    @Test
    void expiredFilterAlsoCatchesActiveGrantsPastTheirTime() {
        String json = listQuery("EXPIRED", null).getQueryObject().toString();

        assertThat(json).contains("EXPIRED").contains("ACTIVE").contains("$lte");
    }

    @Test
    void listShowsTheEffectiveStatus() {
        signIn(patient);
        AccessGrant lapsed = grant("g9", patient, doctor, GrantStatus.ACTIVE, T0.minusSeconds(1), GrantScope.PROFILE);
        when(template.count(any(Query.class), eq(AccessGrant.class))).thenReturn(1L);
        when(template.find(any(Query.class), eq(AccessGrant.class))).thenReturn(List.of(lapsed));

        PagedResponse<AccessGrantView> page = service.list(null, null, 0, 20);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getItems()).singleElement().satisfies(v -> assertThat(v.getStatus()).isEqualTo(GrantStatus.EXPIRED));
    }

    @Test
    void listRejectsBadStatusAndPaging() {
        signIn(patient);
        assertApiError(() -> service.list("SOMETIMES", null, 0, 20), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertApiError(() -> service.list(null, null, -1, 20), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertApiError(() -> service.list(null, null, 0, 101), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertApiError(() -> service.list(null, null, 0, 0), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }

    @Test
    void adminsHaveNoGrantsToList() {
        AppUser admin = user("a1", "ADMIN", "Admin", "CID-ADMINA");
        users.put("a1", admin);
        signIn(admin);
        assertApiError(() -> service.list(null, null, 0, 20), HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
        verify(template, never()).find(any(Query.class), eq(AccessGrant.class));
    }
}
