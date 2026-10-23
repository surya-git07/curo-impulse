package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.MedicationProfile;
import com.curo.curo_backend.service.MedicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MedicationController {

    @Autowired
    private MedicationService medicationService;

    @GetMapping("/patient/me/medications")
    public MedicationProfile mine() {
        return medicationService.mine();
    }

    @GetMapping("/patient/{careId}/medications")
    public MedicationProfile forPatient(@PathVariable String careId) {
        return medicationService.forPatient(careId);
    }
}
