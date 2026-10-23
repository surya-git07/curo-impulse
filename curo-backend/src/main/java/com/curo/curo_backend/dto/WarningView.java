package com.curo.curo_backend.dto;

import com.curo.curo_backend.model.Severity;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class WarningView {
    private String drug;
    private Severity severity;
    private String warning;
    private String explanation;
}
