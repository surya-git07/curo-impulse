package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.PatientProfileView;
import com.curo.curo_backend.dto.UpdateProfileRequest;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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

/** The optional profile fields a patient can set: date of birth, gender, blood group and chronic conditions. */
class PatientProfileFieldsTest {

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

    @Test
    void theNewFieldsAreStoredAndShownBack() {
        UpdateProfileRequest r = new UpdateProfileRequest();
        r.setBloodGroup(" b+ ");
        r.setGender("male");
        r.setDateOfBirth(LocalDate.of(1980, 5, 14));
        r.setChronicConditions(List.of("Type 2 diabetes", "type 2 DIABETES", " ", "Asthma"));

        PatientProfileView v = service.update(r);

        assertThat(v.getBloodGroup()).isEqualTo("B+");
        assertThat(v.getGender()).isEqualTo("MALE");
        assertThat(v.getDateOfBirth()).isEqualTo(LocalDate.of(1980, 5, 14));
        assertThat(v.getChronicConditions()).containsExactly("Type 2 diabetes", "Asthma");
        assertThat(patient.getBloodGroup()).isEqualTo("B+");
    }

    @Test
    void aFieldThatIsNotSentIsLeftAsItWas() {
        patient.setAllergies(List.of("Latex"));
        patient.setBloodGroup("O-");
        UpdateProfileRequest r = new UpdateProfileRequest();
        r.setGender("FEMALE");

        service.update(r);

        assertThat(patient.getAllergies()).containsExactly("Latex");
        assertThat(patient.getBloodGroup()).isEqualTo("O-");
        assertThat(patient.getGender()).isEqualTo("FEMALE");
    }

    @Test
    void badValuesAreRefusedAndChangeNothing() {
        UpdateProfileRequest badBlood = new UpdateProfileRequest();
        badBlood.setAllergies(List.of("Latex"));
        badBlood.setBloodGroup("C+");
        assertThatThrownBy(() -> service.update(badBlood)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR"));

        UpdateProfileRequest badGender = new UpdateProfileRequest();
        badGender.setGender("robot");
        assertThatThrownBy(() -> service.update(badGender)).isInstanceOf(ApiException.class);

        UpdateProfileRequest future = new UpdateProfileRequest();
        future.setDateOfBirth(LocalDate.now().plusDays(1));
        assertThatThrownBy(() -> service.update(future)).isInstanceOf(ApiException.class);

        assertThat(patient.getAllergies()).isNullOrEmpty();
        verify(repo, never()).save(any(AppUser.class));
    }

    @Test
    void anEmptyRequestIsStillRefused() {
        assertThatThrownBy(() -> service.update(new UpdateProfileRequest())).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("VALIDATION_ERROR"));
    }
}
