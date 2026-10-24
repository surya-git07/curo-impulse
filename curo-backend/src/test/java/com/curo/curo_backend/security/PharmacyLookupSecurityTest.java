package com.curo.curo_backend.security;

import com.curo.curo_backend.dto.ActivePrescriptionsResponse;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.repository.UserRepository;
import com.curo.curo_backend.service.PharmacyLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** GET /pharmacy/patients/{careId}/prescriptions/active through the real filter chain: PHARMACY only. */
@SpringBootTest
@AutoConfigureMockMvc
class PharmacyLookupSecurityTest {

    private static final String PATH = "/pharmacy/patients/CID-7K3MQ9/prescriptions/active";

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

    // the service rules are covered by PharmacyLookupServiceTest; this test is about who gets through the route
    @MockitoBean
    private PharmacyLookupService lookupService;

    @BeforeEach
    void setUp() {
        for (String role : new String[]{"PHARMACY", "DOCTOR", "HOSPITAL", "RECEPTION", "PATIENT", "CAREGIVER", "ADMIN"}) {
            AppUser u = new AppUser();
            u.setId("u-" + role);
            u.setRole(role);
            when(userRepository.findById("u-" + role)).thenReturn(Optional.of(u));
        }
        when(lookupService.activePrescriptions(anyString())).thenReturn(new ActivePrescriptionsResponse("CID-7K3MQ9", List.of()));
    }

    private int as(String role) throws Exception {
        String token = jwtService.generateToken("u-" + role, role, "CID-X2222" + role.charAt(0), null);
        return mvc.perform(get(PATH).header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
    }

    @Test
    void aPharmacyGetsThroughTheRoute() throws Exception {
        assertThat(as("PHARMACY")).isEqualTo(200);
    }

    @Test
    void everyOtherRoleGetsA403() throws Exception {
        for (String role : new String[]{"DOCTOR", "HOSPITAL", "RECEPTION", "PATIENT", "CAREGIVER"}) {
            assertThat(as(role)).as(role).isEqualTo(403);
        }
    }

    @Test
    void noTokenIsRefused() throws Exception {
        assertThat(mvc.perform(get(PATH)).andReturn().getResponse().getStatus()).isIn(401, 403);
    }
}
