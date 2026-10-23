package com.curo.curo_backend.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ModifyRequest {
    @Size(max = 100)
    private String dose;
    @Size(max = 50)
    private String duration;
    private Integer durationDays;
    private String frequency;
    private List<String> times;
    // required: every change must say why
    @Size(max = 500)
    private String reason;
}
