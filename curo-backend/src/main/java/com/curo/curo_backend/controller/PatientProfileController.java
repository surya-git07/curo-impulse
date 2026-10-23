package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.PatientProfileView;
import com.curo.curo_backend.dto.UpdateProfileRequest;
import com.curo.curo_backend.service.PatientProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/patient/me")
public class PatientProfileController {

    @Autowired
    private PatientProfileService patientProfileService;

    @GetMapping
    public PatientProfileView get() {
        return patientProfileService.get();
    }

    @PutMapping
    public PatientProfileView update(@RequestBody UpdateProfileRequest request) {
        return patientProfileService.update(request);
    }
}
