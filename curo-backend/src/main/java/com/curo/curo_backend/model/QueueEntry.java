package com.curo.curo_backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One patient waiting at the front desk. It holds who and when only, never medical data, and it grants nobody
 * access to the patient record: a doctor still needs an active access grant.
 */
@Data
@Document("queue_entries")
@CompoundIndex(name = "status_checkedInAt", def = "{'status': 1, 'checkedInAt': -1}")
@CompoundIndex(name = "doctorId_checkedInAt", def = "{'doctorId': 1, 'checkedInAt': -1}")
public class QueueEntry {

    @Id
    private String id;

    private String careId;

    private String patientName;

    // the doctor the patient is waiting for: a VERIFIED DOCTOR account when the entry was made
    private String doctorId;

    private String doctorName;

    private QueueStatus status;

    // server time, UTC
    private Instant checkedInAt;

    // user id of the reception account
    private String checkedInBy;

    // may be empty until accounts are tied to a facility
    private String facilityId;
}
