package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.MedicationProfile;
import com.curo.curo_backend.dto.MedicationView;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.AuditOutcome;
import com.curo.curo_backend.model.Frequency;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.GrantStatus;
import com.curo.curo_backend.model.PrescriptionStatus;
import com.curo.curo_backend.support.PrescriptionHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.curo.curo_backend.support.Fixtures.signIn;
import static com.curo.curo_backend.support.Fixtures.signOut;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MedicationServiceTest {

    private static final Instant T0 = PrescriptionHarness.T0; // 2026-10-23 15:30 in India

    private PrescriptionHarness h;
    private MedicationService service;

    @BeforeEach
    void setUp() {
        h = new PrescriptionHarness();
        service = h.medicationService;
        h.patient.setAllergies(List.of("Penicillin"));
    }

    @AfterEach
    void tearDown() {
        signOut();
    }

    private MedicationProfile asDoctor(GrantScope... scope) {
        h.grantTo(h.doctor, scope);
        signIn(h.doctor);
        return service.forPatient("CID-7K3MQ9");
    }

    @Test
    void listsOnlyWhatThePatientIsTakingNow() {
        h.existing("Metformin", T0.minus(Duration.ofDays(2)), null, PrescriptionStatus.ACTIVE);
        h.existing("Amoxicillin", T0.minus(Duration.ofDays(2)), 7, PrescriptionStatus.ACTIVE);
        h.existing("Ibuprofen", T0.minus(Duration.ofDays(20)), 5, PrescriptionStatus.ACTIVE);
        h.existing("Warfarin", T0.minus(Duration.ofDays(20)), null, PrescriptionStatus.DISCONTINUED);

        MedicationProfile profile = asDoctor(GrantScope.MEDICATIONS);

        assertThat(profile.getMedications()).extracting(MedicationView::getDrug).containsExactly("Amoxicillin", "Metformin");
    }

    @Test
    void worksOutStartAndInclusiveEndDatesInIndianTime() {
        // 2026-10-23 20:00 UTC is already 24 October in India
        h.existing("Amoxicillin", Instant.parse("2026-10-23T20:00:00Z"), 7, PrescriptionStatus.ACTIVE);

        MedicationView m = asDoctor(GrantScope.MEDICATIONS).getMedications().get(0);

        assertThat(m.getStartDate()).isEqualTo(LocalDate.of(2026, 10, 24));
        assertThat(m.getEndDate()).as("7-day course").isEqualTo(LocalDate.of(2026, 10, 30));
    }

    @Test
    void aCourseWithoutADurationHasNoEndDateAndStaysUntilDiscontinued() {
        h.existing("Metformin", T0.minus(Duration.ofDays(400)), null, PrescriptionStatus.ACTIVE);

        MedicationView m = asDoctor(GrantScope.MEDICATIONS).getMedications().get(0);

        assertThat(m.getEndDate()).isNull();
    }

    @Test
    void aCourseDropsOffTheDayAfterItsLastDay() {
        h.existing("Amoxicillin", T0.minus(Duration.ofDays(3)), 7, PrescriptionStatus.ACTIVE);
        h.grantTo(h.doctor, GrantStatus.ACTIVE, T0.plus(Duration.ofDays(30)), GrantScope.MEDICATIONS);
        signIn(h.doctor);

        assertThat(service.forPatient("CID-7K3MQ9").getMedications()).hasSize(1);
        h.clock.advance(Duration.ofDays(3)); // last day (inclusive) is day 7
        assertThat(service.forPatient("CID-7K3MQ9").getMedications()).hasSize(1);
        h.clock.advance(Duration.ofDays(1));
        assertThat(service.forPatient("CID-7K3MQ9").getMedications()).isEmpty();
    }

    @Test
    void eachMedicationCarriesTheContractFields() {
        var p = h.existing("Metformin", T0.minus(Duration.ofDays(1)), 30, PrescriptionStatus.ACTIVE);
        p.setFrequency(Frequency.TWICE_DAILY);
        p.setSchedule(Frequency.TWICE_DAILY.defaultTimes());
        p.setDose("500 mg");
        p.setCurrentVersion(3);

        MedicationView m = asDoctor(GrantScope.MEDICATIONS).getMedications().get(0);

        assertThat(m.getPrescriptionId()).isEqualTo(p.getId());
        assertThat(m.getDose()).isEqualTo("500 mg");
        assertThat(m.getFrequency()).isEqualTo(Frequency.TWICE_DAILY);
        assertThat(m.getTimes()).containsExactly("08:00", "20:00");
        assertThat(m.getStatus()).isEqualTo(PrescriptionStatus.ACTIVE);
        assertThat(m.getCurrentVersion()).isEqualTo(3);
        assertThat(m.getPrescribedBy().getName()).isEqualTo("Dr Test");
    }

    @Test
    void anotherPatientsMedicationsNeverAppear() {
        h.existing("Metformin").setCareId("CID-OTHER1");

        assertThat(asDoctor(GrantScope.MEDICATIONS).getMedications()).isEmpty();
    }

    @Test
    void medicationsAreOrderedByIssueDateThenName() {
        h.existing("Metformin", T0.minus(Duration.ofDays(1)), null, PrescriptionStatus.ACTIVE);
        h.existing("Atenolol", T0.minus(Duration.ofDays(1)), null, PrescriptionStatus.ACTIVE);
        h.existing("Insulin", T0.minus(Duration.ofDays(5)), null, PrescriptionStatus.ACTIVE);

        assertThat(asDoctor(GrantScope.MEDICATIONS).getMedications()).extracting(MedicationView::getDrug)
                .containsExactly("Insulin", "Atenolol", "Metformin");
    }

    // ---- allergies: profile data, shown only when the grant reaches it

    @Test
    void allergiesAreHiddenFromAGrantThatOnlyCoversMedications() {
        assertThat(asDoctor(GrantScope.MEDICATIONS).getAllergies()).isNull();
    }

    @Test
    void allergiesAreShownWhenTheGrantAlsoCoversProfile() {
        assertThat(asDoctor(GrantScope.MEDICATIONS, GrantScope.PROFILE).getAllergies()).containsExactly("Penicillin");
    }

    // ---- access and audit

    @Test
    void aClinicianReadIsLoggedAgainstThePatient() {
        asDoctor(GrantScope.MEDICATIONS);

        assertThat(h.audit).singleElement().satisfies(e -> {
            assertThat(e.getAction()).isEqualTo(AuditAction.READ);
            assertThat(e.getResource()).isEqualTo("MEDICATIONS");
            assertThat(e.getOutcome()).isEqualTo(AuditOutcome.ALLOWED);
            assertThat(e.getPatientId()).isEqualTo("p1");
            assertThat(e.getActorUserId()).isEqualTo("d1");
        });
    }

    @Test
    void aCaregiverNeedsTheirOwnGrant() {
        signIn(h.caregiver);
        assertThatThrownBy(() -> service.forPatient("CID-7K3MQ9")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("NO_ACTIVE_GRANT"));

        h.grantTo(h.caregiver, GrantScope.MEDICATIONS);
        assertThat(service.forPatient("CID-7K3MQ9").getMedications()).isEmpty();
    }

    @Test
    void aPharmacyWithAPrescriptionsOnlyGrantCannotSeeTheMedicationProfile() {
        h.grantTo(h.pharmacy, GrantScope.PRESCRIPTIONS);
        signIn(h.pharmacy);

        assertThatThrownBy(() -> service.forPatient("CID-7K3MQ9")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("NO_ACTIVE_GRANT"));
    }

    // ---- the patient's own view

    @Test
    void aPatientSeesTheirOwnMedicationsAndAllergiesWithoutAGrant() {
        h.existing("Metformin");
        signIn(h.patient);

        MedicationProfile profile = service.mine();

        assertThat(profile.getCareId()).isEqualTo("CID-7K3MQ9");
        assertThat(profile.getMedications()).hasSize(1);
        assertThat(profile.getAllergies()).containsExactly("Penicillin");
        assertThat(h.audit).as("reading your own data is not an access event").isEmpty();
    }

    @Test
    void onlyPatientsHaveAProfileOfTheirOwn() {
        signIn(h.doctor);
        assertThatThrownBy(() -> service.mine()).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(e.getCode()).isEqualTo("FORBIDDEN_ROLE");
        });
    }

    @Test
    void notSignedInIs401() {
        signOut();
        assertThatThrownBy(() -> service.mine()).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
