package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.AlertView;
import com.curo.curo_backend.dto.CaregiverLinkRequest;
import com.curo.curo_backend.dto.CaregiverLinkView;
import com.curo.curo_backend.dto.DoseView;
import com.curo.curo_backend.dto.PagedResponse;
import com.curo.curo_backend.model.AlertStatus;
import com.curo.curo_backend.model.AlertType;
import com.curo.curo_backend.model.DoseStatus;
import com.curo.curo_backend.model.LinkStatus;
import com.curo.curo_backend.service.AlertService;
import com.curo.curo_backend.service.ApiException;
import com.curo.curo_backend.service.CaregiverLinkService;
import com.curo.curo_backend.service.DoseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AlertControllersTest {

    private static final Instant T0 = Instant.parse("2026-10-23T10:00:00Z");

    private AlertService alerts;
    private CaregiverLinkService links;
    private DoseService doses;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        alerts = mock(AlertService.class);
        links = mock(CaregiverLinkService.class);
        doses = mock(DoseService.class);
        AlertController a = new AlertController();
        ReflectionTestUtils.setField(a, "alertService", alerts);
        CaregiverLinkController c = new CaregiverLinkController();
        ReflectionTestUtils.setField(c, "caregiverLinkService", links);
        DoseController d = new DoseController();
        ReflectionTestUtils.setField(d, "doseService", doses);
        mvc = MockMvcBuilders.standaloneSetup(a, c, d).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    private AlertView alert(boolean read) {
        return new AlertView("a1", AlertType.MISSED_DOSE, "CID-7K3MQ9", "You missed your Warfarin 5 mg dose scheduled at 08:00.", "dose1", "rx1",
                "EMAIL", AlertStatus.SENT, T0, read ? T0.plusSeconds(60) : null);
    }

    // ---- alerts

    @Test
    void alertsHaveTheContractShapeAndPagedWrapper() throws Exception {
        when(alerts.list(false, 0, 20)).thenReturn(new PagedResponse<>(0, 20, 1, List.of(alert(false))));

        mvc.perform(get("/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value("a1"))
                .andExpect(jsonPath("$.items[0].type").value("MISSED_DOSE"))
                .andExpect(jsonPath("$.items[0].patientCareId").value("CID-7K3MQ9"))
                .andExpect(jsonPath("$.items[0].message").value("You missed your Warfarin 5 mg dose scheduled at 08:00."))
                .andExpect(jsonPath("$.items[0].doseId").value("dose1"))
                .andExpect(jsonPath("$.items[0].prescriptionId").value("rx1"))
                .andExpect(jsonPath("$.items[0].channel").value("EMAIL"))
                .andExpect(jsonPath("$.items[0].status").value("SENT"))
                .andExpect(jsonPath("$.items[0].sentAt").value("2026-10-23T10:00:00Z"))
                .andExpect(jsonPath("$.items[0].readAt").doesNotExist())
                .andExpect(jsonPath("$.items[0].recipientId").doesNotExist());
    }

    @Test
    void theUnreadFilterAndPagingArePassedThrough() throws Exception {
        when(alerts.list(true, 2, 5)).thenReturn(new PagedResponse<>(2, 5, 0, List.of()));

        mvc.perform(get("/alerts").param("unread", "true").param("page", "2").param("size", "5")).andExpect(status().isOk());
        verify(alerts).list(true, 2, 5);
    }

    @Test
    void markingReadReturnsTheAlertAndAnUnknownOneIs404() throws Exception {
        when(alerts.markRead("a1")).thenReturn(alert(true));
        when(alerts.markRead("nope")).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "ALERT_NOT_FOUND", "No such alert."));

        mvc.perform(post("/alerts/a1/read")).andExpect(status().isOk()).andExpect(jsonPath("$.readAt").value("2026-10-23T10:01:00Z"));
        mvc.perform(post("/alerts/nope/read")).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("ALERT_NOT_FOUND"));
    }

    // ---- caregiver links

    private CaregiverLinkView link(LinkStatus status) {
        return new CaregiverLinkView("link1", "CID-7K3MQ9", "Test Patient", "CID-3CC3CC", "Test Caregiver", "daughter", status, T0,
                status == LinkStatus.REVOKED ? T0.plusSeconds(60) : null);
    }

    @Test
    void addingACaregiverReturns201WithTheLink() throws Exception {
        when(links.add(any())).thenReturn(link(LinkStatus.ACTIVE));

        mvc.perform(post("/caregiver-links").contentType(MediaType.APPLICATION_JSON).content("{\"careId\":\"CID-3CC3CC\",\"relationship\":\"daughter\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("link1"))
                .andExpect(jsonPath("$.caregiverCareId").value("CID-3CC3CC"))
                .andExpect(jsonPath("$.caregiverName").value("Test Caregiver"))
                .andExpect(jsonPath("$.patientCareId").value("CID-7K3MQ9"))
                .andExpect(jsonPath("$.relationship").value("daughter"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.grantId").doesNotExist());
        var captor = ArgumentCaptor.forClass(CaregiverLinkRequest.class);
        verify(links).add(captor.capture());
        assertThat(captor.getValue().getCareId()).isEqualTo("CID-3CC3CC");
    }

    @Test
    void aMissingCareIdIs400AndNothingIsCalled() throws Exception {
        mvc.perform(post("/caregiver-links").contentType(MediaType.APPLICATION_JSON).content("{\"relationship\":\"daughter\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        verifyNoInteractions(links);
    }

    @Test
    void duplicatesAndUnknownCaregiversUseTheirCodes() throws Exception {
        when(links.add(any())).thenThrow(new ApiException(HttpStatus.CONFLICT, "LINK_EXISTS", "already"));
        mvc.perform(post("/caregiver-links").contentType(MediaType.APPLICATION_JSON).content("{\"careId\":\"CID-3CC3CC\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("LINK_EXISTS"));

        org.mockito.Mockito.doThrow(new ApiException(HttpStatus.NOT_FOUND, "CAREGIVER_NOT_FOUND", "none")).when(links).add(any());
        mvc.perform(post("/caregiver-links").contentType(MediaType.APPLICATION_JSON).content("{\"careId\":\"CID-ZZZZZZ\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("CAREGIVER_NOT_FOUND"));
    }

    @Test
    void linksCanBeListedAndRevoked() throws Exception {
        when(links.list()).thenReturn(List.of(link(LinkStatus.ACTIVE)));
        when(links.revoke("link1")).thenReturn(link(LinkStatus.REVOKED));

        mvc.perform(get("/caregiver-links")).andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("ACTIVE"));
        mvc.perform(post("/caregiver-links/link1/revoke")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED")).andExpect(jsonPath("$.revokedAt").value("2026-10-23T10:01:00Z"));
    }

    // ---- the caregiver's view of the schedule

    @Test
    void theScheduleOfAnotherPatientIsReadByCareIdWithOptionalDates() throws Exception {
        when(doses.scheduleFor(eq("CID-7K3MQ9"), any(), any())).thenReturn(List.of(new DoseView("dose1", "rx1", "Warfarin", "5 mg",
                Instant.parse("2026-10-23T14:30:00Z"), DoseStatus.UPCOMING, null, false, null)));

        mvc.perform(get("/patient/CID-7K3MQ9/dose-schedule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].drug").value("Warfarin"))
                .andExpect(jsonPath("$[0].status").value("UPCOMING"))
                .andExpect(jsonPath("$[0].scheduledAt").value("2026-10-23T14:30:00Z"));
        verify(doses).scheduleFor("CID-7K3MQ9", null, null);

        mvc.perform(get("/patient/CID-7K3MQ9/dose-schedule").param("from", "2026-10-23T00:00:00Z").param("to", "2026-10-30T00:00:00Z"))
                .andExpect(status().isOk());
        verify(doses).scheduleFor("CID-7K3MQ9", Instant.parse("2026-10-23T00:00:00Z"), Instant.parse("2026-10-30T00:00:00Z"));
    }

    @Test
    void withoutAGrantTheScheduleIs403() throws Exception {
        when(doses.scheduleFor(any(), isNull(), isNull())).thenThrow(new ApiException(HttpStatus.FORBIDDEN, "NO_ACTIVE_GRANT", "no grant"));

        mvc.perform(get("/patient/CID-7K3MQ9/dose-schedule")).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("NO_ACTIVE_GRANT"));
    }
}
