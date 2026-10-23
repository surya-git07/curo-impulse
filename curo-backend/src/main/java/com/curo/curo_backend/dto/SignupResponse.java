package com.curo.curo_backend.dto;

import com.curo.curo_backend.model.Consent;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SignupResponse {
    private String id;
    private String careId;
    private String name;
    private String role;
    private Consent consent;
}
