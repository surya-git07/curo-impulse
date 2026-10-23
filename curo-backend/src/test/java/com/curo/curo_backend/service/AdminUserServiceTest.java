package com.curo.curo_backend.service;

import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.Pharmacy;
import com.curo.curo_backend.model.VerificationStatus;
import com.curo.curo_backend.repository.PharmacyRepository;
import com.curo.curo_backend.repository.UserRepository;
import com.curo.curo_backend.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PharmacyRepository pharmacies = mock(PharmacyRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final AdminUserService service = new AdminUserService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "pharmacyRepository", pharmacies);
        ReflectionTestUtils.setField(service, "currentUser", currentUser);
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        asRole("SUPER_ADMIN");
    }

    private void asRole(String role) {
        AppUser me = new AppUser();
        me.setId("admin1");
        me.setRole(role);
        when(currentUser.require()).thenReturn(me);
    }

    private AppUser pending(String role) {
        AppUser u = new AppUser();
        u.setId("u1");
        u.setRole(role);
        u.setName("Test Person");
        u.setVerificationStatus(VerificationStatus.PENDING);
        u.setFacilityId("PHARM".equals(role) ? null : "ph1");
        when(users.findById("u1")).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void verifyMakesAPendingDoctorVerified() {
        pending("DOCTOR");
        var view = service.verify("u1");
        assertThat(view.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(view.getVerificationReasonCode()).isEqualTo("APPROVED_BY_ADMIN");
    }

    @Test
    void verifyingAPharmacyAlsoSwitchesItsRecordOn() {
        pending("PHARMACY");
        Pharmacy ph = new Pharmacy();
        ph.setId("ph1");
        when(pharmacies.findById("ph1")).thenReturn(Optional.of(ph));
        service.verify("u1");
        assertThat(ph.isVerified()).isTrue();
        verify(pharmacies).save(ph);
    }

    @Test
    void rejectNeedsAReasonAndStoresIt() {
        pending("DOCTOR");
        assertThatThrownBy(() -> service.reject("u1", "  ")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("REASON_REQUIRED"));
        var view = service.reject("u1", "Licence could not be confirmed");
        assertThat(view.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(view.getVerificationReason()).isEqualTo("Licence could not be confirmed");
    }

    @Test
    void anAlreadyDecidedAccountIsNotPending() {
        AppUser u = pending("DOCTOR");
        u.setVerificationStatus(VerificationStatus.VERIFIED);
        assertThatThrownBy(() -> service.verify("u1")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("NOT_PENDING"));
        assertThatThrownBy(() -> service.reject("u1", "x")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("NOT_PENDING"));
        verify(users, never()).save(any(AppUser.class));
    }

    @Test
    void anUnknownOrNonStaffAccountIsNotFound() {
        when(users.findById("nobody")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.verify("nobody")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("USER_NOT_FOUND"));
        AppUser patient = new AppUser();
        patient.setId("p1");
        patient.setRole("PATIENT");
        when(users.findById("p1")).thenReturn(Optional.of(patient));
        assertThatThrownBy(() -> service.verify("p1")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("USER_NOT_FOUND"));
    }

    @Test
    void onlyASuperAdminMayReview() {
        asRole("ADMIN");
        assertThatThrownBy(() -> service.verify("u1")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.list(null, 0, 20)).isInstanceOf(ApiException.class);
    }

    @Test
    void seederCreatesTheSuperAdminOnlyWithAPasswordAndNeverTwice() {
        UserRepository repo = mock(UserRepository.class);
        SuperAdminSeeder seeder = new SuperAdminSeeder();
        ReflectionTestUtils.setField(seeder, "userRepository", repo);
        ReflectionTestUtils.setField(seeder, "passwordEncoder", new BCryptPasswordEncoder());
        ReflectionTestUtils.setField(seeder, "careIdGenerator", new CareIdGenerator());
        ReflectionTestUtils.setField(seeder, "phone", "9000000001");

        ReflectionTestUtils.setField(seeder, "password", "");
        assertThat(seeder.seed()).isFalse();
        verify(repo, never()).save(any(AppUser.class));

        ReflectionTestUtils.setField(seeder, "password", "a-test-only-value");
        assertThat(seeder.seed()).isTrue();
        verify(repo).save(org.mockito.ArgumentMatchers.argThat(u ->
                "SUPER_ADMIN".equals(u.getRole()) && "9000000001".equals(u.getPhone())
                        && u.getPasswordHash() != null && !u.getPasswordHash().contains("a-test-only-value")));

        when(repo.existsByPhone("9000000001")).thenReturn(true);
        assertThat(seeder.seed()).isFalse();
    }
}
