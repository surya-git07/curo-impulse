package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.QueueEntryView;
import com.curo.curo_backend.dto.QueueView;
import com.curo.curo_backend.service.QueueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/doctor/queue")
public class DoctorQueueController {

    @Autowired
    private QueueService queueService;

    @GetMapping
    public ResponseEntity<QueueView> today() {
        return ResponseEntity.ok(queueService.todaysQueue());
    }

    @PatchMapping("/{id}/done")
    public ResponseEntity<QueueEntryView> done(@PathVariable String id) {
        return ResponseEntity.ok(queueService.markDone(id));
    }
}
