package com.curo.curo_backend.dto;

import lombok.Data;

import java.util.List;

/** Every field is optional, but at least one must be sent. A field that is not sent is left as it is. */
@Data
public class UpdateProfileRequest {
    private List<String> allergies;

    // YYYY-MM-DD
    private java.time.LocalDate dateOfBirth;

    // MALE, FEMALE or OTHER
    private String gender;

    // A+, A-, B+, B-, AB+, AB-, O+ or O-
    private String bloodGroup;

    private List<String> chronicConditions;
}
