package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.AuditEntryView;
import com.curo.curo_backend.dto.PagedResponse;
import com.curo.curo_backend.dto.ShareCodeRequest;
import com.curo.curo_backend.dto.ShareCodeResponse;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.service.AccessGrantService;
import com.curo.curo_backend.service.ApiException;
import com.curo.curo_backend.service.AuditService;
import com.curo.curo_backend.service.CurrentUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** The signed-in patient's own view of who can see their data. */
@RestController
@RequestMapping("/patient/me")
public class PatientAccessController {

    @Autowired
    private AccessGrantService accessGrantService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private CurrentUserService currentUser;

    @PostMapping("/share-codes")
    public ResponseEntity<ShareCodeResponse> createShareCode(@RequestBody ShareCodeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accessGrantService.createShareCode(request));
    }

    @GetMapping("/access-log")
    public PagedResponse<AuditEntryView> accessLog(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String actorRole,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AppUser patient = currentUser.require();
        if (!"PATIENT".equals(patient.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE", "Only a patient has an access log.");
        }
        return auditService.listForPatient(patient.getId(), from, to, actorRole, page, size);
    }
}
