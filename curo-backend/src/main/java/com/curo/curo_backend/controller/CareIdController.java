package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.CareIdLookupResponse;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.service.AccessGuard;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CareIdController {

    @Autowired
    private AccessGuard accessGuard;

    // First patient-data endpoint on the new stack. Every other one calls the same guard before reading anything.
    @GetMapping("/careid/{careId}")
    public CareIdLookupResponse lookup(@PathVariable String careId) {
        var granted = accessGuard.require(careId, GrantScope.PROFILE, AuditAction.READ, "PROFILE", careId);
        var patient = granted.patient();
        return new CareIdLookupResponse(patient.getCareId(), patient.getName(), null, null,
                new CareIdLookupResponse.ConsentFlag(patient.getConsent() != null && patient.getConsent().isAccepted()));
    }
}
