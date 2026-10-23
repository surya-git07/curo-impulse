package com.curo.curo_backend.dto;

import lombok.Data;

@Data
public class ConsentRequest {
    private Boolean accepted;
    private String policyVersion;
}
