package com.curo.curo_backend.dto;

import com.curo.curo_backend.model.Frequency;
import com.curo.curo_backend.model.PrescriptionStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/** One current prescription on the doctor's Extend list. */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DoctorPrescriptionItem {
    // pass this to PATCH /prescriptions/{id}/extend
    private String id;
    private String drug;
    // strength, as the doctor wrote it, e.g. "500 mg"
    private String strength;
    private Frequency frequency;
    private Instant issuedAt;
    // last day of the course, inclusive; absent when no duration was given
    private LocalDate endDate;
    // whole days from today to endDate (0 on the last day); absent without an end date
    private Integer daysLeft;
    private PrescriptionStatus status;
    // false once a pharmacy has dispensed it: a dispensed course is complete and cannot be extended
    private boolean canExtend;
}
