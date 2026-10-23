package com.curo.curo_backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class ApproveRequest {
    private Integer durationHours;
    // may only narrow what was requested
    private List<String> scope;
}
