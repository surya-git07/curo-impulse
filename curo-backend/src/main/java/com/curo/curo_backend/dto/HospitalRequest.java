package com.curo.curo_backend.dto;

import lombok.Data;

@Data
public class HospitalRequest {
    private String name;
    private String type;
    private String registrationNumber;
    private String gstin;
}