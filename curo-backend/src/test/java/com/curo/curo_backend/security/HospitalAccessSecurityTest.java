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

/** A HOSPITAL account is a facility login only: it cannot prescribe, dispense, request access or read patient data. */
@SpringBootTest
@AutoConfigureMockMvc
class HospitalAccessSecurityTest {

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

    private int hospitalGets(MockHttpServletRequestBuilder request) throws Exception {
        AppUser hospital = new AppUser();
        hospital.setId("h1");
        hospital.setRole("HOSPITAL");
        hospital.setCareId("CID-7K3MQ9");
        when(userRepository.findById("h1")).thenReturn(Optional.of(hospital));
        String token = jwtService.generateToken("h1", "HOSPITAL", "CID-7K3MQ9", "f1");
        return mvc.perform(request.header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
    }

    @Test
    void aHospitalCannotPrescribeOrDispense() throws Exception {
        assertThat(hospitalGets(post("/prescription/issue").contentType("application/json").content("{}"))).isEqualTo(403);
        assertThat(hospitalGets(put("/prescription/rx1").contentType("application/json").content("{}"))).isEqualTo(403);
        assertThat(hospitalGets(post("/prescription/rx1/discontinue"))).isEqualTo(403);
        assertThat(hospitalGets(post("/pharmacy/dispense").contentType("application/json").content("{}"))).isEqualTo(403);
    }

    @Test
    void aHospitalCannotRequestAccessOrReadPatientData() throws Exception {
        assertThat(hospitalGets(post("/access-grants").contentType("application/json").content("{\"careId\":\"CID-7K3MQ9\"}"))).isEqualTo(403);
        assertThat(hospitalGets(get("/careid/CID-7K3MQ9"))).isEqualTo(403);
        assertThat(hospitalGets(get("/patient/CID-7K3MQ9/medications"))).isEqualTo(403);
        assertThat(hospitalGets(get("/patient/CID-7K3MQ9/dose-schedule"))).isEqualTo(403);
        assertThat(hospitalGets(get("/patient/me"))).isEqualTo(403);
        assertThat(hospitalGets(get("/patient/me/medications"))).isEqualTo(403);
        assertThat(hospitalGets(get("/drugs/list"))).isEqualTo(403);
    }
}
