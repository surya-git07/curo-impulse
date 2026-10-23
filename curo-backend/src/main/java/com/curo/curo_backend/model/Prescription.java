package com.curo.curo_backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * One drug prescribed to one patient. The top-level fields always equal the latest version; versions[] keeps
 * every change with who made it, when and why.
 */
@Data
@Document("prescriptions")
@CompoundIndex(name = "care_status", def = "{'careId': 1, 'status': 1}")
public class Prescription {

    @Id
    private String id;

    // guards against two doctors changing the same prescription at once and both writing "version 2"
    @Version
    private Long revision;

    // the patient's CareID
    private String careId;

    // user id of the issuing doctor
    private String doctorId;

    private String doctorName;

    private String doctorFacilityName;

    // canonical name when the drug is in drug_master, otherwise as typed
    private String drug;

    private String dose;

    // as the doctor wrote it, e.g. "7 days"
    private String duration;

    private Integer durationDays;

    private Frequency frequency;

    // dose times as HH:mm, empty for "as needed"
    private List<String> schedule;

    private PrescriptionStatus status;

    private Instant issuedAt;

    private Instant discontinuedAt;

    private int currentVersion;

    private List<PrescriptionVersion> versions;
}
