package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.PatientProfileView;
import com.curo.curo_backend.dto.UpdateProfileRequest;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static com.curo.curo_backend.support.Fixtures.signIn;
import static com.curo.curo_backend.support.Fixtures.signOut;
import static com.curo.curo_backend.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PatientProfileServiceTest {

    private final AppUser patient = user("p1", "PATIENT", "Test Patient", "CID-7K3MQ9");
    private UserRepository repo;
    private PatientProfileService service;

    @BeforeEach
    void setUp() {
        repo = mock(UserRepository.class);
        when(repo.findById(anyString())).thenAnswer(inv -> "p1".equals(inv.getArgument(0)) ? Optional.of(patient) : Optional.empty());
        when(repo.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        CurrentUserService currentUser = new CurrentUserService();
        ReflectionTestUtils.setField(currentUser, "userRepository", repo);
        service = new PatientProfileService();
        ReflectionTestUtils.setField(service, "currentUser", currentUser);
        ReflectionTestUtils.setField(service, "userRepository", repo);
        signIn(patient);
    }

    @AfterEach
    void tearDown() {
        signOut();
    }

    private UpdateProfileRequest allergies(String... values) {
        UpdateProfileRequest r = new UpdateProfileRequest();
        r.setAllergies(java.util.Arrays.asList(values));
        return r;
    }

    @Test
    void profileShowsIdentityAndAnEmptyAllergyListByDefault() {
        PatientProfileView v = service.get();

        assertThat(v.getCareId()).isEqualTo("CID-7K3MQ9");
        assertThat(v.getName()).isEqualTo("Test Patient");
        assertThat(v.getAllergies()).isEmpty();
        assertThat(v.isProfileCompleted()).isFalse();
    }

    @Test
    void allergiesAreStoredTrimmedWithBlanksAndRepeatsRemoved() {
        PatientProfileView v = service.update(allergies("  Penicillin ", "", "penicillin", "NSAIDs", null, "  "));

        assertThat(v.getAllergies()).containsExactly("Penicillin", "NSAIDs");
        assertThat(patient.getAllergies()).containsExactly("Penicillin", "NSAIDs");
    }

    @Test
    void anEmptyListClearsAllergies() {
        patient.setAllergies(List.of("Penicillin"));

        assertThat(service.update(allergies()).getAllergies()).isEmpty();
    }

    @Test
    void allergiesMustBeSent() {
        UpdateProfileRequest none = new UpdateProfileRequest();
        assertThatThrownBy(() -> service.update(none)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR"));
        assertThatThrownBy(() -> service.update(null)).isInstanceOf(ApiException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void limitsAreEnforced() {
        UpdateProfileRequest tooLong = allergies("x".repeat(61));
        assertThatThrownBy(() -> service.update(tooLong)).isInstanceOf(ApiException.class);

        UpdateProfileRequest tooMany = new UpdateProfileRequest();
        tooMany.setAllergies(IntStream.range(0, 31).mapToObj(i -> "allergy" + i).toList());
        assertThatThrownBy(() -> service.update(tooMany)).isInstanceOf(ApiException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void onlyAPatientHasAProfile() {
        AppUser doctor = user("d1", "DOCTOR", "Dr Test", "CID-4HN8XD");
        when(repo.findById("d1")).thenReturn(Optional.of(doctor));
        signIn(doctor);

        assertThatThrownBy(() -> service.get()).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(e.getCode()).isEqualTo("FORBIDDEN_ROLE");
        });
        assertThatThrownBy(() -> service.update(allergies("Penicillin"))).isInstanceOf(ApiException.class);
        assertThat(Collections.emptyList()).isEmpty();
    }
}
