package com.curo.curo_backend.dto;

import com.curo.curo_backend.model.Frequency;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

/** What a pharmacist needs to hand a medicine over, and nothing else: no allergies, history or profile data. */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActivePrescriptionView {
    // pass this as prescriptionId to POST /pharmacy/dispense
    private String id;
    // medicine name
    private String drug;
    // strength, as the doctor wrote it, e.g. "500 mg"
    private String dose;
    private Frequency frequency;
    // as written, e.g. "7 days"
    private String duration;
    // last day of the course, inclusive; absent when no duration was given
    private LocalDate endDate;
    private String doctorName;
    // always NOT_DISPENSED here: only prescriptions still to be handed over are listed
    private String dispenseStatus;
}
