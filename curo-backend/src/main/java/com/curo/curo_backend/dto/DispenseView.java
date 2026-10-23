package com.curo.curo_backend.dto;

import com.curo.curo_backend.model.DispenseStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class DispenseView {
    private String id;
    private String prescriptionId;
    private String pharmacyId;
    private int qtyDispensed;
    private Instant dispensedAt;
    private DispenseStatus status;
    private String drugName;
    private String careId;
}
