package com.curo.curo_backend.service;

import java.time.Instant;

/**
 * The two dose changes that must be atomic, because a patient and the missed-dose job can act on the same dose
 * at the same moment. Each only changes a dose that is still in the state it expects.
 */
public interface DoseUpdates {

    /** UPCOMING or MISSED -> TAKEN. False if the dose was already TAKEN (or does not exist). */
    boolean markTaken(String doseId, Instant takenAt, boolean late, String takenByUserId);

    /** UPCOMING doses due at or before the cutoff -> MISSED. Returns how many changed. TAKEN doses are never touched. */
    long markMissed(Instant cutoff);
}
