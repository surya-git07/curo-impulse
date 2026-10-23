package com.curo.curo_backend.dto;

import com.curo.curo_backend.model.AlertStatus;
import com.curo.curo_backend.model.AlertType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AlertView {
    private String id;
    private AlertType type;
    private String patientCareId;
    private String message;
    private String doseId;
    private String prescriptionId;
    private String channel;
    private AlertStatus status;
    private Instant sentAt;
    private Instant readAt;
}
