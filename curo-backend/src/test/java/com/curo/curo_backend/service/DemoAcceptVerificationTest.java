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
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
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

/** licence.verification.mode=demo-accept: format and GSTIN checks stay, the registry lookup and name match go. */
class DemoAcceptVerificationTest {

    private static final String PASSWORD = "correct-horse-battery";
    // formats and a checksum-valid GSTIN only; none of these is in the seeded registry
    private static final String DOCTOR_NUMBER = "ZZ-90001";
    private static final String HOSPITAL_NUMBER = "ZZ-HOSP-90002";
    private static final String PHARMACY_NUMBER = "ZZ/TST/20B-90003";
    private static final String GSTIN_TN = "33AAGCS6120M1ZB";

    private final DemoAcceptVerificationService demo = new DemoAcceptVerificationService();
    private AuthService service;
    private final Map<String, AppUser> byPhone = new HashMap<>();
    private LicenceRegistryRepository registry;

    @BeforeEach
    void setUp() {
        UserRepository users = mock(UserRepository.class);
        when(users.existsByPhone(anyString())).thenAnswer(inv -> byPhone.containsKey(inv.<String>getArgument(0)));
        when(users.existsByCareId(anyString())).thenReturn(false);
        when(users.existsByLicenceNumber(anyString())).thenAnswer(inv ->
                byPhone.values().stream().anyMatch(u -> inv.getArgument(0).equals(u.getLicenceNumber())));
        when(users.existsByDrugLicenceNumber(anyString())).thenAnswer(inv ->
                byPhone.values().stream().anyMatch(u -> inv.getArgument(0).equals(u.getDrugLicenceNumber())));
        when(users.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            u.setId("665f00000000000000" + String.format("%06d", byPhone.size() + 1));
            byPhone.put(u.getPhone(), u);
            return u;
        });
        registry = mock(LicenceRegistryRepository.class);

        JwtService jwt = new JwtService();
        ReflectionTestUtils.setField(jwt, "secretKey", UUID.randomUUID() + UUID.randomUUID().toString());
        ReflectionTestUtils.setField(jwt, "expirationMs", 3_600_000L);
        CareIdGenerator generator = mock(CareIdGenerator.class);
        int[] n = {0};
        when(generator.generate()).thenAnswer(inv -> "CID-DDDD" + (char) ('A' + n[0]++) + "2");

        service = new AuthService();
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "passwordEncoder", new BCryptPasswordEncoder(4));
        ReflectionTestUtils.setField(service, "jwtService", jwt);
        ReflectionTestUtils.setField(service, "careIdGenerator", generator);
        ReflectionTestUtils.setField(service, "pharmacyRepository", mock(PharmacyRepository.class));
        ReflectionTestUtils.setField(service, "licenceVerificationService", demo);
    }

    private SignupRequest base(String role, String name, String phone) {
        SignupRequest r = new SignupRequest();
        r.setRole(role);
        r.setName(name);
        r.setPhone(phone);
        r.setPassword(PASSWORD);
        ConsentRequest c = new ConsentRequest();
        c.setAccepted(true);
        c.setPolicyVersion("2026-10-01");
        r.setConsent(c);
        return r;
    }

    private SignupRequest hospital(String number, String gstin, String state, String phone) {
        SignupRequest r = base("HOSPITAL", "Any Name At All", phone);
        r.setLicenceNumber(number);
        r.setGstin(gstin);
        r.setState(state);
        return r;
    }

    private void assertRejected(SignupRequest r, HttpStatus status, String code) {
        assertThatThrownBy(() -> service.signup(r)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    @Test
    void aValidFormatIsAcceptedWithoutAnyRegistryEntry() {
        SignupRequest doctor = base("DOCTOR", "Unlisted Doctor", "9000000031");
        doctor.setLicenceNumber(DOCTOR_NUMBER);
        doctor.setStateCouncil("Some Council");
        SignupResponse d = service.signup(doctor);
        assertThat(d.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(d.getVerificationReasonCode()).isEqualTo("LICENCE_ACCEPTED_DEMO");

        SignupResponse h = service.signup(hospital(HOSPITAL_NUMBER, GSTIN_TN, "TN", "9000000032"));
        assertThat(h.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(h.getFacilityId()).isNotBlank();

        SignupRequest pharmacy = base("PHARMACY", "Unlisted Pharmacy", "9000000033");
        pharmacy.setDrugLicenceNumber(PHARMACY_NUMBER);
        pharmacy.setGstin(GSTIN_TN);
        pharmacy.setState("TN");
        assertThat(service.signup(pharmacy).getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);

        org.mockito.Mockito.verifyNoInteractions(registry);
    }

    @Test
    void aBadFormatIsStillRejected() {
        assertRejected(hospital("!", GSTIN_TN, "TN", "9000000034"), HttpStatus.BAD_REQUEST, "INVALID_LICENCE_NUMBER");
        SignupRequest doctor = base("DOCTOR", "X Doctor", "9000000035");
        doctor.setLicenceNumber("a");
        doctor.setStateCouncil("Some Council");
        assertRejected(doctor, HttpStatus.BAD_REQUEST, "INVALID_LICENCE_NUMBER");
        assertThat(demo.verifyDoctor("a", "c", "n").getCode()).isEqualTo("INVALID_LICENCE_NUMBER");
    }

    @Test
    void aBadGstinOrStateMismatchIsStillRejected() {
        assertRejected(hospital(HOSPITAL_NUMBER, "NOT-A-GSTIN", "TN", "9000000036"), HttpStatus.BAD_REQUEST, "INVALID_GSTIN");
        // right shape and checksum, but a Tamil Nadu GSTIN with a Kerala state
        assertRejected(hospital(HOSPITAL_NUMBER, GSTIN_TN, "KL", "9000000036"), HttpStatus.BAD_REQUEST, "GSTIN_STATE_MISMATCH");
        // wrong check character
        assertRejected(hospital(HOSPITAL_NUMBER, "33AAGCS6120M1ZC", "TN", "9000000036"), HttpStatus.BAD_REQUEST, "INVALID_GSTIN");
        // the service itself keeps the same codes if it is ever called directly
        assertThat(demo.verifyHospital(HOSPITAL_NUMBER, GSTIN_TN, "KL", "n").getCode()).isEqualTo("GSTIN_STATE_MISMATCH");
        assertThat(demo.verifyPharmacy(PHARMACY_NUMBER, "bad", "TN", "n").getCode()).isEqualTo("INVALID_GSTIN");
    }

    @Test
    void aDuplicateLicenceIsStillA409() {
        service.signup(hospital(HOSPITAL_NUMBER, GSTIN_TN, "TN", "9000000037"));
        assertRejected(hospital(HOSPITAL_NUMBER, GSTIN_TN, "TN", "9000000038"), HttpStatus.CONFLICT, "LICENCE_TAKEN");
    }

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withBean(LicenceRegistryRepository.class, () -> mock(LicenceRegistryRepository.class))
                .withUserConfiguration(MockRegistryVerificationService.class, DemoAcceptVerificationService.class);
    }

    @Test
    void theDefaultAndMockModesStillUseTheRegistryService() {
        runner().run(ctx -> {
            assertThat(ctx).hasSingleBean(MockRegistryVerificationService.class);
            assertThat(ctx).doesNotHaveBean(DemoAcceptVerificationService.class);
        });
        runner().withPropertyValues("licence.verification.mode=mock").run(ctx -> {
            assertThat(ctx).hasSingleBean(MockRegistryVerificationService.class);
            assertThat(ctx).doesNotHaveBean(DemoAcceptVerificationService.class);
        });
    }

    @Test
    void demoAcceptReplacesTheRegistryService() {
        runner().withPropertyValues("licence.verification.mode=demo-accept").run(ctx -> {
            assertThat(ctx).hasSingleBean(DemoAcceptVerificationService.class);
            assertThat(ctx).doesNotHaveBean(MockRegistryVerificationService.class);
        });
    }
}
