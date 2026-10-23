package com.curo.curo_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** The prescriptions were issued whatever the warnings say; warnings never block. */
@Data
@AllArgsConstructor
public class IssueResponse {
    private List<PrescriptionView> drugs;
    private List<WarningView> warnings;
}
