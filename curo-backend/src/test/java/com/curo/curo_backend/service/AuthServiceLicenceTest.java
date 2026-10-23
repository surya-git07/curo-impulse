package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.ConsentRequest;
import com.curo.curo_backend.dto.LoginRequest;
import com.curo.curo_backend.dto.SignupRequest;
import com.curo.curo_backend.dto.SignupResponse;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.VerificationStatus;
import com.curo.curo_backend.repository.LicenceRegistryRepository;
import com.curo.curo_backend.repository.PharmacyRepository;
import com.curo.curo_backend.model.Pharmacy;
import com.curo.curo_backend.repository.UserRepository;
import com.curo.curo_backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

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

/** DOCTOR and PHARMACY signup against the real mock-registry service and the real seed data. */
class AuthServiceLicenceTest {

    private static final String PASSWORD = "correct-horse-battery";

    private UserRepository users;
    private AuthService service;
    private final Map<String, AppUser> byPhone = new HashMap<>();
    private final Map<String, AppUser> byCareId = new HashMap<>();
    private int careIdCounter = 0;
    private final java.util.List<Pharmacy> pharmacies = new java.util.ArrayList<>();
    private PharmacyRepository pharmacyRepository;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        when(users.existsByPhone(anyString())).thenAnswer(inv -> byPhone.containsKey(inv.<String>getArgument(0)));
        when(users.existsByCareId(anyString())).thenAnswer(inv -> byCareId.containsKey(inv.<String>getArgument(0)));
        when(users.existsByLicenceNumber(anyString())).thenAnswer(inv ->
                byPhone.values().stream().anyMatch(u -> inv.getArgument(0).equals(u.getLicenceNumber())));
        when(users.existsByDrugLicenceNumber(anyString())).thenAnswer(inv ->
                byPhone.values().stream().anyMatch(u -> inv.getArgument(0).equals(u.getDrugLicenceNumber())));
        when(users.findByPhone(anyString())).thenAnswer(inv -> Optional.ofNullable(byPhone.get(inv.<String>getArgument(0))));
        when(users.findByCareId(anyString())).thenAnswer(inv -> Optional.ofNullable(byCareId.get(inv.<String>getArgument(0))));
        when(users.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            u.setId("665f00000000000000" + String.format("%06d", byPhone.size() + 1));
            byPhone.put(u.getPhone(), u);
            byCareId.put(u.getCareId(), u);
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
        when(generator.generate()).thenAnswer(inv -> "CID-AAAA" + (char) ('A' + careIdCounter / 10) + (char) ('A' + careIdCounter++ % 10));

        service = new AuthService();
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "passwordEncoder", new BCryptPasswordEncoder(4));
        ReflectionTestUtils.setField(service, "jwtService", jwt);
        ReflectionTestUtils.setField(service, "careIdGenerator", generator);
        pharmacyRepository = mock(PharmacyRepository.class);
        when(pharmacyRepository.save(any(Pharmacy.class))).thenAnswer(inv -> {
            pharmacies.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        ReflectionTestUtils.setField(service, "pharmacyRepository", pharmacyRepository);
        ReflectionTestUtils.setField(service, "licenceVerificationService", verification);
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

    private SignupRequest doctor(String number, String council, String name) {
        SignupRequest r = base("DOCTOR", name, "9000000002");
        r.setLicenceNumber(number);
        r.setStateCouncil(council);
        return r;
    }

    private SignupRequest pharmacy(String number, String gstin, String state, String name) {
        SignupRequest r = base("PHARMACY", name, "9000000003");
        r.setDrugLicenceNumber(number);
        r.setGstin(gstin);
        r.setState(state);
        return r;
    }

    private SignupRequest validDoctor() {
        return doctor("TN-48213", "Tamil Nadu Medical Council", "Anjali Varadarajan");
    }

    private SignupRequest validPharmacy() {
        return pharmacy("TN/CHE/20B-48213", "33AAGFS4821K1Z5", "TN", "Sri Velan Medicals");
    }

    private void assertApiError(Supplier<?> call, HttpStatus status, String code) {
        assertThatThrownBy(call::get).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    private void assertRejectedWithoutSaving(SignupRequest request, HttpStatus status, String code) {
        assertApiError(() -> service.signup(request), status, code);
        verify(users, never()).save(any());
    }

    // ---- doctor

    @Test
    void doctorMatchingTheRegistryIsVerified() {
        SignupResponse r = service.signup(validDoctor());

        assertThat(r.getRole()).isEqualTo("DOCTOR");
        assertThat(r.getName()).isEqualTo("Anjali Varadarajan");
        assertThat(r.getCareId()).startsWith("CID-");
        assertThat(r.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(r.getVerificationReasonCode()).isEqualTo("LICENCE_MATCHED");
        assertThat(r.getVerificationReason()).isNotBlank();

        AppUser stored = byPhone.get("9000000002");
        assertThat(stored.getLicenceNumber()).isEqualTo("TN-48213");
        assertThat(stored.getStateCouncil()).isEqualTo("Tamil Nadu Medical Council");
        assertThat(stored.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(stored.getDrugLicenceNumber()).isNull();
    }

    @Test
    void doctorWithAWellFormedButUnlistedNumberIsPending() {
        SignupResponse r = service.signup(doctor("tn-70001", "Tamil Nadu Medical Council", "Some Doctor"));

        assertThat(r.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(r.getVerificationReasonCode()).isEqualTo("LICENCE_NOT_IN_REGISTRY");
        assertThat(byPhone.get("9000000002").getLicenceNumber()).isEqualTo("TN-70001");
    }

    @Test
    void doctorWithAMalformedNumberIsRejected() {
        assertRejectedWithoutSaving(doctor("TN 48213", "Tamil Nadu Medical Council", "Anjali Varadarajan"),
                HttpStatus.BAD_REQUEST, "INVALID_LICENCE_NUMBER");
        assertRejectedWithoutSaving(doctor("!!", "Tamil Nadu Medical Council", "Anjali Varadarajan"),
                HttpStatus.BAD_REQUEST, "INVALID_LICENCE_NUMBER");
    }

    @Test
    void doctorWithTheWrongNameIsRejected() {
        assertRejectedWithoutSaving(doctor("TN-48213", "Tamil Nadu Medical Council", "Someone Else"),
                HttpStatus.BAD_REQUEST, "LICENCE_NAME_MISMATCH");
    }

    @Test
    void doctorWithTheWrongCouncilIsRejected() {
        assertRejectedWithoutSaving(doctor("TN-48213", "Karnataka Medical Council", "Anjali Varadarajan"),
                HttpStatus.BAD_REQUEST, "LICENCE_STATE_MISMATCH");
    }

    @Test
    void doctorMissingLicenceFieldsIsRejected() {
        assertRejectedWithoutSaving(doctor(null, "Tamil Nadu Medical Council", "Anjali Varadarajan"), HttpStatus.BAD_REQUEST, "LICENCE_FIELDS_REQUIRED");
        assertRejectedWithoutSaving(doctor(" ", "Tamil Nadu Medical Council", "Anjali Varadarajan"), HttpStatus.BAD_REQUEST, "LICENCE_FIELDS_REQUIRED");
        assertRejectedWithoutSaving(doctor("TN-48213", null, "Anjali Varadarajan"), HttpStatus.BAD_REQUEST, "LICENCE_FIELDS_REQUIRED");
    }

    @Test
    void secondAccountWithTheSameDoctorLicenceIs409EvenWithAnotherPhoneOrCase() {
        service.signup(validDoctor());

        SignupRequest again = doctor("tn-48213", "Tamil Nadu Medical Council", "Anjali Varadarajan");
        again.setPhone("9000000099");

        assertApiError(() -> service.signup(again), HttpStatus.CONFLICT, "LICENCE_TAKEN");
        assertThat(byPhone).hasSize(1);
    }

    @Test
    void duplicateIsReportedBeforeRegistryMismatches() {
        service.signup(validDoctor());

        SignupRequest impostor = doctor("TN-48213", "Tamil Nadu Medical Council", "Wrong Name");
        impostor.setPhone("9000000099");

        assertApiError(() -> service.signup(impostor), HttpStatus.CONFLICT, "LICENCE_TAKEN");
    }

    @Test
    void aLicenceRaceCaughtByTheUniqueIndexIsAlso409() {
        when(users.save(any(AppUser.class))).thenThrow(new DuplicateKeyException("licenceNumber"));
        when(users.existsByLicenceNumber("TN-48213")).thenReturn(false, true);

        assertApiError(() -> service.signup(validDoctor()), HttpStatus.CONFLICT, "LICENCE_TAKEN");
    }

    // ---- pharmacy

    @Test
    void pharmacyMatchingTheRegistryIsVerified() {
        SignupResponse r = service.signup(validPharmacy());

        assertThat(r.getRole()).isEqualTo("PHARMACY");
        assertThat(r.getName()).isEqualTo("Sri Velan Medicals");
        assertThat(r.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(r.getVerificationReasonCode()).isEqualTo("LICENCE_MATCHED");

        AppUser stored = byPhone.get("9000000003");
        assertThat(stored.getDrugLicenceNumber()).isEqualTo("TN/CHE/20B-48213");
        assertThat(stored.getGstin()).isEqualTo("33AAGFS4821K1Z5");
        assertThat(stored.getState()).isEqualTo("TN");
        assertThat(stored.getLicenceNumber()).isNull();
    }

    @Test
    void pharmacyWithAValidGstinButAnUnlistedLicenceIsPending() {
        SignupResponse r = service.signup(pharmacy("tn/che/20b-00001", "33AAAAA0000A1Z9", "tn", "New Chemists"));

        assertThat(r.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(r.getVerificationReasonCode()).isEqualTo("LICENCE_NOT_IN_REGISTRY");
        AppUser stored = byPhone.get("9000000003");
        assertThat(stored.getDrugLicenceNumber()).isEqualTo("TN/CHE/20B-00001");
        assertThat(stored.getState()).isEqualTo("TN");
    }

    @Test
    void pharmacyMissingLicenceFieldsIsRejected() {
        assertRejectedWithoutSaving(pharmacy(null, "33AAGFS4821K1Z5", "TN", "Sri Velan Medicals"), HttpStatus.BAD_REQUEST, "LICENCE_FIELDS_REQUIRED");
        assertRejectedWithoutSaving(pharmacy("TN/CHE/20B-48213", null, "TN", "Sri Velan Medicals"), HttpStatus.BAD_REQUEST, "LICENCE_FIELDS_REQUIRED");
        assertRejectedWithoutSaving(pharmacy("TN/CHE/20B-48213", "33AAGFS4821K1Z5", "", "Sri Velan Medicals"), HttpStatus.BAD_REQUEST, "LICENCE_FIELDS_REQUIRED");
    }

    @Test
    void pharmacyWithAnUnknownStateCodeIsRejected() {
        assertRejectedWithoutSaving(pharmacy("TN/CHE/20B-48213", "33AAGFS4821K1Z5", "ZZ", "Sri Velan Medicals"), HttpStatus.BAD_REQUEST, "INVALID_STATE");
        assertRejectedWithoutSaving(pharmacy("TN/CHE/20B-48213", "33AAGFS4821K1Z5", "Tamil Nadu", "Sri Velan Medicals"), HttpStatus.BAD_REQUEST, "INVALID_STATE");
    }

    @Test
    void pharmacyWithABadGstinIsRejected() {
        assertRejectedWithoutSaving(pharmacy("TN/CHE/20B-48213", "33AAGFS4821K1Z", "TN", "Sri Velan Medicals"), HttpStatus.BAD_REQUEST, "INVALID_GSTIN");
        assertRejectedWithoutSaving(pharmacy("TN/CHE/20B-48213", "33AAGFS4821K1Z6", "TN", "Sri Velan Medicals"), HttpStatus.BAD_REQUEST, "INVALID_GSTIN");
        assertRejectedWithoutSaving(pharmacy("TN/CHE/20B-48213", "99AAGFS4821K1ZN", "TN", "Sri Velan Medicals"), HttpStatus.BAD_REQUEST, "INVALID_GSTIN");
    }

    @Test
    void pharmacyWhoseGstinBelongsToAnotherStateIsRejected() {
        assertRejectedWithoutSaving(pharmacy("TN/CHE/20B-48213", "33AAGFS4821K1Z5", "KA", "Sri Velan Medicals"), HttpStatus.BAD_REQUEST, "GSTIN_STATE_MISMATCH");
    }

    @Test
    void pharmacyWithAMalformedLicenceNumberIsRejected() {
        assertRejectedWithoutSaving(pharmacy("12 34", "33AAGFS4821K1Z5", "TN", "Sri Velan Medicals"), HttpStatus.BAD_REQUEST, "INVALID_LICENCE_NUMBER");
    }

    @Test
    void pharmacyWithTheWrongNameIsRejected() {
        assertRejectedWithoutSaving(pharmacy("TN/CHE/20B-48213", "33AAGFS4821K1Z5", "TN", "Other Chemists"), HttpStatus.BAD_REQUEST, "LICENCE_NAME_MISMATCH");
    }

    @Test
    void pharmacyWithTheWrongStateForItsLicenceIsRejected() {
        // a valid Karnataka GSTIN and state, but the licence is registered in Tamil Nadu
        assertRejectedWithoutSaving(pharmacy("TN/CHE/20B-48213", "29AAGCN7731L1ZW", "KA", "Sri Velan Medicals"), HttpStatus.BAD_REQUEST, "LICENCE_STATE_MISMATCH");
    }

    @Test
    void pharmacyWithADifferentValidGstinIsRejected() {
        assertRejectedWithoutSaving(pharmacy("TN/CHE/20B-48213", "33AAAAA0000A1Z9", "TN", "Sri Velan Medicals"), HttpStatus.BAD_REQUEST, "LICENCE_GSTIN_MISMATCH");
    }

    @Test
    void secondAccountWithTheSameDrugLicenceIs409() {
        service.signup(validPharmacy());

        SignupRequest again = validPharmacy();
        again.setPhone("9000000099");

        assertApiError(() -> service.signup(again), HttpStatus.CONFLICT, "LICENCE_TAKEN");
        assertThat(byPhone).hasSize(1);
    }

    // ---- the pharmacy record created at signup

    private SignupRequest locatedPharmacy() {
        SignupRequest r = validPharmacy();
        r.setAddress("  1 Test Street, Chennai ");
        return r;
    }

    @Test
    void aVerifiedPharmacySignupCreatesAListedPharmacyLinkedToTheAccount() {
        SignupResponse r = service.signup(locatedPharmacy());

        assertThat(pharmacies).singleElement().satisfies(p -> {
            assertThat(p.getId()).isEqualTo(r.getFacilityId()).isNotBlank();
            assertThat(p.getName()).isEqualTo("Sri Velan Medicals");
            assertThat(p.getAddress()).isEqualTo("1 Test Street, Chennai");
            assertThat(p.getState()).isEqualTo("TN");
            assertThat(p.isVerified()).isTrue();
            assertThat(p.getOwnerUserId()).isEqualTo(r.getId());
            assertThat(p.getSeedKey()).isNull();
        });
        AppUser user = byPhone.get("9000000003");
        assertThat(user.getFacilityId()).isEqualTo(r.getFacilityId());
        assertThat(user.getFacilityName()).isEqualTo("Sri Velan Medicals");
    }

    @Test
    void aPendingPharmacyIsCreatedButNotListedUntilApproved() {
        service.signup(pharmacy("tn/che/20b-00001", "33AAAAA0000A1Z9", "TN", "New Chemists"));

        assertThat(pharmacies).singleElement().satisfies(p -> assertThat(p.isVerified()).isFalse());
    }

    @Test
    void aPharmacyWithoutAnAddressIsStillCreated() {
        service.signup(validPharmacy());

        assertThat(pharmacies).singleElement().satisfies(p -> assertThat(p.getAddress()).isNull());
    }

    @Test
    void signupKnowsNothingOfCoordinatesAnyMore() {
        assertThat(SignupRequest.class.getDeclaredFields()).extracting(java.lang.reflect.Field::getName).doesNotContain("lat", "lng");
        assertThat(Pharmacy.class.getDeclaredFields()).extracting(java.lang.reflect.Field::getName).doesNotContain("lat", "lng");
    }

    @Test
    void ifThePharmacyCannotBeCreatedTheAccountIsUndone() {
        when(pharmacyRepository.save(any(Pharmacy.class))).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("down"));

        assertApiError(() -> service.signup(validPharmacy()), HttpStatus.INTERNAL_SERVER_ERROR, "PHARMACY_UNAVAILABLE");

        org.mockito.Mockito.verify(users).delete(any(AppUser.class));
    }

    @Test
    void doctorsAndPatientsGetNoPharmacy() {
        service.signup(validDoctor());
        service.signup(base("PATIENT", "Test Patient", "9000000001"));

        assertThat(pharmacies).isEmpty();
        assertThat(byPhone.get("9000000002").getFacilityId()).isNull();
        assertThat(byPhone.get("9000000001").getFacilityId()).isNull();
    }

    @Test
    void aRejectedPharmacyApplicationCreatesNothing() {
        assertRejectedWithoutSaving(pharmacy("TN/CHE/20B-48213", "33AAGFS4821K1Z5", "TN", "Other Chemists"),
                HttpStatus.BAD_REQUEST, "LICENCE_NAME_MISMATCH");
        assertThat(pharmacies).isEmpty();
    }

    @Test
    void anEmptyRegistryMeansEveryWellFormedLicenceIsPendingNotVerified() {
        LicenceRegistryRepository empty = mock(LicenceRegistryRepository.class);
        when(empty.findByTypeAndLicenceNumber(anyString(), anyString())).thenReturn(Optional.empty());
        MockRegistryVerificationService verification = new MockRegistryVerificationService();
        ReflectionTestUtils.setField(verification, "registryRepository", empty);
        ReflectionTestUtils.setField(service, "licenceVerificationService", verification);

        assertThat(service.signup(validDoctor()).getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
    }

    // ---- login of licensed accounts

    private LoginRequest login(String identifier, String password) {
        LoginRequest r = new LoginRequest();
        r.setIdentifier(identifier);
        r.setPassword(password);
        return r;
    }

    @Test
    void verifiedDoctorCanLogIn() {
        String careId = service.signup(validDoctor()).getCareId();

        assertThat(service.login(login(careId, PASSWORD)).getToken()).isNotBlank();
    }

    @Test
    void pendingAccountGets403OnlyWithTheRightPassword() {
        String careId = service.signup(doctor("TN-70001", "Tamil Nadu Medical Council", "Some Doctor")).getCareId();

        assertApiError(() -> service.login(login(careId, PASSWORD)), HttpStatus.FORBIDDEN, "ACCOUNT_PENDING");
        assertApiError(() -> service.login(login(careId, "wrong-password")), HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
    }

    @Test
    void approvedAccountCanLogInAndRejectedCannot() {
        String careId = service.signup(doctor("TN-70001", "Tamil Nadu Medical Council", "Some Doctor")).getCareId();
        AppUser stored = byCareId.get(careId);

        stored.setVerificationStatus(VerificationStatus.VERIFIED);
        assertThat(service.login(login(careId, PASSWORD)).getToken()).isNotBlank();

        stored.setVerificationStatus(VerificationStatus.REJECTED);
        assertApiError(() -> service.login(login(careId, PASSWORD)), HttpStatus.FORBIDDEN, "ACCOUNT_REJECTED");
    }

    @Test
    void patientsAreUnaffectedByVerificationRules() {
        SignupResponse r = service.signup(base("PATIENT", "Test Patient", "9000000001"));

        assertThat(r.getVerificationStatus()).isNull();
        assertThat(service.login(login(r.getCareId(), PASSWORD)).getToken()).isNotBlank();
        AppUser stored = byPhone.get("9000000001");
        assertThat(stored.getLicenceNumber()).isNull();
        assertThat(stored.getDrugLicenceNumber()).isNull();
        assertThat(stored.getGstin()).isNull();
    }
}
