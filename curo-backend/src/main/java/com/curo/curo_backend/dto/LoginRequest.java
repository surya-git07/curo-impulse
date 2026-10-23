package com.curo.curo_backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    // CareID (CID-XXXXXX) or phone
    @NotBlank
    private String identifier;

    @NotBlank
    private String password;
}
