package com.curo.curo_backend.model;

/** Only the server ever sets MISSED. A patient can only move a dose to TAKEN. */
public enum DoseStatus {
    UPCOMING,
    TAKEN,
    MISSED
}
