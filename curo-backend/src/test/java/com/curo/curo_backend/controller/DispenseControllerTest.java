package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.DispenseOrderRequest;
import com.curo.curo_backend.dto.DispenseView;
import com.curo.curo_backend.model.DispenseStatus;
import com.curo.curo_backend.service.ApiException;
import com.curo.curo_backend.service.DispenseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DispenseControllerTest {

    private DispenseService dispense;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        dispense = mock(DispenseService.class);
        DispenseController controller = new DispenseController();
        ReflectionTestUtils.setField(controller, "dispenseService", dispense);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void dispenseReturnsTheContractLogFields() throws Exception {
        when(dispense.dispense(any())).thenReturn(new DispenseView("log1", "rx1", "pharm1", 4, Instant.parse("2026-10-23T10:00:00Z"),
                DispenseStatus.DISPENSED, "Amoxicillin", "CID-7K3MQ9"));

        mvc.perform(post("/pharmacy/dispense").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prescriptionId\":\"rx1\",\"pharmacyId\":\"pharm1\",\"drug\":\"Amoxicillin\",\"quantity\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("log1"))
                .andExpect(jsonPath("$.prescriptionId").value("rx1"))
                .andExpect(jsonPath("$.pharmacyId").value("pharm1"))
                .andExpect(jsonPath("$.qtyDispensed").value(4))
                .andExpect(jsonPath("$.dispensedAt").value("2026-10-23T10:00:00Z"))
                .andExpect(jsonPath("$.status").value("DISPENSED"))
                .andExpect(jsonPath("$.drugName").value("Amoxicillin"))
                .andExpect(jsonPath("$.careId").value("CID-7K3MQ9"))
                .andExpect(jsonPath("$.rejectReason").doesNotExist());

        ArgumentCaptor<DispenseOrderRequest> captor = ArgumentCaptor.forClass(DispenseOrderRequest.class);
        verify(dispense).dispense(captor.capture());
        assertThat(captor.getValue().getPrescriptionId()).isEqualTo("rx1");
        assertThat(captor.getValue().getPharmacyId()).isEqualTo("pharm1");
        assertThat(captor.getValue().getQuantity()).isEqualTo(4);
    }

    @Test
    void theErrorsKeepTheirCodes() throws Exception {
        when(dispense.dispense(any())).thenThrow(new ApiException(HttpStatus.CONFLICT, "ALREADY_DISPENSED", "already"));
        mvc.perform(post("/pharmacy/dispense").contentType(MediaType.APPLICATION_JSON).content("{\"prescriptionId\":\"rx1\",\"quantity\":1}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ALREADY_DISPENSED"));

        doThrow(new ApiException(HttpStatus.FORBIDDEN, "NO_ACTIVE_GRANT", "no grant")).when(dispense).dispense(any());
        mvc.perform(post("/pharmacy/dispense").contentType(MediaType.APPLICATION_JSON).content("{\"prescriptionId\":\"rx1\",\"quantity\":1}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("NO_ACTIVE_GRANT"));

        doThrow(new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "not yours")).when(dispense).dispense(any());
        mvc.perform(post("/pharmacy/dispense").contentType(MediaType.APPLICATION_JSON).content("{\"prescriptionId\":\"rx1\",\"pharmacyId\":\"x\",\"quantity\":1}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void theStockAndFinderEndpointsAreGone() throws Exception {
        mvc.perform(get("/pharmacy/stock/pharm1")).andExpect(status().isNotFound());
        mvc.perform(put("/pharmacy/stock/pharm1").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().is4xxClientError());
        mvc.perform(get("/pharmacies/nearby?lat=12.9&lng=77.6")).andExpect(status().isNotFound());
    }
}
