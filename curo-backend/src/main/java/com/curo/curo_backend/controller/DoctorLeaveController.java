package com.curo.curo_backend.controller;

import org.springframework.context.annotation.Profile;
import com.curo.curo_backend.model.DoctorLeave;
import com.curo.curo_backend.model.Staff;
import com.curo.curo_backend.repository.DoctorLeaveRepository;
import com.curo.curo_backend.repository.StaffRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Profile("legacy-jpa")
@RestController
@RequestMapping("/doctor/leave")
public class DoctorLeaveController {

    @Autowired private DoctorLeaveRepository doctorLeaveRepository;
    @Autowired private StaffRepository staffRepository;

    @PostMapping("/mark")
    public ResponseEntity<?> markSelfLeave(@RequestBody Map<String, Object> body, Authentication auth) {
        String username = auth.getName();
        Optional<Staff> staffOpt = staffRepository.findByUsername(username);
        if (staffOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Staff account not found.");
        }
        Staff self = staffOpt.get();

        Integer substituteId = (Integer) body.get("substituteDoctorId");
        if (substituteId != null && substituteId.equals(self.getId())) {
            return ResponseEntity.badRequest().body("You cannot assign yourself as your own substitute.");
        }

        DoctorLeave leave = new DoctorLeave();
        leave.setDoctorId(self.getId());
        leave.setLeaveFrom(LocalDate.parse((String) body.get("leaveFrom")));
        leave.setLeaveUntil(LocalDate.parse((String) body.get("leaveUntil")));
        leave.setReason((String) body.get("reason"));
        leave.setSubstituteDoctorId(substituteId);
        leave.setStatus("ACTIVE");

        DoctorLeave saved = doctorLeaveRepository.save(leave);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/mine")
    public ResponseEntity<?> getMyActiveLeave(Authentication auth) {
        String username = auth.getName();
        Optional<Staff> staffOpt = staffRepository.findByUsername(username);
        if (staffOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Staff account not found.");
        }
        Integer myId = staffOpt.get().getId();

        LocalDate today = LocalDate.now();
        Optional<DoctorLeave> activeLeave = doctorLeaveRepository.findByDoctorIdAndStatus(myId, "ACTIVE").stream()
                .filter(l -> !today.isBefore(l.getLeaveFrom()) && !today.isAfter(l.getLeaveUntil()))
                .findFirst();

        return ResponseEntity.ok(activeLeave.orElse(null));
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<?> endMyLeave(@PathVariable Integer id, Authentication auth) {
        String username = auth.getName();
        Optional<Staff> staffOpt = staffRepository.findByUsername(username);
        if (staffOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Staff account not found.");
        }

        Optional<DoctorLeave> leaveOpt = doctorLeaveRepository.findById(id);
        if (leaveOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Leave record not found.");
        }
        DoctorLeave leave = leaveOpt.get();
        if (!leave.getDoctorId().equals(staffOpt.get().getId())) {
            return ResponseEntity.status(403).body("You can only end your own leave.");
        }
        leave.setStatus("ENDED");
        doctorLeaveRepository.save(leave);
        return ResponseEntity.ok(leave);
    }
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

        // Pull the fuller professional profile from the mock HPR registry using the stored HPR ID
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