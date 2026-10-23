package com.curo.curo_backend.dto;

import lombok.Data;

@Data
public class CheckInRequest {
    private String abha;
    private Integer hospitalId;
    private Integer doctorId;
    private String patientName;
    private Integer age;
    private String gender;
    // whatever else you actually need
}