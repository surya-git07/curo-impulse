package com.curo.curo_backend.model;

/** How serious an interaction or allergy finding is. UNKNOWN means the check could not be completed. */
public enum Severity {
    NONE(0),
    LOW(1),
    MODERATE(2),
    HIGH(3),
    UNKNOWN(-1);

    private final int rank;

    Severity(int rank) {
        this.rank = rank;
    }

    public boolean isWorseThan(Severity other) {
        return rank > other.rank;
    }
}
