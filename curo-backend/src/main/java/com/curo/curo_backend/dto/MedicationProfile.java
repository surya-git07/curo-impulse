package com.curo.curo_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MedicationProfile {
    private String careId;
    private Instant asOf;
    private List<MedicationView> medications;
    // only shown to a patient themself, or a grant that also covers PROFILE
    private List<String> allergies;
}
