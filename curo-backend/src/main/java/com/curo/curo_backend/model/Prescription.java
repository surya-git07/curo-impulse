package com.curo.curo_backend.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Prescription {

    private Integer id;

    private String patientAbha;

    private Integer doctorId;

    private String drug;

    private String dose;

    private String duration;

    private String status;

    private LocalDateTime issuedAt;

    private Integer prescriptionGroupId;

    private String externalPrescriptionId;
}