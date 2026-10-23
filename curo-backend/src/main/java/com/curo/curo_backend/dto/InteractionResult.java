package com.curo.curo_backend.dto;

import com.curo.curo_backend.model.Severity;
import lombok.AllArgsConstructor;
import lombok.Data;

/** Advice only. There is deliberately no "blocked" field: nothing in the system refuses a prescription because of this. */
@Data
@AllArgsConstructor
public class InteractionResult {
    private Severity severity;
    // short; null when severity is NONE
    private String warning;
    // longer; lists every finding
    private String explanation;
}
