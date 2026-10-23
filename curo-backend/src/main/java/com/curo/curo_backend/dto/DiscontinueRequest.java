package com.curo.curo_backend.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DiscontinueRequest {
    @Size(max = 500)
    private String reason;
}
