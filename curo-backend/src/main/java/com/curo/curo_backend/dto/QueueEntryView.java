package com.curo.curo_backend.dto;

import com.curo.curo_backend.model.QueueStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/** careId, name, doctor, status and time only. No medical data. The doctor queue adds the entry id so it can be marked done. */
@Data
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QueueEntryView {
    private String id;
    private String careId;
    private String patientName;
    private String doctorId;
    private String doctorName;
    private QueueStatus status;
    private Instant checkedInAt;
}
