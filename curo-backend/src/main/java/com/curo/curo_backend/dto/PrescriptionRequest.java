package com.curo.curo_backend.dto;

import lombok.Data;

@Data
public class PrescriptionRequest {
    private String patientAbha;
    private Integer doctorId;
    private Integer hospitalId;
    private String drug;
    private String dose;
    private String duration;
}