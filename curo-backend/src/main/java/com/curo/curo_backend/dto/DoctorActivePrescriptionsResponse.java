package com.curo.curo_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class DoctorActivePrescriptionsResponse {
    private String careId;
    private List<DoctorPrescriptionItem> prescriptions;
}
