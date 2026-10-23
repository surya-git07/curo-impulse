package com.curo.curo_backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

/** The origins come from CORS_ALLOWED_ORIGINS (cors.allowed-origins), not from the code. */
@SpringBootTest
@AutoConfigureMockMvc
class CorsConfiguredOriginsTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> UUID.randomUUID() + UUID.randomUUID().toString());
        registry.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/curo-test");
        registry.add("spring.data.mongodb.auto-index-creation", () -> "false");
        registry.add("licence.verification.seed-on-startup", () -> "false");
        registry.add("drugs.seed-on-startup", () -> "false");
        registry.add("cors.allowed-origins", () -> " https://app.example.com , https://admin.example.com ,, ");
    }

    @Autowired
    private MockMvc mvc;

    private MockHttpServletResponse preflight(String origin) throws Exception {
        return mvc.perform(options("/prescription/issue").header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")).andReturn().getResponse();
    }

    @Test
    void onlyTheConfiguredOriginsAreAllowedAndTheDefaultsAreGone() throws Exception {
        assertThat(preflight("https://app.example.com").getHeader("Access-Control-Allow-Origin")).isEqualTo("https://app.example.com");
        assertThat(preflight("https://admin.example.com").getStatus()).isEqualTo(200);
        assertThat(preflight("http://localhost:5173").getStatus()).isEqualTo(403);
        assertThat(preflight("http://localhost:8081").getStatus()).isEqualTo(403);
    }

    // ---- parsing

    @Test
    void parsingTrimsAndSkipsBlanks() {
        assertThat(SecurityConfig.parseOrigins(" https://a.com , ,https://b.com,")).containsExactly("https://a.com", "https://b.com");
        assertThat(SecurityConfig.parseOrigins("http://localhost:5173,http://localhost:8081"))
                .containsExactly("http://localhost:5173", "http://localhost:8081");
    }

    @Test
    void nothingConfiguredMeansNoOriginIsAllowed() {
        assertThat(SecurityConfig.parseOrigins("")).isEmpty();
        assertThat(SecurityConfig.parseOrigins("   ")).isEmpty();
        assertThat(SecurityConfig.parseOrigins(null)).isEmpty();
    }

    @Test
    void wildcardsAreRefusedBecauseCredentialsAreAllowed() {
        for (String bad : new String[]{"*", "https://a.com,*", "https://*.example.com", " * "}) {
            assertThatThrownBy(() -> SecurityConfig.parseOrigins(bad)).as(bad)
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("wildcards");
        }
    }
}
