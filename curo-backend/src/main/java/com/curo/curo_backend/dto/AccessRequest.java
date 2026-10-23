package com.curo.curo_backend.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Send exactly one of careId or shareCode. With a share code, scope and duration come from the code. */
@Data
public class AccessRequest {

    @Size(max = 20)
    private String careId;

    @Size(max = 20)
    private String shareCode;

    private List<String> scope;

    @Size(max = 500)
    private String reason;

    private Integer requestedDurationHours;
}
