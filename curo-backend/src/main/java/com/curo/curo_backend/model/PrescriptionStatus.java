package com.curo.curo_backend.model;

public enum PrescriptionStatus {
    // issued, not yet handed over by a pharmacy
    ACTIVE,
    // a pharmacy has dispensed it; the patient is taking it, so it still counts as a current medication
    DISPENSED,
    DISCONTINUED;

    /** True while the patient is meant to be taking it: issued or dispensed, not discontinued. */
    public boolean isLive() {
        return this == ACTIVE || this == DISPENSED;
    }
}
