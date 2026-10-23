package com.curo.curo_backend.dto;

import lombok.Data;

@Data
public class PharmacyFacilityRequest {
    private String name;
    private String type;
    private String drugLicenseNumber;
    private String gstin;
}