package com.curo.curo_backend.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Hospital {

    private Integer id;

    private String name;

    private Integer pharmacyId;

    private String type;

    private String registrationNumber; // Clinical Establishment / state-specific registration number

    private String gstin;

    private String verificationStatus = "PENDING"; // PENDING, VERIFIED, REJECTED

    private LocalDateTime verifiedAt;

    private String verifiedBy; // admin username who verified it
}