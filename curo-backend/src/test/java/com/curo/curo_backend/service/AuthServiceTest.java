package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.ConsentRequest;
import com.curo.curo_backend.dto.LoginRequest;
import com.curo.curo_backend.dto.LoginResponse;
import com.curo.curo_backend.dto.SignupRequest;
import com.curo.curo_backend.dto.SignupResponse;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.repository.UserRepository;
import com.curo.curo_backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UserRepository repo;
    private CareIdGenerator generator;
    private JwtService jwtService;
    private AuthService service;
    private final Map<String, AppUser> byPhone = new HashMap<>();

    @BeforeEach
    void setUp() {
        repo = mock(UserRepository.class);
        generator = mock(CareIdGenerator.class);
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secretKey", UUID.randomUUID() + UUID.randomUUID().toString());
        ReflectionTestUtils.setField(jwtService, "expirationMs", 3_600_000L);

        service = new AuthService();
        ReflectionTestUtils.setField(service, "userRepository", repo);
        ReflectionTestUtils.setField(service, "passwordEncoder", new BCryptPasswordEncoder(4));
        ReflectionTestUtils.setField(service, "jwtService", jwtService);
        ReflectionTestUtils.setField(service, "careIdGenerator", generator);

        when(generator.generate()).thenReturn("CID-7K3MQ9");
        when(repo.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            u.setId("665f0000000000000000a001");
            byPhone.put(u.getPhone(), u);
            return u;
        });
    }

    private SignupRequest signupRequest() {
        SignupRequest r = new SignupRequest();
        r.setRole("PATIENT");
        r.setName("Test Patient");
        r.setPhone("9000000001");
        r.setPassword("correct-horse-battery");
        ConsentRequest c = new ConsentRequest();
        c.setAccepted(true);
        c.setPolicyVersion("2026-10-01");
        r.setConsent(c);
        return r;
    }

    private void assertApiError(Supplier<?> call, HttpStatus status, String code) {
        assertThatThrownBy(call::get)
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(status);
                    assertThat(e.getCode()).isEqualTo(code);
                });
    }

    // ---- signup

    @Test
    void signupCreatesUserWithCareIdHashedPasswordAndServerSideConsentTimestamp() {
        Instant before = Instant.now();

        SignupResponse response = service.signup(signupRequest());

        assertThat(response.getCareId()).isEqualTo("CID-7K3MQ9");
        assertThat(response.getId()).isEqualTo("665f0000000000000000a001");
        assertThat(response.getRole()).isEqualTo("PATIENT");
        assertThat(response.getName()).isEqualTo("Test Patient");
        assertThat(response.getConsent().isAccepted()).isTrue();
        assertThat(response.getConsent().getPolicyVersion()).isEqualTo("2026-10-01");
        assertThat(response.getConsent().getAcceptedAt()).isBetween(before, Instant.now().plus(Duration.ofSeconds(1)));

        AppUser stored = byPhone.get("9000000001");
        assertThat(stored.getPasswordHash()).startsWith("$2").isNotEqualTo("correct-horse-battery");
    }

    @Test
    void signupWithoutConsentIsRejected() {
        SignupRequest noConsent = signupRequest();
        noConsent.setConsent(null);
        assertApiError(() -> service.signup(noConsent), HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED");

        SignupRequest declined = signupRequest();
        declined.getConsent().setAccepted(false);
        assertApiError(() -> service.signup(declined), HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED");

        SignupRequest nullAccepted = signupRequest();
        nullAccepted.getConsent().setAccepted(null);
        assertApiError(() -> service.signup(nullAccepted), HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED");

        SignupRequest noVersion = signupRequest();
        noVersion.getConsent().setPolicyVersion(" ");
        assertApiError(() -> service.signup(noVersion), HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED");

        verify(repo, never()).save(any());
    }

    @Test
    void signupRejectsShortAndOverlongPasswords() {
        SignupRequest shortPw = signupRequest();
        shortPw.setPassword("1234567");
        assertApiError(() -> service.signup(shortPw), HttpStatus.BAD_REQUEST, "WEAK_PASSWORD");

        SignupRequest longPw = signupRequest();
        longPw.setPassword("x".repeat(73));
        assertApiError(() -> service.signup(longPw), HttpStatus.BAD_REQUEST, "WEAK_PASSWORD");
    }

    @Test
    void signupRejectsTakenPhone() {
        when(repo.existsByPhone("9000000001")).thenReturn(true);
        assertApiError(() -> service.signup(signupRequest()), HttpStatus.CONFLICT, "PHONE_TAKEN");
        verify(repo, never()).save(any());
    }

    @Test
    void signupOnlyAllowsPatientAndCaregiverForNow() {
        SignupRequest caregiver = signupRequest();
        caregiver.setRole("caregiver");
        assertThat(service.signup(caregiver).getRole()).isEqualTo("CAREGIVER");

        SignupRequest doctor = signupRequest();
        doctor.setRole("DOCTOR");
        assertApiError(() -> service.signup(doctor), HttpStatus.NOT_IMPLEMENTED, "ROLE_NOT_SUPPORTED_YET");

        SignupRequest admin = signupRequest();
        admin.setRole("SUPER_ADMIN");
        assertApiError(() -> service.signup(admin), HttpStatus.BAD_REQUEST, "INVALID_ROLE");
    }

    @Test
    void signupRetriesWhenCareIdAlreadyExists() {
        when(generator.generate()).thenReturn("CID-AAAAAA", "CID-BBBBBB");
        when(repo.existsByCareId("CID-AAAAAA")).thenReturn(true);

        assertThat(service.signup(signupRequest()).getCareId()).isEqualTo("CID-BBBBBB");
    }

    @Test
    void signupRetriesOnDuplicateKeyFromAConcurrentCareIdCollision() {
        when(generator.generate()).thenReturn("CID-AAAAAA", "CID-BBBBBB");
        when(repo.save(any(AppUser.class)))
                .thenThrow(new DuplicateKeyException("careId"))
                .thenAnswer(inv -> {
                    AppUser u = inv.getArgument(0);
                    u.setId("x");
                    return u;
                });

        assertThat(service.signup(signupRequest()).getCareId()).isEqualTo("CID-BBBBBB");
    }

    @Test
    void signupReportsPhoneTakenWhenDuplicateKeyIsThePhone() {
        when(repo.save(any(AppUser.class))).thenThrow(new DuplicateKeyException("phone"));
        when(repo.existsByPhone("9000000001")).thenReturn(false, true);

        assertApiError(() -> service.signup(signupRequest()), HttpStatus.CONFLICT, "PHONE_TAKEN");
    }

    @Test
    void signupGivesUpAfterTenCareIdCollisions() {
        when(repo.existsByCareId(anyString())).thenReturn(true);
        assertApiError(() -> service.signup(signupRequest()), HttpStatus.INTERNAL_SERVER_ERROR, "CAREID_UNAVAILABLE");
    }

    // ---- login

    private void signUpAndStub() {
        service.signup(signupRequest());
        AppUser stored = byPhone.get("9000000001");
        when(repo.findByCareId("CID-7K3MQ9")).thenReturn(Optional.of(stored));
        when(repo.findByPhone("9000000001")).thenReturn(Optional.of(stored));
    }

    private LoginRequest login(String identifier, String password) {
        LoginRequest r = new LoginRequest();
        r.setIdentifier(identifier);
        r.setPassword(password);
        return r;
    }

    @Test
    void loginByCareIdReturnsTokenWithContractClaimsAndSummary() {
        signUpAndStub();

        LoginResponse response = service.login(login("CID-7K3MQ9", "correct-horse-battery"));

        assertThat(jwtService.extractUsername(response.getToken())).isEqualTo("665f0000000000000000a001");
        assertThat(jwtService.extractRole(response.getToken())).isEqualTo("PATIENT");
        assertThat(jwtService.<String>extractClaim(response.getToken(), c -> c.get("careId", String.class))).isEqualTo("CID-7K3MQ9");
        assertThat(response.getExpiresAt()).isAfter(Instant.now().plus(Duration.ofMinutes(55)));
        assertThat(response.getUser().getCareId()).isEqualTo("CID-7K3MQ9");
        assertThat(response.getUser().getName()).isEqualTo("Test Patient");
        assertThat(response.getUser().isProfileCompleted()).isFalse();
    }

    @Test
    void loginByPhoneAndLowercaseCareIdWork() {
        signUpAndStub();

        assertThat(service.login(login("9000000001", "correct-horse-battery")).getToken()).isNotBlank();
        assertThat(service.login(login(" cid-7k3mq9 ", "correct-horse-battery")).getToken()).isNotBlank();
    }

    @Test
    void wrongPasswordUnknownUserAndDeletedUserAllLookTheSame() {
        signUpAndStub();
        byPhone.get("9000000001");

        assertApiError(() -> service.login(login("CID-7K3MQ9", "wrong-password")), HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        assertApiError(() -> service.login(login("CID-ZZZZZZ", "correct-horse-battery")), HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        assertApiError(() -> service.login(login("9111111111", "correct-horse-battery")), HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        assertApiError(() -> service.login(login("CID-7K3MQ9", "x".repeat(100))), HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");

        byPhone.get("9000000001").setDeletedAt(Instant.now());
        assertApiError(() -> service.login(login("CID-7K3MQ9", "correct-horse-battery")), HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
    }
}
