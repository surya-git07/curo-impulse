package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.ActivePrescriptionsResponse;
import com.curo.curo_backend.service.PharmacyLookupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pharmacy")
public class PharmacyLookupController {

    @Autowired
    private PharmacyLookupService lookupService;

    @GetMapping("/patients/{careId}/prescriptions/active")
    public ActivePrescriptionsResponse active(@PathVariable String careId) {
        return lookupService.activePrescriptions(careId);
    }
}
