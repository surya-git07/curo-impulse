package com.curo.curo_backend.security;

import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.repository.AccessGrantRepository;
import com.curo.curo_backend.repository.AuditLogRepository;
import com.curo.curo_backend.repository.PrescriptionRepository;
import com.curo.curo_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** GET /patient/{careId}/summary through the real filter chain: doctors only, and no grant means no profile fields. */
@SpringBootTest
@AutoConfigureMockMvc
class PatientSummarySecurityTest {

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
    void users() {
        for (String role : new String[]{"DOCTOR", "PATIENT", "PHARMACY", "RECEPTION", "HOSPITAL"}) {
            AppUser u = new AppUser();
            u.setId("u-" + role);
            u.setRole(role);
            u.setName("Test " + role);
            when(userRepository.findById("u-" + role)).thenReturn(Optional.of(u));
        }
        AppUser patient = new AppUser();
        patient.setId("p1");
        patient.setRole("PATIENT");
        patient.setCareId("CID-7K3MQ9");
        patient.setName("Test Patient");
        patient.setBloodGroup("B+");
        patient.setDateOfBirth(LocalDate.of(1980, 5, 14));
        patient.setChronicConditions(java.util.List.of("Type 2 diabetes"));
        when(userRepository.findByCareId("CID-7K3MQ9")).thenReturn(Optional.of(patient));
        when(userRepository.findById("p1")).thenReturn(Optional.of(patient));
    }

    private org.springframework.mock.web.MockHttpServletResponse call(String role) throws Exception {
        var request = get("/patient/CID-7K3MQ9/summary");
        if (role != null) {
            request.header("Authorization", "Bearer " + jwtService.generateToken("u-" + role, role, "CID-X2222" + role.charAt(0), null));
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    @Test
    void aDoctorWithoutAGrantGetsNoActiveGrantAndNoProfileFields() throws Exception {
        var res = call("DOCTOR");
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("NO_ACTIVE_GRANT")
                .doesNotContain("B+").doesNotContain("bloodGroup").doesNotContain("Type 2").doesNotContain("Test Patient");
        verifyNoInteractions(prescriptionRepository);
    }

    @Test
    void everyOtherRoleAndNoTokenIsRefused() throws Exception {
        for (String role : new String[]{"PATIENT", "PHARMACY", "RECEPTION", "HOSPITAL"}) {
            var res = call(role);
            assertThat(res.getStatus()).as(role).isEqualTo(403);
            assertThat(res.getContentAsString()).as(role).doesNotContain("bloodGroup");
        }
        assertThat(call(null).getStatus()).isIn(401, 403);
    }
}
