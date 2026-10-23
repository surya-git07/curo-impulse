package com.curo.curo_backend.dto;

import com.curo.curo_backend.model.Frequency;
import com.curo.curo_backend.model.PrescriptionStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PrescriptionView {
    private String id;
    private String careId;
    private String doctorId;
    private String drug;
    private String dose;
    private String duration;
    private Integer durationDays;
    private Frequency frequency;
    private List<String> times;
    private PrescriptionStatus status;
    private Instant issuedAt;
    private Instant discontinuedAt;
    private int currentVersion;
    // only on the responses that show history
    private List<VersionView> versions;
}
