package com.curo.curo_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public class PatientProfileView {
    private String id;
    private String careId;
    private String name;
    private String phone;
    private List<String> allergies;
    private boolean profileCompleted;
    private java.time.LocalDate dateOfBirth;
    private String gender;
    private String bloodGroup;
    private List<String> chronicConditions;

    public PatientProfileView(String id, String careId, String name, String phone, List<String> allergies, boolean profileCompleted) {
        this(id, careId, name, phone, allergies, profileCompleted, null, null, null, List.of());
    }
}
