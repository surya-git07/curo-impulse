package com.curo.curo_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QueueCheckInRequest {
    @NotBlank
    private String careId;

    // required, but a missing or unknown doctor is answered 400 INVALID_DOCTOR by the service, not a generic validation error
    private String doctorId;
}
