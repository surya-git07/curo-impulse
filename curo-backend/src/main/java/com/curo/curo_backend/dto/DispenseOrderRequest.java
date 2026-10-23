package com.curo.curo_backend.dto;

import lombok.Data;

/** pharmacyId may be sent but is ignored: the pharmacy is the signed-in pharmacist's own. drug, if sent, must match the prescription. */
@Data
public class DispenseOrderRequest {
    private String prescriptionId;
    private String drug;
    private Integer quantity;
}
