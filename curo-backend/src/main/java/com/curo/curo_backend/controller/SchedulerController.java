package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.TickRequest;
import com.curo.curo_backend.dto.TickResponse;
import com.curo.curo_backend.service.ApiException;
import com.curo.curo_backend.service.DoseAlertService;
import com.curo.curo_backend.service.InternalKeyVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Set;

/**
 * Called by an EventBridge rule every 5 minutes, never by an app. It carries no JWT; the caller proves itself with the
 * shared secret in X-Internal-Key (env INTERNAL_API_KEY). Repeated or overlapping calls are harmless.
 */
@RestController
@RequestMapping("/internal/scheduler")
public class SchedulerController {

    private static final Logger log = LoggerFactory.getLogger(SchedulerController.class);
    private static final Set<String> TASKS = Set.of("REMINDERS", "MISSED", "ALL");

    @Autowired
    private InternalKeyVerifier keyVerifier;

    @Autowired
    private DoseAlertService doseAlertService;

    @PostMapping("/dose-tick")
    public TickResponse doseTick(@RequestHeader(value = "X-Internal-Key", required = false) String key,
                                 @RequestBody(required = false) TickRequest request) {
        if (!keyVerifier.matches(key)) {
            // same answer whether the key is missing, wrong, or not configured at all
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_KEY", "Missing or invalid internal key.");
        }
        String task = request == null || request.getTask() == null || request.getTask().isBlank()
                ? "ALL" : request.getTask().trim().toUpperCase(Locale.ROOT);
        if (!TASKS.contains(task)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "task must be REMINDERS, MISSED or ALL.");
        }

        long reminders = 0;
        long missed = 0;
        long alerts = 0;
        if (!"MISSED".equals(task)) {
            reminders = doseAlertService.sendReminders();
            alerts += reminders;
        }
        if (!"REMINDERS".equals(task)) {
            DoseAlertService.MissedResult result = doseAlertService.processMissed();
            missed = result.markedMissed();
            alerts += result.alertsCreated();
        }
        log.info("dose tick: {} reminder(s), {} dose(s) marked MISSED, {} alert(s) created", reminders, missed, alerts);
        return new TickResponse(reminders, missed, alerts);
    }
}
