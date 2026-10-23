package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.ActorView;
import com.curo.curo_backend.dto.HistoryResponse;
import com.curo.curo_backend.dto.IssueResponse;
import com.curo.curo_backend.dto.MedicationProfile;
import com.curo.curo_backend.dto.MedicationView;
import com.curo.curo_backend.dto.PatientProfileView;
import com.curo.curo_backend.dto.PrescriptionView;
import com.curo.curo_backend.dto.VersionView;
import com.curo.curo_backend.dto.WarningView;
import com.curo.curo_backend.model.ChangeType;
import com.curo.curo_backend.model.DrugMaster;
import com.curo.curo_backend.model.Frequency;
import com.curo.curo_backend.model.PrescriptionStatus;
import com.curo.curo_backend.model.Severity;
import com.curo.curo_backend.repository.DrugMasterRepository;
import com.curo.curo_backend.service.ApiException;
import com.curo.curo_backend.service.MedicationService;
import com.curo.curo_backend.service.PatientProfileService;
import com.curo.curo_backend.service.PrescriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PrescriptionControllersTest {

    private static final Instant T0 = Instant.parse("2026-10-23T10:00:00Z");

    private PrescriptionService prescriptions;
    private MedicationService medications;
    private PatientProfileService profile;
    private DrugMasterRepository drugRepo;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        prescriptions = mock(PrescriptionService.class);
        medications = mock(MedicationService.class);
        profile = mock(PatientProfileService.class);
        drugRepo = mock(DrugMasterRepository.class);

        PrescriptionController p = new PrescriptionController();
        ReflectionTestUtils.setField(p, "prescriptionService", prescriptions);
        MedicationController m = new MedicationController();
        ReflectionTestUtils.setField(m, "medicationService", medications);
        PatientProfileController pp = new PatientProfileController();
        ReflectionTestUtils.setField(pp, "patientProfileService", profile);
        DrugController d = new DrugController();
        ReflectionTestUtils.setField(d, "drugMasterRepository", drugRepo);

        mvc = MockMvcBuilders.standaloneSetup(p, m, pp, d).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    private PrescriptionView view(int version, PrescriptionStatus status, List<VersionView> versions) {
        return new PrescriptionView("rx1", "CID-7K3MQ9", "d1", "Warfarin", "5 mg", "7 days", 7, Frequency.TWICE_DAILY,
                List.of("08:00", "20:00"), status, T0, null, version, versions);
    }

    private VersionView version(int n, ChangeType type, String reason) {
        return new VersionView(n, type, "5 mg", "7 days", 7, Frequency.TWICE_DAILY, List.of("08:00", "20:00"), reason,
                new ActorView("d1", "DOCTOR", "Dr Test", null), T0);
    }

    // ---- prescriptions

    @Test
    void issueReturns201WithPrescriptionsAndWarnings() throws Exception {
        when(prescriptions.issue(any())).thenReturn(new IssueResponse(
                List.of(view(1, PrescriptionStatus.ACTIVE, null)),
                List.of(new WarningView("Aspirin", Severity.HIGH, "Aspirin with Warfarin: Serious bleeding risk.", "- bleeding"))));

        mvc.perform(post("/prescription/issue").contentType(MediaType.APPLICATION_JSON).content("""
                        {"careId":"CID-7K3MQ9","doctorId":"ignored","hospitalId":"ignored",
                         "drugs":[{"drug":"Aspirin","dose":"75 mg","frequency":"ONCE_DAILY","durationDays":7}]}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.drugs[0].id").value("rx1"))
                .andExpect(jsonPath("$.drugs[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.drugs[0].times[1]").value("20:00"))
                .andExpect(jsonPath("$.drugs[0].currentVersion").value(1))
                .andExpect(jsonPath("$.drugs[0].versions").doesNotExist())
                .andExpect(jsonPath("$.warnings[0].drug").value("Aspirin"))
                .andExpect(jsonPath("$.warnings[0].severity").value("HIGH"))
                .andExpect(jsonPath("$.warnings[0].warning").value("Aspirin with Warfarin: Serious bleeding risk."))
                .andExpect(jsonPath("$.warnings[0].explanation").exists())
                .andExpect(jsonPath("$.warnings[0].blocked").doesNotExist())
                .andExpect(jsonPath("$.blocked").doesNotExist());

        var captor = ArgumentCaptor.forClass(com.curo.curo_backend.dto.IssueRequest.class);
        verify(prescriptions).issue(captor.capture());
        assertThat(captor.getValue().getCareId()).isEqualTo("CID-7K3MQ9");
        assertThat(captor.getValue().getDrugs().get(0).getDurationDays()).isEqualTo(7);
    }

    @Test
    void issueWithoutACareIdIs400() throws Exception {
        mvc.perform(post("/prescription/issue").contentType(MediaType.APPLICATION_JSON).content("{\"drugs\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        verifyNoInteractions(prescriptions);
    }

    @Test
    void noActiveGrantComesBackAs403() throws Exception {
        when(prescriptions.issue(any())).thenThrow(new ApiException(HttpStatus.FORBIDDEN, "NO_ACTIVE_GRANT", "No grant."));

        mvc.perform(post("/prescription/issue").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"careId\":\"CID-7K3MQ9\",\"drugs\":[{\"drug\":\"Aspirin\",\"dose\":\"75 mg\"}]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("NO_ACTIVE_GRANT"));
    }

    @Test
    void modifyReturnsTheNewVersionWithItsHistory() throws Exception {
        when(prescriptions.modify(eq("rx1"), any())).thenReturn(view(2, PrescriptionStatus.ACTIVE,
                List.of(version(1, ChangeType.ISSUED, null), version(2, ChangeType.MODIFIED, "INR high"))));

        mvc.perform(put("/prescription/rx1").contentType(MediaType.APPLICATION_JSON).content("{\"dose\":\"2.5 mg\",\"reason\":\"INR high\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").value(2))
                .andExpect(jsonPath("$.versions[1].changeType").value("MODIFIED"))
                .andExpect(jsonPath("$.versions[1].reason").value("INR high"))
                .andExpect(jsonPath("$.versions[1].changedBy.name").value("Dr Test"))
                .andExpect(jsonPath("$.versions[1].changedBy.role").value("DOCTOR"))
                .andExpect(jsonPath("$.versions[1].changedAt").value("2026-10-23T10:00:00Z"));

        var captor = ArgumentCaptor.forClass(com.curo.curo_backend.dto.ModifyRequest.class);
        verify(prescriptions).modify(eq("rx1"), captor.capture());
        assertThat(captor.getValue().getDose()).isEqualTo("2.5 mg");
        assertThat(captor.getValue().getReason()).isEqualTo("INR high");
    }

    @Test
    void discontinueWorksWithABodyAndWithoutOne() throws Exception {
        when(prescriptions.discontinue(eq("rx1"), any())).thenReturn(view(2, PrescriptionStatus.DISCONTINUED, null));

        mvc.perform(post("/prescription/rx1/discontinue").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"done\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCONTINUED"));
        mvc.perform(post("/prescription/rx1/discontinue")).andExpect(status().isOk());
        verify(prescriptions).discontinue(eq("rx1"), isNull());
    }

    @Test
    void historyReturnsEveryVersion() throws Exception {
        when(prescriptions.history("rx1")).thenReturn(new HistoryResponse("rx1", "CID-7K3MQ9", "Warfarin", 2,
                List.of(version(1, ChangeType.ISSUED, null), version(2, ChangeType.MODIFIED, "INR high"))));

        mvc.perform(get("/prescription/rx1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prescriptionId").value("rx1"))
                .andExpect(jsonPath("$.careId").value("CID-7K3MQ9"))
                .andExpect(jsonPath("$.currentVersion").value(2))
                .andExpect(jsonPath("$.versions.length()").value(2))
                .andExpect(jsonPath("$.versions[0].changeType").value("ISSUED"));
    }

    @Test
    void anOptimisticLockConflictIs409() throws Exception {
        when(prescriptions.modify(eq("rx1"), any())).thenThrow(new org.springframework.dao.OptimisticLockingFailureException("x"));

        mvc.perform(put("/prescription/rx1").contentType(MediaType.APPLICATION_JSON).content("{\"dose\":\"1 mg\",\"reason\":\"r\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONCURRENT_MODIFICATION"));
    }

    // ---- medication profile

    private MedicationProfile profileWith(List<String> allergies) {
        return new MedicationProfile("CID-7K3MQ9", T0, List.of(new MedicationView("rx1", "Warfarin", "5 mg", Frequency.TWICE_DAILY,
                List.of("08:00", "20:00"), LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 29), PrescriptionStatus.ACTIVE, 1,
                new MedicationView.Prescriber("Dr Test", null))), allergies);
    }

    @Test
    void medicationProfileHasTheContractShape() throws Exception {
        when(medications.forPatient("CID-7K3MQ9")).thenReturn(profileWith(List.of("Penicillin")));

        mvc.perform(get("/patient/CID-7K3MQ9/medications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.careId").value("CID-7K3MQ9"))
                .andExpect(jsonPath("$.asOf").value("2026-10-23T10:00:00Z"))
                .andExpect(jsonPath("$.medications[0].prescriptionId").value("rx1"))
                .andExpect(jsonPath("$.medications[0].startDate").value("2026-10-23"))
                .andExpect(jsonPath("$.medications[0].endDate").value("2026-10-29"))
                .andExpect(jsonPath("$.medications[0].frequency").value("TWICE_DAILY"))
                .andExpect(jsonPath("$.medications[0].prescribedBy.name").value("Dr Test"))
                .andExpect(jsonPath("$.allergies[0]").value("Penicillin"));
    }

    @Test
    void allergiesAreLeftOutOfTheJsonWhenNotAllowed() throws Exception {
        when(medications.forPatient("CID-7K3MQ9")).thenReturn(profileWith(null));

        mvc.perform(get("/patient/CID-7K3MQ9/medications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allergies").doesNotExist());
    }

    @Test
    void theMeRouteIsNotMistakenForACareId() throws Exception {
        when(medications.mine()).thenReturn(profileWith(List.of()));

        mvc.perform(get("/patient/me/medications")).andExpect(status().isOk());
        verify(medications).mine();
        verifyNoInteractions(prescriptions);
    }

    // ---- patient profile and drug list

    @Test
    void profileCanBeReadAndItsAllergiesUpdated() throws Exception {
        when(profile.get()).thenReturn(new PatientProfileView("p1", "CID-7K3MQ9", "Test Patient", "9000000001", List.of(), false));
        when(profile.update(any())).thenReturn(new PatientProfileView("p1", "CID-7K3MQ9", "Test Patient", "9000000001", List.of("Penicillin"), false));

        mvc.perform(get("/patient/me")).andExpect(status().isOk()).andExpect(jsonPath("$.careId").value("CID-7K3MQ9"))
                .andExpect(jsonPath("$.allergies.length()").value(0)).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(put("/patient/me").contentType(MediaType.APPLICATION_JSON).content("{\"allergies\":[\"Penicillin\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allergies[0]").value("Penicillin"));
    }

    @Test
    void drugListIsSortedByNameAndHasTheContractFields() throws Exception {
        DrugMaster d = new DrugMaster();
        d.setId("dm1");
        d.setName("Warfarin");
        d.setNormalizedName("warfarin");
        d.setDrugClass("ANTICOAGULANT");
        d.setScheduleType("RX");
        when(drugRepo.findAll(any(Sort.class))).thenReturn(List.of(d));

        mvc.perform(get("/drugs/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("dm1"))
                .andExpect(jsonPath("$[0].name").value("Warfarin"))
                .andExpect(jsonPath("$[0].drugClass").value("ANTICOAGULANT"))
                .andExpect(jsonPath("$[0].scheduleType").value("RX"))
                .andExpect(jsonPath("$[0].normalizedName").doesNotExist())
                .andExpect(jsonPath("$[0].aliases").doesNotExist());
        var captor = ArgumentCaptor.forClass(Sort.class);
        verify(drugRepo).findAll(captor.capture());
        assertThat(captor.getValue().getOrderFor("name")).isNotNull();
    }
}
