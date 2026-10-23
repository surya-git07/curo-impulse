package com.curo.curo_backend.service;

import java.time.Instant;

/**
 * Stock changes that must be atomic: two counter sales or two dispenses at the same moment must never both succeed on
 * the last unit, and a set must not race with a dispense.
 */
public interface StockUpdates {

    /** Sets the quantity for a pharmacy and drug, creating the row if it does not exist. */
    void set(String pharmacyId, String normalizedDrug, String displayDrug, int quantity, Instant at);

    /** Takes {@code quantity} units off, but only if at least that many are there. False (and nothing changes) otherwise. */
    boolean decrement(String pharmacyId, String normalizedDrug, int quantity, Instant at);

    /** Puts units back, used to undo a decrement when the dispense could not be recorded. */
    void increment(String pharmacyId, String normalizedDrug, int quantity, Instant at);
}
