package com.curo.curo_backend.dto;

import lombok.Data;

import java.util.List;

@Data
public class ShareCodeRequest {
    private List<String> scope;
    private Integer durationHours;
}
