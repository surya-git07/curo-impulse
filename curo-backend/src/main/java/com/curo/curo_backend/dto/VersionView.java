package com.curo.curo_backend.dto;

import com.curo.curo_backend.model.ChangeType;
import com.curo.curo_backend.model.Frequency;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
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
}
