package com.curo.curo_backend.dto;

import com.curo.curo_backend.model.ChangeType;
import com.curo.curo_backend.model.Frequency;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
public class VersionView {
    private int version;
    private ChangeType changeType;
    private String dose;
    private String duration;
    private Integer durationDays;
    private Frequency frequency;
    private List<String> times;
    private String reason;
    private ActorView changedBy;
    private Instant changedAt;
    // only on an EXTENDED version: the last day of the course before and after
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocalDate previousEndDate;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private LocalDate endDate;

    public VersionView(int version, ChangeType changeType, String dose, String duration, Integer durationDays, Frequency frequency,
                       List<String> times, String reason, ActorView changedBy, Instant changedAt) {
        this(version, changeType, dose, duration, durationDays, frequency, times, reason, changedBy, changedAt, null, null);
    }
}
