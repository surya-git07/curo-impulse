package com.curo.curo_backend.controller;

import org.springframework.context.annotation.Profile;
import com.curo.curo_backend.dto.LoginRequest;
import com.curo.curo_backend.dto.LoginResponse;
import com.curo.curo_backend.dto.StaffRegisterRequest;
import com.curo.curo_backend.model.Hospital;
import com.curo.curo_backend.model.PharmacyFacility;
import com.curo.curo_backend.model.Staff;
import com.curo.curo_backend.repository.HospitalRepository;
import com.curo.curo_backend.repository.PharmacyFacilityRepository;
import com.curo.curo_backend.repository.StaffRepository;
import com.curo.curo_backend.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Profile("legacy-jpa")
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private HospitalRepository hospitalRepository;

    @Autowired
    private PharmacyFacilityRepository pharmacyFacilityRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        Optional<Staff> staffOpt = staffRepository.findByUsername(request.getUsername());

        if (staffOpt.isEmpty()) {
            return ResponseEntity.status(401).body("Invalid username or password");
        }

        Staff staff = staffOpt.get();

        if (!passwordEncoder.matches(request.getPassword(), staff.getPasswordHash())) {
            return ResponseEntity.status(401).body("Invalid username or password");
        }

        String facilityName = "Unassigned";
        Long facilityId = null;

        if (staff.getHospitalId() != null) {
            Optional<Hospital> hospitalOpt = hospitalRepository.findById(staff.getHospitalId());
            if (hospitalOpt.isPresent()) {
                facilityName = hospitalOpt.get().getName();
                facilityId = Long.valueOf(hospitalOpt.get().getId());
            }
        } else if (staff.getPharmacyFacilityId() != null) {
            Optional<PharmacyFacility> facilityOpt = pharmacyFacilityRepository.findById(staff.getPharmacyFacilityId());
            if (facilityOpt.isPresent()) {
                facilityName = facilityOpt.get().getName();
                facilityId = Long.valueOf(facilityOpt.get().getId());
            }
        }

        String token = jwtService.generateToken(
                staff.getUsername(),
                staff.getRole(),
                facilityId,
                facilityName,
                staff.getSpecialization(),
                staff.getId()
        );

        LoginResponse response = new LoginResponse(
                token,
                staff.getUsername(),
                staff.getRole(),
                staff.getFullName(),
                facilityName
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerStaff(@RequestBody StaffRegisterRequest request) {
        if (staffRepository.findByUsername(request.getUsername()).isPresent()) {
            return ResponseEntity.status(409).body("Username already taken");
        }

        // Doctors must have a verified HPR ID before an account can be created.
        // We re-verify server-side rather than trusting the frontend's flag.
        if ("DOCTOR".equals(request.getRole())) {
            if (request.getHprId() == null || !request.getHprId().matches("[A-Za-z0-9\\-]{6,20}")) {
                return ResponseEntity.status(400).body("A valid HPR ID is required to register a doctor account.");
            }
        }

        Staff staff = new Staff();
        staff.setUsername(request.getUsername());
        staff.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        staff.setRole(request.getRole());
        staff.setFullName(request.getFullName());
        staff.setHospitalId(request.getHospitalId());
        staff.setPharmacyFacilityId(request.getPharmacyFacilityId());
        staff.setHprId(request.getHprId());
        staff.setHprVerified("DOCTOR".equals(request.getRole()));

        staffRepository.save(staff);

        return ResponseEntity.ok("Registration successful. You can now log in.");
    }
}