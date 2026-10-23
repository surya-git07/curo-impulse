package com.curo.curo_backend.controller;

import com.curo.curo_backend.service.DoseScheduleService;
import com.curo.curo_backend.service.InternalKeyVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SchedulerControllerTest {

    private final String key = UUID.randomUUID().toString(); // made up per run, not a real secret
    private DoseScheduleService doses;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        doses = mock(DoseScheduleService.class);
        when(doses.markMissed()).thenReturn(3L);
        InternalKeyVerifier verifier = new InternalKeyVerifier();
        ReflectionTestUtils.setField(verifier, "configuredKey", key);
        SchedulerController controller = new SchedulerController();
        ReflectionTestUtils.setField(controller, "keyVerifier", verifier);
        ReflectionTestUtils.setField(controller, "doseScheduleService", doses);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void theRightKeyRunsTheJobAndReportsTheCount() throws Exception {
        mvc.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.markedMissed").value(3))
                .andExpect(jsonPath("$.remindersSent").value(0))
                .andExpect(jsonPath("$.alertsCreated").value(0));
        verify(doses).markMissed();
    }

    @Test
    void aMissingOrWrongKeyIs401AndRunsNothing() throws Exception {
        mvc.perform(post("/internal/scheduler/dose-tick")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_KEY"));
        mvc.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", key + "x")).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", key.toUpperCase())).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", "")).andExpect(status().isUnauthorized());
        verifyNoInteractions(doses);
    }

    @Test
    void theKeyIsNeverEchoedBack() throws Exception {
        String body = mvc.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", "guess-123"))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("guess-123").doesNotContain(key);
    }

    @Test
    void ifNoKeyIsConfiguredEveryCallIsRefused() throws Exception {
        InternalKeyVerifier unconfigured = new InternalKeyVerifier();
        ReflectionTestUtils.setField(unconfigured, "configuredKey", "");
        SchedulerController controller = new SchedulerController();
        ReflectionTestUtils.setField(controller, "keyVerifier", unconfigured);
        ReflectionTestUtils.setField(controller, "doseScheduleService", doses);
        MockMvc open = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();

        open.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", "")).andExpect(status().isUnauthorized());
        open.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", "anything")).andExpect(status().isUnauthorized());
        open.perform(post("/internal/scheduler/dose-tick")).andExpect(status().isUnauthorized());
        verifyNoInteractions(doses);
    }

    @Test
    void theTaskCanBeChosen() throws Exception {
        mvc.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"task\":\"MISSED\"}")).andExpect(status().isOk());
        mvc.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"task\":\"all\"}")).andExpect(status().isOk());
        verify(doses, times(2)).markMissed();
    }

    @Test
    void remindersAloneDoNotMarkAnythingMissed() throws Exception {
        mvc.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"task\":\"REMINDERS\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.markedMissed").value(0));
        verify(doses, never()).markMissed();
    }

    @Test
    void anUnknownTaskIs400() throws Exception {
        mvc.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"task\":\"DELETE_EVERYTHING\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        verifyNoInteractions(doses);
    }

    @Test
    void onlyPostIsAccepted() throws Exception {
        mvc.perform(get("/internal/scheduler/dose-tick").header("X-Internal-Key", key)).andExpect(status().isMethodNotAllowed());
    }
}
