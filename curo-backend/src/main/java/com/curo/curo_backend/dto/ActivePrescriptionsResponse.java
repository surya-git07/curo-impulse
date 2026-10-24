package com.curo.curo_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ActivePrescriptionsResponse {
    private String careId;
    private List<ActivePrescriptionView> prescriptions;
}
