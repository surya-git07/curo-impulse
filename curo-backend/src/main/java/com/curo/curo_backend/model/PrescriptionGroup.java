package com.curo.curo_backend.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PrescriptionGroup {

    private Integer id;

    private String patientAbha;

    private Integer doctorId;

    private Integer hospitalId;

    private Integer pharmacyId;

    private String qrCode;

    private String status;

    private LocalDateTime issuedAt;

    private Integer extendedDays = 0;

    private String extensionNote;
}