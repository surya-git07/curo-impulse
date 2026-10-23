package com.curo.curo_backend.dto;

import lombok.Data;

@Data
public class StaffRegisterRequest {
    private String username;
    private String password;
    private String fullName;
    private String role;
    private Integer hospitalId;
    private Integer pharmacyFacilityId;
    private String hprId;
    private Boolean hprVerified;
}