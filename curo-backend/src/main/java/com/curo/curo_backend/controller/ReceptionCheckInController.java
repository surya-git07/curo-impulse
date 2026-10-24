package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.DoctorOption;
import com.curo.curo_backend.dto.QueueCheckInRequest;
import com.curo.curo_backend.dto.QueueEntryView;
import com.curo.curo_backend.service.QueueService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/reception")
public class ReceptionCheckInController {

    @Autowired
    private QueueService queueService;

    @GetMapping("/doctors")
    public ResponseEntity<List<DoctorOption>> doctors() {
        return ResponseEntity.ok(queueService.verifiedDoctors());
    }

    @PostMapping("/check-in")
    public ResponseEntity<QueueEntryView> checkIn(@Valid @RequestBody QueueCheckInRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(queueService.checkIn(request.getCareId(), request.getDoctorId()));
    }
}
