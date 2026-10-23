package com.curo.curo_backend.dto;

import com.curo.curo_backend.model.Frequency;
import com.curo.curo_backend.model.PrescriptionStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MedicationView {

    @Data
    @AllArgsConstructor
    public static class Prescriber {
        private String name;
        private String hospitalName;
    }

    private String prescriptionId;
    private String drug;
    private String dose;
    private Frequency frequency;
    private List<String> times;
    private LocalDate startDate;
    // last day of the course, inclusive; absent when no duration was given
    private LocalDate endDate;
    private PrescriptionStatus status;
    private int currentVersion;
    private Prescriber prescribedBy;
}
