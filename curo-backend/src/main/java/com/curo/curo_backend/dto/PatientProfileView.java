package com.curo.curo_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PatientProfileView {
    private String id;
    private String careId;
    private String name;
    private String phone;
    private List<String> allergies;
    private boolean profileCompleted;
}
