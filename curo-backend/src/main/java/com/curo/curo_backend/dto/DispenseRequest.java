package com.curo.curo_backend.dto;

import lombok.Data;

@Data
public class DispenseRequest {
    private Integer prescriptionId;
    private Integer pharmacyId;
    private String drug;
    private Integer quantity;
}