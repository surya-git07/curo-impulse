package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.DoseView;
import com.curo.curo_backend.dto.PagedResponse;
import com.curo.curo_backend.dto.TakenRequest;
import com.curo.curo_backend.service.DoseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
public class DoseController {

    @Autowired
    private DoseService doseService;

    @GetMapping("/patient/me/doses/today")
    public List<DoseView> today() {
        return doseService.today();
    }

    @GetMapping("/patient/me/dose-log")
    public PagedResponse<DoseView> log(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return doseService.log(from, to, status, page, size);
    }

    // A caregiver or doctor reading a patient schedule: needs an active grant (403 NO_ACTIVE_GRANT otherwise)
    @GetMapping("/patient/{careId}/dose-schedule")
    public List<DoseView> schedule(@PathVariable String careId,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return doseService.scheduleFor(careId, from, to);
    }

    @PostMapping("/doses/{id}/taken")
    public DoseView taken(@PathVariable String id, @RequestBody(required = false) TakenRequest request) {
        return doseService.markTaken(id, request);
    }
}
