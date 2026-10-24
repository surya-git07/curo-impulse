package com.curo.curo_backend.security;

import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.repository.AccessGrantRepository;
import com.curo.curo_backend.repository.AuditLogRepository;
import com.curo.curo_backend.repository.PrescriptionRepository;
import com.curo.curo_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * A pharmacy may hand a medicine over without a patient grant, but that opens nothing else: adherence, history, allergies and
 * profile details stay behind the patient role or an active grant. Databases are mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PharmacyAccessBoundarySecurityTest {

    private static final String CARE_ID = "CID-7K3MQ9";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> UUID.randomUUID() + UUID.randomUUID().toString());
        registry.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/curo-test");
        registry.add("spring.data.mongodb.auto-index-creation", () -> "false");
        registry.add("licence.verification.seed-on-startup", () -> "false");
        registry.add("drugs.seed-on-startup", () -> "false");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AccessGrantRepository grantRepository;

    @MockitoBean
    private AuditLogRepository auditLogRepository;

    @MockitoBean
    private PrescriptionRepository prescriptionRepository;

    @BeforeEach
    void setUp() {
        for (String role : new String[]{"PHARMACY", "DOCTOR", "HOSPITAL", "RECEPTION", "PATIENT", "CAREGIVER"}) {
            AppUser u = new AppUser();
            u.setId("u-" + role);
            u.setRole(role);
            u.setName("Test " + role);
            when(userRepository.findById("u-" + role)).thenReturn(Optional.of(u));
        }
        AppUser patient = new AppUser();
        patient.setId("p1");
        patient.setRole("PATIENT");
        patient.setCareId(CARE_ID);
        patient.setName("Test Patient");
        patient.setAllergies(java.util.List.of("Penicillin"));
        when(userRepository.findByCareId(CARE_ID)).thenReturn(Optional.of(patient));
        Prescription rx = new Prescription();
        rx.setId("rx1");
        rx.setCareId(CARE_ID);
        rx.setDrug("Amoxicillin");
        when(prescriptionRepository.findById("rx1")).thenReturn(Optional.of(rx));
        // the grant repository returns no grants for anybody
    }

    private MockHttpServletResponse as(String role, MockHttpServletRequestBuilder request) throws Exception {
        String token = jwtService.generateToken("u-" + role, role, "CID-X2222" + role.charAt(0), null);
        return mvc.perform(request.header("Authorization", "Bearer " + token)).andReturn().getResponse();
    }

    @Test
    void aPharmacyCannotReadAdherenceOrDoseHistory() throws Exception {
        for (String path : new String[]{"/patient/me/dose-log", "/patient/me/doses/today", "/patient/" + CARE_ID + "/dose-schedule"}) {
            assertThat(as("PHARMACY", get(path)).getStatus()).as(path).isEqualTo(403);
        }
    }

    @Test
    void aPharmacyCannotReadTheProfileAllergiesOrSummary() throws Exception {
        for (String path : new String[]{"/patient/me", "/patient/me/medications", "/patient/me/access-log", "/patient/" + CARE_ID + "/summary"}) {
            MockHttpServletResponse res = as("PHARMACY", get(path));
            assertThat(res.getStatus()).as(path).isEqualTo(403);
            assertThat(res.getContentAsString()).as(path).doesNotContain("Penicillin");
        }
    }

    @Test
    void medicinesProfileAndPrescriptionHistoryStillNeedAnActiveGrant() throws Exception {
        for (String path : new String[]{"/patient/" + CARE_ID + "/medications", "/careid/" + CARE_ID, "/prescription/rx1/history"}) {
            MockHttpServletResponse res = as("PHARMACY", get(path));
            assertThat(res.getStatus()).as(path).isEqualTo(403);
            assertThat(res.getContentAsString()).as(path).contains("NO_ACTIVE_GRANT").doesNotContain("Penicillin").doesNotContain("Amoxicillin");
        }
    }

    @Test
    void onlyAPharmacyAccountReachesTheDispenseRoute() throws Exception {
        for (String role : new String[]{"DOCTOR", "HOSPITAL", "RECEPTION", "PATIENT", "CAREGIVER"}) {
            assertThat(as(role, post("/pharmacy/dispense").contentType("application/json").content("{\"prescriptionId\":\"rx1\",\"quantity\":1}"))
                    .getStatus()).as(role).isEqualTo(403);
        }
        assertThat(mvc.perform(post("/pharmacy/dispense").contentType("application/json").content("{}")).andReturn().getResponse().getStatus())
                .isIn(401, 403);
    }
}
