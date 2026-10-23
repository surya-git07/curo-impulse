package com.curo.curo_backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

/** Preflight requests through the real security chain, with the default origins (cors.allowed-origins not set). */
@SpringBootTest
@AutoConfigureMockMvc
class CorsPreflightTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> UUID.randomUUID() + UUID.randomUUID().toString());
        registry.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/curo-test");
        registry.add("spring.data.mongodb.auto-index-creation", () -> "false");
        registry.add("licence.verification.seed-on-startup", () -> "false");
        registry.add("drugs.seed-on-startup", () -> "false");
        registry.add("pharmacies.seed-on-startup", () -> "false");
    }

    @Autowired
    private MockMvc mvc;

    private MockHttpServletResponse preflight(String origin, String method, String headers, String path) throws Exception {
        MockHttpServletRequestBuilder request = options(path)
                .header("Origin", origin)
                .header("Access-Control-Request-Method", method);
        if (headers != null) {
            request.header("Access-Control-Request-Headers", headers);
        }
        return mvc.perform(request).andReturn().getResponse();
    }

    @Test
    void aListedOriginGetsAFullPreflightAnswerWithoutLoggingIn() throws Exception {
        MockHttpServletResponse r = preflight("http://localhost:5173", "POST", "authorization,content-type", "/prescription/issue");

        assertThat(r.getStatus()).as("not 401/403: the browser has no token yet").isEqualTo(200);
        assertThat(r.getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
        assertThat(r.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(r.getHeader("Access-Control-Allow-Methods")).contains("POST");
        assertThat(r.getHeader("Access-Control-Allow-Headers").toLowerCase()).contains("authorization").contains("content-type");
        assertThat(r.getHeader("Access-Control-Max-Age")).isEqualTo("3600");
    }

    @Test
    void theSecondDefaultOriginIsAlsoAllowed() throws Exception {
        MockHttpServletResponse r = preflight("http://localhost:8081", "GET", "authorization", "/patient/me/doses/today");

        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(r.getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:8081");
    }

    @Test
    void everyAllowedMethodPassesPreflight() throws Exception {
        for (String method : new String[]{"GET", "POST", "PUT", "PATCH", "DELETE"}) {
            MockHttpServletResponse r = preflight("http://localhost:5173", method, "content-type", "/access-grants/g1");

            assertThat(r.getStatus()).as(method).isEqualTo(200);
            assertThat(r.getHeader("Access-Control-Allow-Methods")).as(method).contains(method);
        }
    }

    @Test
    void anUnlistedOriginIsRefused() throws Exception {
        for (String origin : new String[]{"https://evil.example.com", "http://localhost:3000", "http://localhost:5173.evil.com",
                "https://localhost:5173", "null"}) {
            MockHttpServletResponse r = preflight(origin, "POST", "authorization", "/prescription/issue");

            assertThat(r.getStatus()).as(origin).isEqualTo(403);
            assertThat(r.getHeader("Access-Control-Allow-Origin")).as(origin).isNull();
        }
    }

    @Test
    void aMethodOutsideTheListIsRefused() throws Exception {
        for (String method : new String[]{"TRACE", "CONNECT", "HEAD"}) {
            MockHttpServletResponse r = preflight("http://localhost:5173", method, null, "/prescription/issue");

            assertThat(r.getStatus()).as(method).isEqualTo(403);
            assertThat(r.getHeader("Access-Control-Allow-Origin")).as(method).isNull();
        }
    }

    @Test
    void aHeaderOutsideTheListIsNeverGranted() throws Exception {
        // asked for on its own: refused outright
        for (String header : new String[]{"x-internal-key", "x-custom", "cookie"}) {
            MockHttpServletResponse alone = preflight("http://localhost:5173", "POST", header, "/prescription/issue");
            assertThat(alone.getStatus()).as(header).isEqualTo(403);
            assertThat(alone.getHeader("Access-Control-Allow-Origin")).as(header).isNull();
        }

        // asked for alongside an allowed one: the answer grants only the allowed header, so the browser blocks the real request
        MockHttpServletResponse mixed = preflight("http://localhost:5173", "POST", "authorization,x-internal-key", "/prescription/issue");
        String granted = String.valueOf(mixed.getHeader("Access-Control-Allow-Headers")).toLowerCase();
        assertThat(mixed.getStatus() == 403 || !granted.contains("x-internal-key")).isTrue();
        assertThat(granted).doesNotContain("x-internal-key");
    }

    @Test
    void theAnswerNeverUsesAWildcardBecauseCredentialsAreAllowed() throws Exception {
        MockHttpServletResponse r = preflight("http://localhost:5173", "POST", "authorization,content-type", "/auth/login");

        assertThat(r.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(r.getHeader("Access-Control-Allow-Origin")).isNotEqualTo("*");
        assertThat(r.getHeader("Access-Control-Allow-Headers")).doesNotContain("*");
        assertThat(r.getHeader("Access-Control-Allow-Methods")).doesNotContain("*");
    }

    @Test
    void aPreflightDoesNotNeedTheEndpointToExist() throws Exception {
        MockHttpServletResponse r = preflight("http://localhost:5173", "POST", "content-type", "/auth/signup");

        assertThat(r.getStatus()).isEqualTo(200);
    }

    @Test
    void aRefusedRealRequestFromAListedOriginStillCarriesTheCorsHeader() throws Exception {
        // so the browser shows the real 401/403 instead of a confusing network error
        MockHttpServletResponse r = mvc.perform(get("/patient/me/access-log").header("Origin", "http://localhost:5173"))
                .andReturn().getResponse();

        assertThat(r.getStatus()).isIn(401, 403);
        assertThat(r.getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
    }

    @Test
    void aRealRequestFromAnUnlistedOriginGetsNoCorsHeader() throws Exception {
        MockHttpServletResponse r = mvc.perform(get("/patient/me/access-log").header("Origin", "https://evil.example.com"))
                .andReturn().getResponse();

        assertThat(r.getHeader("Access-Control-Allow-Origin")).isNull();
    }
}
