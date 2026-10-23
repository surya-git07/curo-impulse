package com.curo.curo_backend.dto;

import com.curo.curo_backend.model.LinkStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CaregiverLinkView {
    private String id;
    private String patientCareId;
    private String patientName;
    private String caregiverCareId;
    private String caregiverName;
    private String relationship;
    private LinkStatus status;
    private Instant createdAt;
    private Instant revokedAt;
}
