package com.curo.curo_backend.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DispensingLog {

    private Integer id;

    private Integer prescriptionId;

    private Integer pharmacyId;

    private Integer qtyDispensed;

    private LocalDateTime dispensedAt;

    private String status;

    private String drugName;

    private String patientAbha;

    private String rejectReason;
}