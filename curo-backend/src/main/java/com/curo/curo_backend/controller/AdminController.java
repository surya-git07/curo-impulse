package com.curo.curo_backend.controller;

import org.springframework.context.annotation.Profile;
import com.curo.curo_backend.model.DoctorLeave;
import com.curo.curo_backend.repository.DoctorLeaveRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Profile("legacy-jpa")
@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired private DoctorLeaveRepository doctorLeaveRepository;

    @GetMapping("/leave/active")
    public ResponseEntity<List<DoctorLeave>> getActiveLeaves() {
        return ResponseEntity.ok(doctorLeaveRepository.findByStatus("ACTIVE"));
    }
}