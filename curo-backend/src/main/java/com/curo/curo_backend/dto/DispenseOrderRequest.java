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
    private String drug;
    private Integer quantity;
}
