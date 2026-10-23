package com.curo.curo_backend.dto;

import com.curo.curo_backend.model.DoseStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DoseView {

    @Data
    @AllArgsConstructor
    public static class MarkedBy {
        private String userId;
        private String role;
    }

    private String id;
    private String prescriptionId;
    private String drug;
    private String dose;
    private Instant scheduledAt;
    private DoseStatus status;
    private Instant takenAt;
    private boolean late;
    private MarkedBy markedBy;
}
