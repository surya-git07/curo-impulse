package com.curo.curo_backend.controller;

import com.curo.curo_backend.service.DoseAlertService;
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
    private DoseAlertService alerts;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        alerts = mock(DoseAlertService.class);
        when(alerts.sendReminders()).thenReturn(2L);
        when(alerts.processMissed()).thenReturn(new DoseAlertService.MissedResult(3, 7));
        InternalKeyVerifier verifier = new InternalKeyVerifier();
        ReflectionTestUtils.setField(verifier, "configuredKey", key);
        SchedulerController controller = new SchedulerController();
        ReflectionTestUtils.setField(controller, "keyVerifier", verifier);
        ReflectionTestUtils.setField(controller, "doseAlertService", alerts);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void theRightKeyRunsBothTasksAndReportsTheCounts() throws Exception {
        mvc.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remindersSent").value(2))
                .andExpect(jsonPath("$.markedMissed").value(3))
                .andExpect(jsonPath("$.alertsCreated").value(9)); // 2 reminders + 7 missed-dose and escalation alerts
        verify(alerts).sendReminders();
        verify(alerts).processMissed();
    }

    @Test
    void aMissingOrWrongKeyIs401AndRunsNothing() throws Exception {
        mvc.perform(post("/internal/scheduler/dose-tick")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_KEY"));
        mvc.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", key + "x")).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", key.toUpperCase())).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", "")).andExpect(status().isUnauthorized());
        verifyNoInteractions(alerts);
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
        ReflectionTestUtils.setField(controller, "doseAlertService", alerts);
        MockMvc open = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();

        open.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", "")).andExpect(status().isUnauthorized());
        open.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", "anything")).andExpect(status().isUnauthorized());
        open.perform(post("/internal/scheduler/dose-tick")).andExpect(status().isUnauthorized());
        verifyNoInteractions(alerts);
    }

    @Test
    void theMissedTaskAloneSendsNoReminders() throws Exception {
        mvc.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"task\":\"MISSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remindersSent").value(0))
                .andExpect(jsonPath("$.markedMissed").value(3))
                .andExpect(jsonPath("$.alertsCreated").value(7));
        verify(alerts, never()).sendReminders();
    }

    @Test
    void theRemindersTaskAloneMarksNothingMissed() throws Exception {
        mvc.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"task\":\"reminders\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remindersSent").value(2))
                .andExpect(jsonPath("$.markedMissed").value(0))
                .andExpect(jsonPath("$.alertsCreated").value(2));
        verify(alerts, never()).processMissed();
    }

    @Test
    void allIsTheDefaultAndCanBeSaidOutLoud() throws Exception {
        mvc.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{\"task\":\"all\"}")).andExpect(status().isOk());
        mvc.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        verify(alerts, times(2)).processMissed();
        verify(alerts, times(2)).sendReminders();
    }

    @Test
    void anUnknownTaskIs400() throws Exception {
        mvc.perform(post("/internal/scheduler/dose-tick").header("X-Internal-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"task\":\"DELETE_EVERYTHING\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        verifyNoInteractions(alerts);
    }

    @Test
    void onlyPostIsAccepted() throws Exception {
        mvc.perform(get("/internal/scheduler/dose-tick").header("X-Internal-Key", key)).andExpect(status().isMethodNotAllowed());
    }
}
