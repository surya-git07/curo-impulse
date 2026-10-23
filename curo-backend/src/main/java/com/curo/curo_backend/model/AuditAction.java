package com.curo.curo_backend.model;

public enum AuditAction {
    READ,
    WRITE,
    QR_SCAN,
    GRANT_REQUESTED,
    GRANT_APPROVED,
    GRANT_REVOKED,
    GRANT_EXPIRED
}
