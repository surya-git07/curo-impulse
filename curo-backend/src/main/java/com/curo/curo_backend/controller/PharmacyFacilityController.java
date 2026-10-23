package com.curo.curo_backend.controller;

import org.springframework.context.annotation.Profile;
import com.curo.curo_backend.dto.PharmacyFacilityRequest;
import com.curo.curo_backend.model.PharmacyFacility;
import com.curo.curo_backend.repository.PharmacyFacilityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Profile("legacy-jpa")
@RestController
@RequestMapping("/pharmacy-facility")
public class PharmacyFacilityController {

    @Autowired
    private PharmacyFacilityRepository repository;

    private static final Pattern GSTIN_FORMAT = Pattern.compile(
            "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$"
    );

    private boolean isValidGstinFormat(String gstin) {
        return gstin != null && GSTIN_FORMAT.matcher(gstin.toUpperCase()).matches();
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody PharmacyFacilityRequest request) {
        boolean exists = repository.findAll().stream()
                .anyMatch(f -> f.getName().equalsIgnoreCase(request.getName()));

        if (exists) {
            return ResponseEntity.status(409).body("A facility with this name already exists");
        }

        if (request.getDrugLicenseNumber() == null || request.getDrugLicenseNumber().isBlank()) {
            return ResponseEntity.badRequest().body("Drug license number is required.");
        }

        if (request.getGstin() != null && !request.getGstin().isBlank() && !isValidGstinFormat(request.getGstin())) {
            return ResponseEntity.badRequest().body("GSTIN format is invalid. Expected format: 22AAAAA0000A1Z5");
        }

        PharmacyFacility facility = new PharmacyFacility();
        facility.setName(request.getName());
        facility.setType(request.getType());
        facility.setDrugLicenseNumber(request.getDrugLicenseNumber());
        facility.setGstin(request.getGstin());
        facility.setVerificationStatus("PENDING");

        return ResponseEntity.ok(repository.save(facility));
    }

    @GetMapping("/list")
    public ResponseEntity<List<PharmacyFacility>> list() {
        return ResponseEntity.ok(repository.findAll());
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<?> verify(@PathVariable Integer id, @RequestParam String adminUsername) {
        Optional<PharmacyFacility> facilityOpt = repository.findById(id);
        if (facilityOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Facility not found.");
        }
        PharmacyFacility facility = facilityOpt.get();
        facility.setVerificationStatus("VERIFIED");
        facility.setVerifiedAt(LocalDateTime.now());
        facility.setVerifiedBy(adminUsername);
        return ResponseEntity.ok(repository.save(facility));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Integer id, @RequestParam String adminUsername) {
        Optional<PharmacyFacility> facilityOpt = repository.findById(id);
        if (facilityOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Facility not found.");
        }
        PharmacyFacility facility = facilityOpt.get();
        facility.setVerificationStatus("REJECTED");
        facility.setVerifiedAt(LocalDateTime.now());
        facility.setVerifiedBy(adminUsername);
        return ResponseEntity.ok(repository.save(facility));
    }
}