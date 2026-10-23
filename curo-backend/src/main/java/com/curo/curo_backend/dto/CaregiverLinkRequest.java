package com.curo.curo_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CaregiverLinkRequest {

    // the caregiver's CareID
    @NotBlank
    @Size(max = 20)
    private String careId;

    @Size(max = 50)
    private String relationship;
}
