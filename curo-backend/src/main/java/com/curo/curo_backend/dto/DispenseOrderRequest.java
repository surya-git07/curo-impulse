package com.curo.curo_backend.dto;

import lombok.Data;

/**
 * pharmacyId is optional, but if sent it must be the signed-in pharmacist's own pharmacy (a pharmacist can only dispense as
 * their own). drug, if sent, must match the prescription.
 */
@Data
public class DispenseOrderRequest {
    private String prescriptionId;
    private String pharmacyId;
    // optional: the CareID being served. If sent, it must be the prescription's patient.
    private String careId;
    // optional: the caregiver who collects the medicine, by CareID. If sent, it must be an existing CAREGIVER account.
    private String collectedByCareId;
    private String drug;
    private Integer quantity;
}
