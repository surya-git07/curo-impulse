package com.curo.curo_backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** One attempt by a pharmacy to dispense a prescription. Append-only. */
@Data
@Document("dispensing_logs")
@CompoundIndexes({
        // a prescription is dispensed once: the database itself refuses a second DISPENSED row, even for two simultaneous requests
        @CompoundIndex(name = "prescription_dispensed_unique", def = "{'prescriptionId': 1}", unique = true,
                partialFilter = "{'status': 'DISPENSED'}"),
        @CompoundIndex(name = "pharmacy_at", def = "{'pharmacyId': 1, 'at': -1}")
})
public class DispensingLog {

    @Id
    private String id;

    private String prescriptionId;

    private String pharmacyId;

    private String careId;

    private String drug;

    private int quantity;

    private DispenseStatus status;

    // why a REJECTED attempt was refused
    private String rejectReason;

    private String dispensedByUserId;

    private Instant at;
}
