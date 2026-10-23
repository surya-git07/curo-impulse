package com.curo.curo_backend.controller;

import org.springframework.context.annotation.Profile;
import com.curo.curo_backend.model.Staff;
import com.curo.curo_backend.repository.StaffRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Profile("legacy-jpa")
@RestController
@RequestMapping("/doctor")
public class DoctorProfileController {

    @Autowired private StaffRepository staffRepository;

    @GetMapping("/profile")
    public ResponseEntity<?> getMyProfile(Authentication auth) {
        String username = auth.getName();
        Optional<Staff> staffOpt = staffRepository.findByUsername(username);
        if (staffOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Staff account not found.");
        }
        Staff self = staffOpt.get();

        Map<String, Object> profile = new HashMap<>();
        profile.put("fullName", self.getFullName());
        profile.put("username", self.getUsername());
        profile.put("specialization", self.getSpecialization());
        profile.put("hprId", self.getHprId());
        profile.put("hprVerified", self.getHprVerified());

        if (self.getHprId() != null) {
            Map<String, Object> hprDetails = new HashMap<>();
            hprDetails.put("qualification", "MBBS, MD");
            hprDetails.put("registrationNumber", "REG-" + self.getHprId().replaceAll("[^A-Za-z0-9]", ""));
            hprDetails.put("registrationCouncil", "Tamil Nadu Medical Council");
            hprDetails.put("registeredSince", "2015");
            hprDetails.put("validTill", "2027-12-31");
            profile.put("hprDetails", hprDetails);
        }

        return ResponseEntity.ok(profile);
    }
}