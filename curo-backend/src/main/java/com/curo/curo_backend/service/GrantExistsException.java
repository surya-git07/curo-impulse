package com.curo.curo_backend.service;

import org.springframework.http.HttpStatus;

/** 409 GRANT_EXISTS: the requester already has a pending or active grant on this patient. */
public class GrantExistsException extends ApiException {

    private final String grantId;

    public GrantExistsException(String grantId) {
        super(HttpStatus.CONFLICT, "GRANT_EXISTS", "You already have a pending or active grant for this patient.");
        this.grantId = grantId;
    }

    public String getGrantId() {
        return grantId;
    }
}
