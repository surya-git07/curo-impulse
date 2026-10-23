package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.ConsentRequest;
import com.curo.curo_backend.dto.LoginRequest;
import com.curo.curo_backend.dto.SignupRequest;
import com.curo.curo_backend.dto.SignupResponse;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.repository.PharmacyRepository;
import com.curo.curo_backend.repository.UserRepository;
import com.curo.curo_backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** RECEPTION signup: no licence checks, no approval step, can sign in straight away. */
class AuthServiceReceptionTest {

    private static final String PASSWORD = "correct-horse-battery";

    private AuthService service;
    private LicenceVerificationService verification;
    private final Map<String, AppUser> byPhone = new HashMap<>();
    private final Map<String, AppUser> byCareId = new HashMap<>();

    @BeforeEach
    void setUp() {
        UserRepository users = mock(UserRepository.class);
        when(users.existsByPhone(anyString())).thenAnswer(inv -> byPhone.containsKey(inv.<String>getArgument(0)));
        when(users.existsByCareId(anyString())).thenReturn(false);
        when(users.findByPhone(anyString())).thenAnswer(inv -> Optional.ofNullable(byPhone.get(inv.<String>getArgument(0))));
        when(users.findByCareId(anyString())).thenAnswer(inv -> Optional.ofNullable(byCareId.get(inv.<String>getArgument(0))));
        when(users.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            u.setId("665f00000000000000" + String.format("%06d", byPhone.size() + 1));
            byPhone.put(u.getPhone(), u);
            byCareId.put(u.getCareId(), u);
            return u;
        });

        JwtService jwt = new JwtService();
        ReflectionTestUtils.setField(jwt, "secretKey", UUID.randomUUID() + UUID.randomUUID().toString());
        ReflectionTestUtils.setField(jwt, "expirationMs", 3_600_000L);

        CareIdGenerator generator = mock(CareIdGenerator.class);
        when(generator.generate()).thenReturn("CID-RRRRR2");

        verification = mock(LicenceVerificationService.class);
        service = new AuthService();
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "passwordEncoder", new BCryptPasswordEncoder(4));
        ReflectionTestUtils.setField(service, "jwtService", jwt);
        ReflectionTestUtils.setField(service, "careIdGenerator", generator);
        ReflectionTestUtils.setField(service, "pharmacyRepository", mock(PharmacyRepository.class));
        ReflectionTestUtils.setField(service, "licenceVerificationService", verification);
    }

    private SignupRequest reception() {
        SignupRequest r = new SignupRequest();
        r.setRole("RECEPTION");
        r.setName("Test Receptionist");
        r.setPhone("9000000020");
        r.setPassword(PASSWORD);
        ConsentRequest c = new ConsentRequest();
        c.setAccepted(true);
        c.setPolicyVersion("2026-10-01");
        r.setConsent(c);
        return r;
    }

    @Test
    void signsUpWithoutAnyLicenceAndWithoutVerification() {
        SignupResponse res = service.signup(reception());
        assertThat(res.getRole()).isEqualTo("RECEPTION");
        assertThat(res.getCareId()).isEqualTo("CID-RRRRR2");
        assertThat(res.getVerificationStatus()).isNull();
        assertThat(res.getFacilityId()).isNull();
        verifyNoInteractions(verification);
    }

    @Test
    void canSignInStraightAfterSignupWithNoApproval() {
        service.signup(reception());
        LoginRequest login = new LoginRequest();
        login.setIdentifier("9000000020");
        login.setPassword(PASSWORD);
        var res = service.login(login);
        assertThat(res.getToken()).isNotBlank();
        assertThat(res.getUser().getRole()).isEqualTo("RECEPTION");
    }

    @Test
    void stillNeedsConsent() {
        SignupRequest r = reception();
        r.getConsent().setAccepted(false);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.signup(r))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("CONSENT_REQUIRED"));
    }

    @Test
    void adminIsStillNotASelfSignupRole() {
        SignupRequest r = reception();
        r.setRole("ADMIN");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.signup(r))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("INVALID_ROLE"));
    }
}
