package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.DispenseView;
import com.curo.curo_backend.dto.NearbyPharmacyView;
import com.curo.curo_backend.dto.StockView;
import com.curo.curo_backend.model.DispenseStatus;
import com.curo.curo_backend.service.ApiException;
import com.curo.curo_backend.service.NearbyPharmacyService;
import com.curo.curo_backend.service.PharmacyStockService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PharmacyControllersTest {

    private NearbyPharmacyService nearby;
    private PharmacyStockService stock;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        nearby = mock(NearbyPharmacyService.class);
        stock = mock(PharmacyStockService.class);
        NearbyPharmacyController n = new NearbyPharmacyController();
        ReflectionTestUtils.setField(n, "nearbyPharmacyService", nearby);
        PharmacyStockController s = new PharmacyStockController();
        ReflectionTestUtils.setField(s, "pharmacyStockService", stock);
        mvc = MockMvcBuilders.standaloneSetup(n, s).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    // ---- nearby

    @Test
    void nearbyHasTheContractShapeAndNoQuantities() throws Exception {
        when(nearby.nearby(any(), any(), any(), any(), any())).thenReturn(List.of(new NearbyPharmacyView("ph1", "Koramangala Care Pharmacy",
                "Koramangala, Bengaluru 560034", "KA", new NearbyPharmacyView.Location(12.9352, 77.6245), 0.03, true)));

        mvc.perform(get("/pharmacies/nearby").param("lat", "12.9352").param("lng", "77.6245").param("drug", "Amoxicillin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("ph1"))
                .andExpect(jsonPath("$[0].name").value("Koramangala Care Pharmacy"))
                .andExpect(jsonPath("$[0].address").value("Koramangala, Bengaluru 560034"))
                .andExpect(jsonPath("$[0].state").value("KA"))
                .andExpect(jsonPath("$[0].location.lat").value(12.9352))
                .andExpect(jsonPath("$[0].location.lng").value(77.6245))
                .andExpect(jsonPath("$[0].distanceKm").value(0.03))
                .andExpect(jsonPath("$[0].inStock").value(true))
                .andExpect(jsonPath("$[0].quantity").doesNotExist());
        verify(nearby).nearby(12.9352, 77.6245, "Amoxicillin", null, null);
    }

    @Test
    void inStockIsExplicitlyNullWhenNoDrugWasAsked() throws Exception {
        when(nearby.nearby(any(), any(), isNull(), any(), any())).thenReturn(List.of(new NearbyPharmacyView("ph1", "P", "A", "KA",
                new NearbyPharmacyView.Location(1, 2), 1.5, null)));

        String body = mvc.perform(get("/pharmacies/nearby").param("lat", "12.9").param("lng", "77.6")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("\"inStock\":null");
    }

    @Test
    void radiusAndLimitArePassedThrough() throws Exception {
        when(nearby.nearby(any(), any(), any(), any(), any())).thenReturn(List.of());

        mvc.perform(get("/pharmacies/nearby").param("lat", "12.9").param("lng", "77.6").param("radiusKm", "12.5").param("limit", "3"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        verify(nearby).nearby(12.9, 77.6, null, 12.5, 3);
    }

    @Test
    void badCoordinatesAre400WithTheContractCode() throws Exception {
        when(nearby.nearby(any(), any(), any(), any(), any()))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COORDINATES", "bad"));

        mvc.perform(get("/pharmacies/nearby")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_COORDINATES"));
    }

    @Test
    void textWhereANumberIsExpectedIs400NotAServerError() throws Exception {
        mvc.perform(get("/pharmacies/nearby").param("lat", "north").param("lng", "77.6"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    // ---- stock and dispense

    @Test
    void stockListHasTheContractFields() throws Exception {
        when(stock.stock("pharm1")).thenReturn(List.of(new StockView("s1", "pharm1", "Amoxicillin", 10, Instant.parse("2026-10-23T10:00:00Z"))));

        mvc.perform(get("/pharmacy/stock/pharm1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("s1"))
                .andExpect(jsonPath("$[0].pharmacyId").value("pharm1"))
                .andExpect(jsonPath("$[0].drug").value("Amoxicillin"))
                .andExpect(jsonPath("$[0].quantity").value(10));
    }

    @Test
    void stockUpdateSendsDrugAndQuantity() throws Exception {
        when(stock.updateStock(eq("pharm1"), any())).thenReturn(new StockView("s1", "pharm1", "Amoxicillin", 25, Instant.now()));

        mvc.perform(put("/pharmacy/stock/pharm1").contentType(MediaType.APPLICATION_JSON).content("{\"drug\":\"Amoxicillin\",\"quantity\":25}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(25));

        var captor = ArgumentCaptor.forClass(com.curo.curo_backend.dto.StockUpdateRequest.class);
        verify(stock).updateStock(eq("pharm1"), captor.capture());
        assertThat(captor.getValue().getDrug()).isEqualTo("Amoxicillin");
        assertThat(captor.getValue().getQuantity()).isEqualTo(25);
    }

    @Test
    void dispenseReturnsTheContractLogFieldsAndIgnoresAPharmacyIdInTheBody() throws Exception {
        when(stock.dispense(any())).thenReturn(new DispenseView("log1", "rx1", "pharm1", 4, Instant.parse("2026-10-23T10:00:00Z"),
                DispenseStatus.DISPENSED, "Amoxicillin", "CID-7K3MQ9"));

        mvc.perform(post("/pharmacy/dispense").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prescriptionId\":\"rx1\",\"pharmacyId\":\"someone-elses\",\"drug\":\"Amoxicillin\",\"quantity\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("log1"))
                .andExpect(jsonPath("$.prescriptionId").value("rx1"))
                .andExpect(jsonPath("$.pharmacyId").value("pharm1"))
                .andExpect(jsonPath("$.qtyDispensed").value(4))
                .andExpect(jsonPath("$.dispensedAt").value("2026-10-23T10:00:00Z"))
                .andExpect(jsonPath("$.status").value("DISPENSED"))
                .andExpect(jsonPath("$.drugName").value("Amoxicillin"))
                .andExpect(jsonPath("$.careId").value("CID-7K3MQ9"));

        var captor = ArgumentCaptor.forClass(com.curo.curo_backend.dto.DispenseOrderRequest.class);
        verify(stock).dispense(captor.capture());
        assertThat(captor.getValue().getPrescriptionId()).isEqualTo("rx1");
        assertThat(captor.getValue().getQuantity()).isEqualTo(4);
    }

    @Test
    void insufficientStockIs409AndNoActiveGrantIs403() throws Exception {
        when(stock.dispense(any())).thenThrow(new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "not enough"));
        mvc.perform(post("/pharmacy/dispense").contentType(MediaType.APPLICATION_JSON).content("{\"prescriptionId\":\"rx1\",\"quantity\":99}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("INSUFFICIENT_STOCK"));

        org.mockito.Mockito.doThrow(new ApiException(HttpStatus.FORBIDDEN, "NO_ACTIVE_GRANT", "no grant")).when(stock).dispense(any());
        mvc.perform(post("/pharmacy/dispense").contentType(MediaType.APPLICATION_JSON).content("{\"prescriptionId\":\"rx1\",\"quantity\":1}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("NO_ACTIVE_GRANT"));
    }
}
