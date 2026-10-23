package com.curo.curo_backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** One scheduled dose of one prescription. Times are UTC instants. */
@Data
@Document("dose_logs")
@CompoundIndexes({
        @CompoundIndex(name = "patient_scheduled", def = "{'patientId': 1, 'scheduledAt': 1}"),
        // the missed-dose job scans by status and time
        @CompoundIndex(name = "status_scheduled", def = "{'status': 1, 'scheduledAt': 1}"),
        // a prescription can have only one dose at a given moment, so a repeated generation cannot double up
        @CompoundIndex(name = "prescription_scheduled_unique", def = "{'prescriptionId': 1, 'scheduledAt': 1}", unique = true)
})
public class DoseLog {

    @Id
    private String id;

    private String prescriptionId;

    // the patient's user id (same meaning as in access_grants)
    private String patientId;

    private Instant scheduledAt;

    private Instant takenAt;

    private DoseStatus status;

    // taken more than the grace period after it was due
    private boolean late;

    // set once when the upcoming-dose reminder has been sent, so overlapping scheduler runs never remind twice
    private Instant reminderSentAt;

    private String takenByUserId;

    private Instant createdAt;
}
