package com.curo.curo_backend.model;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/** One entry in a prescription's history. Versions are only ever appended, never edited or removed. */
@Data
public class PrescriptionVersion {

    private int version;

    private ChangeType changeType;

    private String dose;

    private String duration;

    private Integer durationDays;

    private Frequency frequency;

    // dose times as HH:mm
    private List<String> schedule;

    // why it changed; empty for the first version
    private String reason;

    private String changedByUserId;

    private String changedByName;

    private String changedByRole;

    private Instant changedAt;
}
