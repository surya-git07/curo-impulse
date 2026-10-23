package com.curo.curo_backend.security;

import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** A RECEPTION account is a front-desk login only: it cannot prescribe, dispense, request access or read patient data. */
@SpringBootTest
@AutoConfigureMockMvc
class ReceptionAccessSecurityTest {

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

    private int asReception(MockHttpServletRequestBuilder request) throws Exception {
        AppUser user = new AppUser();
        user.setId("r1");
        user.setRole("RECEPTION");
        user.setCareId("CID-RRRRR2");
        when(userRepository.findById("r1")).thenReturn(Optional.of(user));
        String token = jwtService.generateToken("r1", "RECEPTION", "CID-RRRRR2", null);
        return mvc.perform(request.header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
    }

    @Test
    void cannotPrescribeOrDispense() throws Exception {
        assertThat(asReception(post("/prescription/issue").contentType("application/json").content("{}"))).isEqualTo(403);
        assertThat(asReception(put("/prescription/rx1").contentType("application/json").content("{}"))).isEqualTo(403);
        assertThat(asReception(post("/prescription/rx1/discontinue"))).isEqualTo(403);
        assertThat(asReception(get("/prescription/rx1/history"))).isEqualTo(403);
        assertThat(asReception(post("/pharmacy/dispense").contentType("application/json").content("{}"))).isEqualTo(403);
    }

    @Test
    void cannotRequestAccessOrReadPatientData() throws Exception {
        assertThat(asReception(post("/access-grants").contentType("application/json").content("{\"careId\":\"CID-7K3MQ9\"}"))).isEqualTo(403);
        assertThat(asReception(get("/careid/CID-7K3MQ9"))).isEqualTo(403);
        assertThat(asReception(get("/patient/CID-7K3MQ9/medications"))).isEqualTo(403);
        assertThat(asReception(get("/patient/CID-7K3MQ9/dose-schedule"))).isEqualTo(403);
        assertThat(asReception(get("/patient/me"))).isEqualTo(403);
        assertThat(asReception(get("/patient/me/medications"))).isEqualTo(403);
        assertThat(asReception(get("/patient/me/access-log"))).isEqualTo(403);
        assertThat(asReception(get("/drugs/list"))).isEqualTo(403);
    }
}
