package com.curo.curo_backend.service;

import java.time.Instant;

/**
 * Dose changes that must be atomic, because a patient and the scheduler can act on the same dose at the same moment, and two
 * overlapping scheduler runs can reach the same dose. Each only changes a dose still in the state it expects.
 */
public interface DoseUpdates {

    /** UPCOMING or MISSED -> TAKEN. False if the dose was already TAKEN (or does not exist). */
    boolean markTaken(String doseId, Instant takenAt, boolean late, String takenByUserId);

    /** UPCOMING -> MISSED. False if the dose is no longer UPCOMING (taken, or another run got there first). Never touches a TAKEN dose. */
    boolean markMissed(String doseId);

    /** Records that the reminder was sent, but only if none was before. False means someone else already claimed it. */
    boolean markReminderSent(String doseId, Instant at);
}
