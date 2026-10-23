package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.LoginResponse;
import com.curo.curo_backend.dto.SignupResponse;
import com.curo.curo_backend.dto.UserSummary;
import com.curo.curo_backend.model.Consent;
import com.curo.curo_backend.service.ApiException;
import com.curo.curo_backend.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private AuthService authService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "authService", authService);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    private static final String SIGNUP_BODY = """
            {"role":"PATIENT","name":"Test Patient","phone":"9000000001","password":"correct-horse-battery",
             "consent":{"accepted":true,"policyVersion":"2026-10-01","acceptedAt":"1999-01-01T00:00:00Z"}}
            """;

    @Test
    void signupReturns201WithCamelCaseFields() throws Exception {
        Consent consent = new Consent();
        consent.setAccepted(true);
        consent.setPolicyVersion("2026-10-01");
        consent.setAcceptedAt(Instant.parse("2026-10-23T10:15:00Z"));
        when(authService.signup(any())).thenReturn(
                new SignupResponse("665f0000000000000000a001", "CID-7K3MQ9", "Test Patient", "PATIENT", consent));

        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(SIGNUP_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.careId").value("CID-7K3MQ9"))
                .andExpect(jsonPath("$.id").value("665f0000000000000000a001"))
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.consent.accepted").value(true))
                .andExpect(jsonPath("$.consent.policyVersion").value("2026-10-01"))
                .andExpect(jsonPath("$.consent.acceptedAt").value("2026-10-23T10:15:00Z"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void apiExceptionsBecomeErrorAndMessageBodies() throws Exception {
        when(authService.signup(any())).thenThrow(
                new ApiException(HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED", "You must accept the privacy policy."));

        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(SIGNUP_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CONSENT_REQUIRED"))
                .andExpect(jsonPath("$.message").value("You must accept the privacy policy."));
    }

    @Test
    void beanValidationFailuresNeverEchoTheRejectedValue() throws Exception {
        String body = """
                {"role":"PATIENT","name":"","phone":"not-a-phone","password":"hunter2-secret","consent":{"accepted":true,"policyVersion":"v1"}}
                """;

        mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hunter2"))))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("phone")));
        verifyNoInteractions(authService);
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_JSON"));
    }

    @Test
    void loginReturnsTokenExpiresAtAndUser() throws Exception {
        when(authService.login(any())).thenReturn(new LoginResponse("jwt-token", Instant.parse("2026-10-23T22:15:00Z"),
                new UserSummary("665f0000000000000000a001", "CID-7K3MQ9", "Test Patient", "PATIENT", null, null, false)));

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"CID-7K3MQ9\",\"password\":\"correct-horse-battery\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-23T22:15:00Z"))
                .andExpect(jsonPath("$.user.careId").value("CID-7K3MQ9"))
                .andExpect(jsonPath("$.user.profileCompleted").value(false))
                .andExpect(jsonPath("$.user.facilityId").doesNotExist());
    }

    @Test
    void loginWithBlankFieldsIs400() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"identifier\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
