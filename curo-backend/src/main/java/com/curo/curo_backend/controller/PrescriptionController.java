package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.DiscontinueRequest;
import com.curo.curo_backend.dto.HistoryResponse;
import com.curo.curo_backend.dto.IssueRequest;
import com.curo.curo_backend.dto.IssueResponse;
import com.curo.curo_backend.dto.ModifyRequest;
import com.curo.curo_backend.dto.PrescriptionView;
import com.curo.curo_backend.service.PrescriptionService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/prescription")
public class PrescriptionController {

    @Autowired
    private PrescriptionService prescriptionService;

    @PostMapping("/issue")
    public ResponseEntity<IssueResponse> issue(@Valid @RequestBody IssueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(prescriptionService.issue(request));
    }

    @PutMapping("/{id}")
    public PrescriptionView modify(@PathVariable String id, @Valid @RequestBody ModifyRequest request) {
        return prescriptionService.modify(id, request);
    }

    @PostMapping("/{id}/discontinue")
    public PrescriptionView discontinue(@PathVariable String id, @Valid @RequestBody(required = false) DiscontinueRequest request) {
        return prescriptionService.discontinue(id, request);
    }

    @GetMapping("/{id}/history")
    public HistoryResponse history(@PathVariable String id) {
        return prescriptionService.history(id);
    }
}
