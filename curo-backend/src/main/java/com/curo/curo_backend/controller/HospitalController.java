package com.curo.curo_backend.controller;

import org.springframework.context.annotation.Profile;
import com.curo.curo_backend.dto.HospitalRequest;
import com.curo.curo_backend.model.Hospital;
import com.curo.curo_backend.repository.HospitalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Profile("legacy-jpa")
@RestController
@RequestMapping("/hospital")
public class HospitalController {

    @Autowired
    private HospitalRepository hospitalRepository;

    // Standard GSTIN format: 2 digits state code, 10 char PAN, 1 entity code, 1 'Z', 1 checksum
    private static final Pattern GSTIN_FORMAT = Pattern.compile(
            "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$"
    );

    private boolean isValidGstinFormat(String gstin) {
        return gstin != null && GSTIN_FORMAT.matcher(gstin.toUpperCase()).matches();
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerHospital(@RequestBody HospitalRequest request) {
        boolean exists = hospitalRepository.findAll().stream()
                .anyMatch(h -> h.getName().equalsIgnoreCase(request.getName()));

        if (exists) {
            return ResponseEntity.status(409).body("A hospital with this name already exists");
        }

        if (request.getRegistrationNumber() == null || request.getRegistrationNumber().isBlank()) {
            return ResponseEntity.badRequest().body("Registration number is required.");
        }

        if (request.getGstin() != null && !request.getGstin().isBlank() && !isValidGstinFormat(request.getGstin())) {
            return ResponseEntity.badRequest().body("GSTIN format is invalid. Expected format: 22AAAAA0000A1Z5");
        }

        Hospital hospital = new Hospital();
        hospital.setName(request.getName());
        hospital.setType(request.getType());
        hospital.setRegistrationNumber(request.getRegistrationNumber());
        hospital.setGstin(request.getGstin());
        hospital.setVerificationStatus("PENDING");

        Hospital saved = hospitalRepository.save(hospital);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/list")
    public ResponseEntity<List<Hospital>> listHospitals() {
        return ResponseEntity.ok(hospitalRepository.findAll());
    }

    // Admin-only manual verification step — wire up role-based security same as your other admin endpoints
    @PostMapping("/{id}/verify")
    public ResponseEntity<?> verifyHospital(@PathVariable Integer id, @RequestParam String adminUsername) {
        Optional<Hospital> hospitalOpt = hospitalRepository.findById(id);
        if (hospitalOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Hospital not found.");
        }
        Hospital hospital = hospitalOpt.get();
        hospital.setVerificationStatus("VERIFIED");
        hospital.setVerifiedAt(LocalDateTime.now());
        hospital.setVerifiedBy(adminUsername);
        return ResponseEntity.ok(hospitalRepository.save(hospital));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectHospital(@PathVariable Integer id, @RequestParam String adminUsername) {
        Optional<Hospital> hospitalOpt = hospitalRepository.findById(id);
        if (hospitalOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Hospital not found.");
        }
        Hospital hospital = hospitalOpt.get();
        hospital.setVerificationStatus("REJECTED");
        hospital.setVerifiedAt(LocalDateTime.now());
        hospital.setVerifiedBy(adminUsername);
        return ResponseEntity.ok(hospitalRepository.save(hospital));
    }
}