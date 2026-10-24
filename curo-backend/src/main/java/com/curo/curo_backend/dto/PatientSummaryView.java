package com.curo.curo_backend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * What a doctor sees on the Patient screen. It is built only after an active PROFILE grant is confirmed.
 * medications needs MEDICATIONS in the grant and prescriptions needs PRESCRIPTIONS; a part the grant does not cover is left out.
 * There is no date of birth, phone or CareID-independent identifier here: age is computed.
 */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PatientSummaryView {
    private String careId;
    private String name;
    private Integer ageYears;
    private String gender;
    private String bloodGroup;
    private List<String> allergies;
    // "Any other disease"
    private List<String> chronicConditions;
    private List<MedicationView> medications;
    // every prescription, newest first, each with its versions
    private List<PrescriptionView> prescriptions;
}
