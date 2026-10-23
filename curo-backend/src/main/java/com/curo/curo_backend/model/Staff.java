package com.curo.curo_backend.model;

import lombok.Data;

@Data
public class Staff {

    private Integer id;

    private Integer pharmacyFacilityId;

    private String username;

    private String passwordHash;

    private String role;

    private Integer hospitalId;

    private Integer doctorId;

    private String fullName;

    private String specialization;
    private String hprId;

    private Boolean hprVerified = false;
}