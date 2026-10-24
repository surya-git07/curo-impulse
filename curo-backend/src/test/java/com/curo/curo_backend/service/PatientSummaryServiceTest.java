package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.MedicationView;
import com.curo.curo_backend.dto.PatientSummaryView;
import com.curo.curo_backend.model.AccessGrant;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.ChangeType;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.model.PrescriptionStatus;
import com.curo.curo_backend.model.PrescriptionVersion;
import com.curo.curo_backend.repository.PrescriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The doctor Patient screen: nothing without an active grant, the right parts with one. All data is fake. */
class PatientSummaryServiceTest {

    private static final String CARE_ID = "CID-7K3MQ9";

    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final AccessGuard guard = mock(AccessGuard.class);
    private final MedicationService medications = mock(MedicationService.class);
    private final PrescriptionRepository prescriptions = mock(PrescriptionRepository.class);
    private final PatientSummaryService service = new PatientSummaryService();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "currentUser", currentUser);
        ReflectionTestUtils.setField(service, "accessGuard", guard);
        ReflectionTestUtils.setField(service, "medicationService", medications);
        ReflectionTestUtils.setField(service, "prescriptionRepository", prescriptions);
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(Instant.parse("2026-10-23T05:00:00Z"), ZoneOffset.UTC));
        ReflectionTestUtils.setField(service, "timeZone", "Asia/Kolkata");
        signedInAs("DOCTOR");
    }

    private void signedInAs(String role) {
        AppUser u = new AppUser();
        u.setId("u1");
        u.setRole(role);
        when(currentUser.require()).thenReturn(u);
    }

    private AppUser patient() {
        AppUser p = new AppUser();
        p.setId("p1");
        p.setCareId(CARE_ID);
        p.setName("Test Patient");
        p.setPhone("9000000101");
        p.setDateOfBirth(LocalDate.of(1980, 5, 14));
        p.setGender("MALE");
        p.setBloodGroup("B+");
        p.setAllergies(List.of("Latex"));
        p.setChronicConditions(List.of("Type 2 diabetes"));
        return p;
    }

    private void grantWith(GrantScope... scopes) {
        AccessGrant g = new AccessGrant();
        g.setScope(Set.of(scopes).stream().toList());
        when(guard.require(eq(CARE_ID), any(GrantScope.class), any(), anyString(), anyString()))
                .thenReturn(new AccessGuard.Granted(patient(), g));
    }

    private Prescription prescription() {
        Prescription p = new Prescription();
        p.setId("rx1");
        p.setCareId(CARE_ID);
        p.setDrug("Metformin");
        p.setDose("500 mg");
        p.setStatus(PrescriptionStatus.ACTIVE);
        p.setIssuedAt(Instant.parse("2026-10-01T05:00:00Z"));
        p.setCurrentVersion(2);
        PrescriptionVersion v1 = new PrescriptionVersion();
        v1.setVersion(1);
        v1.setChangeType(ChangeType.ISSUED);
        v1.setDose("250 mg");
        PrescriptionVersion v2 = new PrescriptionVersion();
        v2.setVersion(2);
        v2.setChangeType(ChangeType.MODIFIED);
        v2.setDose("500 mg");
        v2.setReason("Dose raised");
        p.setVersions(List.of(v1, v2));
        return p;
    }

    @Test
    void withAFullGrantTheScreenGetsEveryField() {
        grantWith(GrantScope.PROFILE, GrantScope.MEDICATIONS, GrantScope.PRESCRIPTIONS);
        when(medications.currentMedications(CARE_ID)).thenReturn(List.of(
                new MedicationView("rx1", "Metformin", "500 mg", null, List.of(), null, null, PrescriptionStatus.ACTIVE, 2, null)));
        when(prescriptions.findByCareIdOrderByIssuedAtDesc(CARE_ID)).thenReturn(List.of(prescription()));

        PatientSummaryView v = service.summary(CARE_ID);

        assertThat(v.getName()).isEqualTo("Test Patient");
        assertThat(v.getAgeYears()).isEqualTo(46);
        assertThat(v.getGender()).isEqualTo("MALE");
        assertThat(v.getBloodGroup()).isEqualTo("B+");
        assertThat(v.getAllergies()).containsExactly("Latex");
        assertThat(v.getChronicConditions()).containsExactly("Type 2 diabetes");
        assertThat(v.getMedications()).extracting(MedicationView::getDrug).containsExactly("Metformin");
        assertThat(v.getPrescriptions()).hasSize(1);
        assertThat(v.getPrescriptions().get(0).getVersions()).hasSize(2);
        assertThat(v.getPrescriptions().get(0).getVersions().get(1).getReason()).isEqualTo("Dose raised");
    }

    @Test
    void thePhoneAndDateOfBirthAreNeverReturned() throws Exception {
        grantWith(GrantScope.PROFILE);
        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .findAndRegisterModules().writeValueAsString(service.summary(CARE_ID));
        assertThat(json).doesNotContain("9000000101").doesNotContain("1980").doesNotContain("dateOfBirth").doesNotContain("phone");
    }

    @Test
    void aProfileOnlyGrantLeavesOutMedicinesAndPrescriptions() {
        grantWith(GrantScope.PROFILE);

        PatientSummaryView v = service.summary(CARE_ID);

        assertThat(v.getBloodGroup()).isEqualTo("B+");
        assertThat(v.getMedications()).isNull();
        assertThat(v.getPrescriptions()).isNull();
        verifyNoInteractions(medications, prescriptions);
    }

    @Test
    void withoutAGrantNothingAboutThePatientIsReadOrReturned() {
        when(guard.require(eq(CARE_ID), any(GrantScope.class), any(), anyString(), anyString()))
                .thenThrow(new ApiException(HttpStatus.FORBIDDEN, "NO_ACTIVE_GRANT", "No active access."));

        assertThatThrownBy(() -> service.summary(CARE_ID)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(e.getCode()).isEqualTo("NO_ACTIVE_GRANT");
        });
        verifyNoInteractions(medications, prescriptions);
    }

    @Test
    void aPatientWithNoOptionalFieldsStillWorks() {
        AppUser bare = new AppUser();
        bare.setCareId(CARE_ID);
        bare.setName("Bare Patient");
        AccessGrant g = new AccessGrant();
        g.setScope(List.of(GrantScope.PROFILE));
        when(guard.require(eq(CARE_ID), any(GrantScope.class), any(), anyString(), anyString()))
                .thenReturn(new AccessGuard.Granted(bare, g));

        PatientSummaryView v = service.summary(CARE_ID);

        assertThat(v.getAgeYears()).isNull();
        assertThat(v.getBloodGroup()).isNull();
        assertThat(v.getAllergies()).isEmpty();
        assertThat(v.getChronicConditions()).isEmpty();
    }

    @Test
    void onlyADoctorMayAsk() {
        for (String role : new String[]{"PATIENT", "PHARMACY", "RECEPTION", "HOSPITAL", "CAREGIVER", "ADMIN"}) {
            signedInAs(role);
            assertThatThrownBy(() -> service.summary(CARE_ID)).as(role).isInstanceOfSatisfying(ApiException.class,
                    e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        }
        verifyNoInteractions(guard);
    }
}
