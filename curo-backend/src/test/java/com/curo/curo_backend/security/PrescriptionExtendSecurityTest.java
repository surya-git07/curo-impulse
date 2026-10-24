package com.curo.curo_backend.security;

import com.curo.curo_backend.dto.DoctorActivePrescriptionsResponse;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.repository.UserRepository;
import com.curo.curo_backend.service.PrescriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/** The two Extend routes through the real filter chain: DOCTOR only. The service rules (grant, dates) are in PrescriptionExtendTest. */
@SpringBootTest
@AutoConfigureMockMvc
class PrescriptionExtendSecurityTest {

    private static final String LIST = "/doctor/patients/CID-7K3MQ9/prescriptions/active";
    private static final String EXTEND = "/prescriptions/rx1/extend";

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
    private PrescriptionService prescriptionService;

    @BeforeEach
    void setUp() {
        for (String role : new String[]{"DOCTOR", "PHARMACY", "HOSPITAL", "RECEPTION", "PATIENT", "CAREGIVER", "ADMIN"}) {
            AppUser u = new AppUser();
            u.setId("u-" + role);
            u.setRole(role);
            when(userRepository.findById("u-" + role)).thenReturn(Optional.of(u));
        }
        when(prescriptionService.activeForDoctor(anyString())).thenReturn(new DoctorActivePrescriptionsResponse("CID-7K3MQ9", List.of()));
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String role) {
        String token = jwtService.generateToken("u-" + role, role, "CID-X2222" + role.charAt(0), null);
        return request.header("Authorization", "Bearer " + token);
    }

    private int list(String role) throws Exception {
        return mvc.perform(withToken(get(LIST), role)).andReturn().getResponse().getStatus();
    }

    private int extend(String role) throws Exception {
        return mvc.perform(withToken(patch(EXTEND).contentType("application/json").content("{\"extraDays\":3}"), role))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void aDoctorGetsThroughBothRoutes() throws Exception {
        assertThat(list("DOCTOR")).isEqualTo(200);
        assertThat(extend("DOCTOR")).isEqualTo(200);
    }

    @Test
    void everyOtherRoleGetsA403OnBothRoutes() throws Exception {
        for (String role : new String[]{"PHARMACY", "HOSPITAL", "RECEPTION", "PATIENT", "CAREGIVER", "ADMIN"}) {
            assertThat(list(role)).as("list " + role).isEqualTo(403);
            assertThat(extend(role)).as("extend " + role).isEqualTo(403);
        }
    }

    @Test
    void noTokenIsRefused() throws Exception {
        assertThat(mvc.perform(get(LIST)).andReturn().getResponse().getStatus()).isIn(401, 403);
        assertThat(mvc.perform(patch(EXTEND).contentType("application/json").content("{\"extraDays\":3}")).andReturn().getResponse().getStatus())
                .isIn(401, 403);
    }
}
