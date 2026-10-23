package com.curo.curo_backend.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class PharmacyFacility {

    private Integer id;

    private String name;

    private String type;

    private String drugLicenseNumber; // under Drugs and Cosmetics Act

    private String gstin;

    private String verificationStatus = "PENDING";

    private LocalDateTime verifiedAt;

    private String verifiedBy;
}