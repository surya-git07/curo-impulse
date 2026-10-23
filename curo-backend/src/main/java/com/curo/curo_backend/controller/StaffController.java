package com.curo.curo_backend.controller;

import org.springframework.context.annotation.Profile;
import com.curo.curo_backend.model.DoctorLeave;
import com.curo.curo_backend.model.Staff;
import com.curo.curo_backend.repository.DoctorLeaveRepository;
import com.curo.curo_backend.repository.StaffRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@Profile("legacy-jpa")
@RestController
@RequestMapping("/staff")
public class StaffController {

    @Autowired private StaffRepository staffRepository;
    @Autowired private DoctorLeaveRepository doctorLeaveRepository;

    @GetMapping("/doctors/{hospitalId}")
    public ResponseEntity<?> getDoctorsWithAvailability(@PathVariable Integer hospitalId) {
        List<Staff> doctors = staffRepository.findAll().stream()
                .filter(s -> "DOCTOR".equals(s.getRole()) && hospitalId.equals(s.getHospitalId()))
                .toList();

        LocalDate today = LocalDate.now();
        List<DoctorLeave> activeLeaves = doctorLeaveRepository.findByStatus("ACTIVE").stream()
                .filter(l -> !today.isBefore(l.getLeaveFrom()) && !today.isAfter(l.getLeaveUntil()))
                .toList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Staff doc : doctors) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("id", doc.getId());
            entry.put("fullName", doc.getFullName());
            entry.put("specialization", doc.getSpecialization());

            Optional<DoctorLeave> onLeave = activeLeaves.stream()
                    .filter(l -> l.getDoctorId().equals(doc.getId()))
                    .findFirst();

            if (onLeave.isPresent()) {
                entry.put("available", false);
                entry.put("leaveUntil", onLeave.get().getLeaveUntil());
                entry.put("reason", onLeave.get().getReason());

                Integer subId = onLeave.get().getSubstituteDoctorId();
                if (subId != null) {
                    staffRepository.findById(subId).ifPresent(sub -> {
                        entry.put("substituteId", sub.getId());
                        entry.put("substituteName", sub.getFullName());
                    });
                }
            } else {
                entry.put("available", true);
            }
            result.add(entry);
        }
        return ResponseEntity.ok(result);
    }
}