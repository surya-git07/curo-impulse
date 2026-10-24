package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.PatientSummaryView;
import com.curo.curo_backend.service.PatientSummaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PatientRecordSummaryController {

    @Autowired
    private PatientSummaryService patientSummaryService;

    @GetMapping("/patient/{careId}/summary")
    public PatientSummaryView summary(@PathVariable String careId) {
        return patientSummaryService.summary(careId);
    }
}
