package com.curo.curo_backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Something one user should know about. Every alert lands in the recipient's inbox (GET /alerts); channel records how it was
 * also pushed out: EMAIL (Amazon SNS) or LOG (the fallback when SNS is not configured).
 */
@Data
@Document("alerts")
@CompoundIndex(name = "recipient_created", def = "{'recipientId': 1, 'createdAt': -1}")
public class Alert {

    @Id
    private String id;

    private AlertType type;

    // user id of who receives it
    private String recipientId;

    // EMAIL or LOG
    private String channel;

    // the dose it is about
    private String doseId;

    private String prescriptionId;

    // the patient it is about; for a patient's own alerts this is themself
    private String patientCareId;

    // text for the inbox. It may name the drug; what is sent outside the app never does.
    private String message;

    private AlertStatus status;

    private Instant sentAt;

    private Instant readAt;

    private Instant createdAt;
}
