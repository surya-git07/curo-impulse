package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.ConsentRequest;
import com.curo.curo_backend.dto.SignupRequest;
import com.curo.curo_backend.dto.SignupResponse;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.VerificationStatus;
import com.curo.curo_backend.repository.LicenceRegistryRepository;
import com.curo.curo_backend.repository.PharmacyRepository;
import com.curo.curo_backend.repository.UserRepository;
import com.curo.curo_backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** HOSPITAL signup against the real mock-registry service and the real seed data (Kochi Lakeshore Hospital). */
class AuthServiceHospitalTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final String NUMBER = "KL-HOSP-31182";
    private static final String GSTIN = "32AAKCK5507R1Z2";
    private static final String NAME = "Kochi Lakeshore Hospital";

    private AuthService service;
    private final Map<String, AppUser> byPhone = new HashMap<>();
    private int careIdCounter = 0;

    @BeforeEach
    void setUp() {
        UserRepository users = mock(UserRepository.class);
        when(users.existsByPhone(anyString())).thenAnswer(inv -> byPhone.containsKey(inv.<String>getArgument(0)));
        when(users.existsByCareId(anyString())).thenReturn(false);
        when(users.existsByLicenceNumber(anyString())).thenAnswer(inv ->
                byPhone.values().stream().anyMatch(u -> inv.getArgument(0).equals(u.getLicenceNumber())));
        when(users.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            u.setId("665f00000000000000" + String.format("%06d", byPhone.size() + 1));
            byPhone.put(u.getPhone(), u);
            return u;
        });

        LicenceRegistryRepository registry = mock(LicenceRegistryRepository.class);
        when(registry.findByTypeAndLicenceNumber(anyString(), anyString())).thenAnswer(inv -> LicenceRegistrySeed.entries().stream()
                .filter(e -> e.getType().equals(inv.getArgument(0)) && e.getLicenceNumber().equals(inv.getArgument(1)))
                .findFirst());
        MockRegistryVerificationService verification = new MockRegistryVerificationService();
        ReflectionTestUtils.setField(verification, "registryRepository", registry);

        JwtService jwt = new JwtService();
        ReflectionTestUtils.setField(jwt, "secretKey", UUID.randomUUID() + UUID.randomUUID().toString());
        ReflectionTestUtils.setField(jwt, "expirationMs", 3_600_000L);

        CareIdGenerator generator = mock(CareIdGenerator.class);
        when(generator.generate()).thenAnswer(inv -> "CID-HHHH" + (char) ('A' + careIdCounter++));

        service = new AuthService();
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "passwordEncoder", new BCryptPasswordEncoder(4));
        ReflectionTestUtils.setField(service, "jwtService", jwt);
        ReflectionTestUtils.setField(service, "careIdGenerator", generator);
        ReflectionTestUtils.setField(service, "pharmacyRepository", mock(PharmacyRepository.class));
        ReflectionTestUtils.setField(service, "licenceVerificationService", verification);
    }

    private SignupRequest hospital(String number, String gstin, String state, String name, String phone) {
        SignupRequest r = new SignupRequest();
        r.setRole("HOSPITAL");
        r.setName(name);
        r.setPhone(phone);
        r.setPassword(PASSWORD);
        ConsentRequest c = new ConsentRequest();
        c.setAccepted(true);
        c.setPolicyVersion("2026-10-01");
        r.setConsent(c);
        r.setLicenceNumber(number);
        r.setGstin(gstin);
        r.setState(state);
        return r;
    }

    private SignupRequest valid() {
        return hospital(NUMBER, GSTIN, "KL", NAME, "9000000010");
    }

    private void assertRejected(SignupRequest r, HttpStatus status, String code) {
        assertThatThrownBy(() -> service.signup(r)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    @Test
    void aRegistryMatchIsVerifiedAndGetsAFacilityId() {
        SignupResponse res = service.signup(valid());
        assertThat(res.getRole()).isEqualTo("HOSPITAL");
        assertThat(res.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(res.getVerificationReasonCode()).isEqualTo("LICENCE_MATCHED");
        assertThat(res.getFacilityId()).isNotBlank();
        AppUser saved = byPhone.get("9000000010");
        assertThat(saved.getFacilityName()).isEqualTo(NAME);
        assertThat(saved.getLicenceNumber()).isEqualTo(NUMBER);
        assertThat(saved.getGstin()).isEqualTo(GSTIN);
        assertThat(saved.getState()).isEqualTo("KL");
    }

    @Test
    void aWellFormedNumberNotInTheRegistryIsPending() {
        // valid GSTIN for Tamil Nadu, but the number is not in the registry
        SignupResponse res = service.signup(hospital("TN-HOSP-99001", "33AAGCS6120M1ZB", "TN", "Unlisted Test Hospital", "9000000011"));
        assertThat(res.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(res.getVerificationReasonCode()).isEqualTo("LICENCE_NOT_IN_REGISTRY");
        assertThat(res.getFacilityId()).isNotBlank();
    }

    @Test
    void missingFieldsAreRefused() {
        SignupRequest noGstin = valid();
        noGstin.setGstin(null);
        assertRejected(noGstin, HttpStatus.BAD_REQUEST, "LICENCE_FIELDS_REQUIRED");
        SignupRequest noState = valid();
        noState.setState(" ");
        assertRejected(noState, HttpStatus.BAD_REQUEST, "LICENCE_FIELDS_REQUIRED");
        SignupRequest noNumber = valid();
        noNumber.setLicenceNumber(null);
        assertRejected(noNumber, HttpStatus.BAD_REQUEST, "LICENCE_FIELDS_REQUIRED");
    }

    @Test
    void badGstinStateAndNumberReuseTheExistingCodes() {
        assertRejected(hospital(NUMBER, "NOT-A-GSTIN", "KL", NAME, "9000000012"), HttpStatus.BAD_REQUEST, "INVALID_GSTIN");
        assertRejected(hospital(NUMBER, GSTIN, "TN", NAME, "9000000012"), HttpStatus.BAD_REQUEST, "GSTIN_STATE_MISMATCH");
        assertRejected(hospital(NUMBER, GSTIN, "ZZ", NAME, "9000000012"), HttpStatus.BAD_REQUEST, "INVALID_STATE");
        assertRejected(hospital("!", GSTIN, "KL", NAME, "9000000012"), HttpStatus.BAD_REQUEST, "INVALID_LICENCE_NUMBER");
    }

    @Test
    void aWrongNameOrAnotherGstinForARegisteredNumberIsRefused() {
        assertRejected(hospital(NUMBER, GSTIN, "KL", "Some Other Hospital", "9000000013"), HttpStatus.BAD_REQUEST, "LICENCE_NAME_MISMATCH");
        // a valid Kerala GSTIN, but not the one registered for this number
        assertRejected(hospital(NUMBER, "32AAIFP1876M1ZV", "KL", NAME, "9000000013"), HttpStatus.BAD_REQUEST, "LICENCE_GSTIN_MISMATCH");
    }

    @Test
    void theSameLicenceCannotBeRegisteredTwice() {
        service.signup(valid());
        assertRejected(hospital(NUMBER, GSTIN, "KL", NAME, "9000000014"), HttpStatus.CONFLICT, "LICENCE_TAKEN");
    }
}
