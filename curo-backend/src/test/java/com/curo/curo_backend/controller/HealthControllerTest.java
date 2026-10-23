package com.curo.curo_backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Full context so the real security filter chains are exercised
@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> UUID.randomUUID() + UUID.randomUUID().toString());
        registry.add("spring.mongodb.uri", () -> "mongodb://localhost:27017/curo-test");
        registry.add("spring.data.mongodb.auto-index-creation", () -> "false");
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void healthIsPublicAndReturnsUp() throws Exception {
        mvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", true))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void otherPathsStillRequireAuthentication() throws Exception {
        int status = mvc.perform(get("/some-protected-path")).andReturn().getResponse().getStatus();
        assertThat(status).isIn(401, 403);
    }

    @Test
    void healthDoesNotOpenSiblingPaths() throws Exception {
        int status = mvc.perform(get("/health/details")).andReturn().getResponse().getStatus();
        assertThat(status).isIn(401, 403);
    }
}
