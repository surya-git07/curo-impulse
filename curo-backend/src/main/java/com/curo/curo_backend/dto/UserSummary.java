package com.curo.curo_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserSummary {
    private String id;
    private String careId;
    private String name;
    private String role;
    private String facilityId;
    private String facilityName;
    private boolean profileCompleted;
}
