package com.curo.curo_backend.security;

import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.Consent;
import com.curo.curo_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** GET /auth/me must need a valid token and must actually exist; signup and login stay public. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthMeSecurityTest {

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

    private AppUser patient() {
        AppUser u = new AppUser();
        u.setId("u1");
        u.setCareId("CID-7K3MQ9");
        u.setName("Test Patient");
        u.setRole("PATIENT");
        Consent c = new Consent();
        c.setAccepted(true);
        c.setPolicyVersion("2026-10-01");
        c.setAcceptedAt(Instant.parse("2026-10-23T10:00:00Z"));
        u.setConsent(c);
        return u;
    }

    @Test
    void meWithAValidTokenReturnsTheUserAndConsent() throws Exception {
        when(userRepository.findById("u1")).thenReturn(Optional.of(patient()));
        String token = jwtService.generateToken("u1", "PATIENT", "CID-7K3MQ9", null);

        var res = mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token)).andReturn().getResponse();

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentAsString())
                .contains("\"careId\":\"CID-7K3MQ9\"", "\"role\":\"PATIENT\"", "\"accepted\":true", "\"policyVersion\":\"2026-10-01\"")
                .doesNotContain("passwordHash", "verificationStatus");
    }

    @Test
    void meWithoutATokenIsRefused() throws Exception {
        assertThat(mvc.perform(get("/auth/me")).andReturn().getResponse().getStatus()).isIn(401, 403);
    }

    @Test
    void meWithAGarbageTokenIsRefused() throws Exception {
        assertThat(mvc.perform(get("/auth/me").header("Authorization", "Bearer nonsense")).andReturn().getResponse().getStatus())
                .isIn(401, 403);
    }

    @Test
    void signupAndLoginStayPublic() throws Exception {
        // an empty body is a validation failure (400), which proves the request got past the login check
        assertThat(mvc.perform(post("/auth/login").contentType("application/json").content("{}")).andReturn().getResponse().getStatus())
                .isEqualTo(400);
        assertThat(mvc.perform(post("/auth/signup").contentType("application/json").content("{}")).andReturn().getResponse().getStatus())
                .isEqualTo(400);
    }

    @Test
    void otherAuthPathsAreNotPublic() throws Exception {
        assertThat(mvc.perform(get("/auth/anything")).andReturn().getResponse().getStatus()).isIn(401, 403);
    }
}
