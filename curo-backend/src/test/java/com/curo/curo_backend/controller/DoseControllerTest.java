package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.DoseView;
import com.curo.curo_backend.dto.PagedResponse;
import com.curo.curo_backend.model.DoseStatus;
import com.curo.curo_backend.service.ApiException;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DoseControllerTest {

    private DoseService doses;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        doses = mock(DoseService.class);
        DoseController controller = new DoseController();
        ReflectionTestUtils.setField(controller, "doseService", doses);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    private DoseView view(DoseStatus status, Instant takenAt, boolean late) {
        return new DoseView("d1", "rx1", "Amoxicillin", "500 mg", Instant.parse("2026-10-23T14:30:00Z"), status, takenAt, late,
                takenAt == null ? null : new DoseView.MarkedBy("p1", "PATIENT"));
    }

    @Test
    void todayHasTheContractFields() throws Exception {
        when(doses.today()).thenReturn(List.of(view(DoseStatus.UPCOMING, null, false)));

        mvc.perform(get("/patient/me/doses/today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("d1"))
                .andExpect(jsonPath("$[0].prescriptionId").value("rx1"))
                .andExpect(jsonPath("$[0].drug").value("Amoxicillin"))
                .andExpect(jsonPath("$[0].dose").value("500 mg"))
                .andExpect(jsonPath("$[0].scheduledAt").value("2026-10-23T14:30:00Z"))
                .andExpect(jsonPath("$[0].status").value("UPCOMING"))
                .andExpect(jsonPath("$[0].late").value(false))
                .andExpect(jsonPath("$[0].takenAt").doesNotExist())
                .andExpect(jsonPath("$[0].markedBy").doesNotExist());
    }

    @Test
    void takenReturnsTheUpdatedDose() throws Exception {
        when(doses.markTaken(eq("d1"), any())).thenReturn(view(DoseStatus.TAKEN, Instant.parse("2026-10-23T14:35:00Z"), false));

        mvc.perform(post("/doses/d1/taken").contentType(MediaType.APPLICATION_JSON).content("{\"takenAt\":\"2026-10-23T14:35:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TAKEN"))
                .andExpect(jsonPath("$.takenAt").value("2026-10-23T14:35:00Z"))
                .andExpect(jsonPath("$.markedBy.userId").value("p1"))
                .andExpect(jsonPath("$.markedBy.role").value("PATIENT"));
        var captor = ArgumentCaptor.forClass(com.curo.curo_backend.dto.TakenRequest.class);
        verify(doses).markTaken(eq("d1"), captor.capture());
        assertThat(captor.getValue().getTakenAt()).isEqualTo(Instant.parse("2026-10-23T14:35:00Z"));
    }

    @Test
    void takenWorksWithNoBody() throws Exception {
        when(doses.markTaken(eq("d1"), isNull())).thenReturn(view(DoseStatus.TAKEN, Instant.parse("2026-10-23T14:35:00Z"), false));

        mvc.perform(post("/doses/d1/taken")).andExpect(status().isOk());
    }

    @Test
    void aStatusInTheBodyIsIgnored() throws Exception {
        when(doses.markTaken(eq("d1"), any())).thenReturn(view(DoseStatus.TAKEN, Instant.parse("2026-10-23T14:35:00Z"), false));

        mvc.perform(post("/doses/d1/taken").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"MISSED\",\"late\":true}"))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(com.curo.curo_backend.dto.TakenRequest.class);
        verify(doses).markTaken(eq("d1"), captor.capture());
        assertThat(captor.getValue().getTakenAt()).isNull();
    }

    @Test
    void alreadyTakenIs409AndUnknownIs404() throws Exception {
        when(doses.markTaken(eq("d1"), any())).thenThrow(new ApiException(HttpStatus.CONFLICT, "DOSE_ALREADY_TAKEN", "already"));
        when(doses.markTaken(eq("nope"), any())).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "DOSE_NOT_FOUND", "none"));

        mvc.perform(post("/doses/d1/taken")).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("DOSE_ALREADY_TAKEN"));
        mvc.perform(post("/doses/nope/taken")).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("DOSE_NOT_FOUND"));
    }

    @Test
    void logPassesItsFiltersAndReturnsThePagedShape() throws Exception {
        when(doses.log(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new PagedResponse<>(1, 5, 12, List.of(view(DoseStatus.MISSED, null, false))));

        mvc.perform(get("/patient/me/dose-log").param("from", "2026-10-01T00:00:00Z").param("to", "2026-11-01T00:00:00Z")
                        .param("status", "MISSED").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.total").value(12))
                .andExpect(jsonPath("$.items[0].status").value("MISSED"));
        verify(doses).log(Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z"), "MISSED", 1, 5);
    }

    @Test
    void aBadDateInTheLogIs400() throws Exception {
        mvc.perform(get("/patient/me/dose-log").param("from", "last week"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
