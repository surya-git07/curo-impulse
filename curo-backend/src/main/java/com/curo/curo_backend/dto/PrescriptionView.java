package com.curo.curo_backend.dto;

import com.curo.curo_backend.model.Frequency;
import com.curo.curo_backend.model.PrescriptionStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
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
    // last day of the course, inclusive, worked out from the issue date and the duration; absent when no duration was given
    private LocalDate endDate;
    // whole days from today to endDate (0 on the last day, never negative); absent without an end date
    private Integer daysLeft;

    public PrescriptionView(String id, String careId, String doctorId, String drug, String dose, String duration, Integer durationDays,
                            Frequency frequency, List<String> times, PrescriptionStatus status, Instant issuedAt, Instant discontinuedAt,
                            int currentVersion, List<VersionView> versions) {
        this(id, careId, doctorId, drug, dose, duration, durationDays, frequency, times, status, issuedAt, discontinuedAt,
                currentVersion, versions, null, null);
    }
}
