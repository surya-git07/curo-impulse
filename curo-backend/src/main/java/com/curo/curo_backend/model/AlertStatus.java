package com.curo.curo_backend.model;

/** SENT: handed to the delivery service. FAILED: the delivery service refused it. The alert is still in the recipient inbox either way. */
public enum AlertStatus {
    SENT,
    FAILED
}
