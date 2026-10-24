package com.curo.curo_backend.model;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
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

    // only on an EXTENDED version: the last day of the course before and after (inclusive); previousEndDate is empty
    // when the prescription had no end date before
    private LocalDate previousEndDate;

    private LocalDate endDate;

    // why it changed; empty for the first version
    private String reason;

    private String changedByUserId;

    private String changedByName;

    private String changedByRole;

    private Instant changedAt;
}
