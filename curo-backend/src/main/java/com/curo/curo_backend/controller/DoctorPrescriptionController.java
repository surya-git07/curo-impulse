package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.DoctorActivePrescriptionsResponse;
import com.curo.curo_backend.dto.ExtendRequest;
import com.curo.curo_backend.dto.PrescriptionView;
import com.curo.curo_backend.service.PrescriptionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The doctor's Extend tab: list a patient's current prescriptions, then move a course's end date later. */
@RestController
public class DoctorPrescriptionController {

    @Autowired
    private PrescriptionService prescriptionService;

    @GetMapping("/doctor/patients/{careId}/prescriptions/active")
    public DoctorActivePrescriptionsResponse active(@PathVariable String careId) {
        return prescriptionService.activeForDoctor(careId);
    }

    @PatchMapping("/prescriptions/{id}/extend")
    public PrescriptionView extend(@PathVariable String id, @Valid @RequestBody ExtendRequest request) {
        return prescriptionService.extend(id, request);
    }
}
