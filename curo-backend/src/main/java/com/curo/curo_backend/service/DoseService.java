package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.DoseView;
import com.curo.curo_backend.dto.PagedResponse;
import com.curo.curo_backend.dto.TakenRequest;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.DoseLog;
import com.curo.curo_backend.model.DoseStatus;
import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.repository.DoseLogRepository;
import com.curo.curo_backend.repository.DoseQueries;
import com.curo.curo_backend.repository.PrescriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** The signed-in patient's own doses: today's list, marking one taken, and the history. */
@Service
public class DoseService {

    @Value("${app.time-zone:Asia/Kolkata}")
    private String timeZone;

    @Autowired
    private CurrentUserService currentUser;

    @Autowired
    private DoseLogRepository doseLogRepository;

    @Autowired
    private DoseUpdates doseUpdates;

    @Autowired
    private DoseScheduleService doseScheduleService;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private AccessGuard accessGuard;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private Clock clock;

    /** Doses due today (local day), earliest first. */
    public List<DoseView> today() {
        AppUser patient = requirePatient();
        ZoneId zone = ZoneId.of(timeZone);
        LocalDate day = clock.instant().atZone(zone).toLocalDate();
        Instant from = day.atStartOfDay(zone).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(zone).toInstant();
        return views(doseLogRepository.findForPatientBetween(
                patient.getId(), from, to));
    }

    /**
     * Someone else dose schedule (a caregiver, or a doctor): needs an active grant covering MEDICATIONS, and the read is audited.
     * Defaults to the next 7 days from the start of today (local time); at most 31 days at a time.
     */
    public List<DoseView> scheduleFor(String careId, Instant from, Instant to) {
        AccessGuard.Granted granted = accessGuard.require(careId, GrantScope.MEDICATIONS, AuditAction.READ, "DOSE_SCHEDULE", careId);
        ZoneId zone = ZoneId.of(timeZone);
        Instant start = from != null ? from : clock.instant().atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
        Instant end = to != null ? to : start.plus(Duration.ofDays(7));
        if (!end.isAfter(start) || Duration.between(start, end).compareTo(Duration.ofDays(31)) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "from must be before to, and the range at most 31 days.");
        }
        return views(doseLogRepository.findForPatientBetween(
                granted.patient().getId(), start, end));
    }

    public DoseView markTaken(String doseId, TakenRequest request) {
        AppUser patient = requirePatient();
        Instant now = clock.instant();

        // A dose that belongs to someone else looks exactly like one that does not exist
        DoseLog dose = doseLogRepository.findById(doseId).filter(d -> patient.getId().equals(d.getPatientId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOSE_NOT_FOUND", "No such dose."));
        if (dose.getStatus() == DoseStatus.TAKEN) {
            throw alreadyTaken();
        }

        Instant takenAt = request == null || request.getTakenAt() == null ? now : request.getTakenAt();
        if (takenAt.isAfter(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIME", "takenAt cannot be in the future.");
        }
        // late = more than the grace period after it was due, whether or not the job has marked it MISSED yet
        boolean late = dose.getStatus() == DoseStatus.MISSED
                || takenAt.isAfter(dose.getScheduledAt().plus(Duration.ofMinutes(doseScheduleService.graceMinutes())));

        // Atomic: if the patient double-taps, or taps while the job runs, exactly one request wins
        if (!doseUpdates.markTaken(doseId, takenAt, late, patient.getId())) {
            throw alreadyTaken();
        }
        dose.setStatus(DoseStatus.TAKEN);
        dose.setTakenAt(takenAt);
        dose.setLate(late);
        dose.setTakenByUserId(patient.getId());
        return views(List.of(dose)).get(0);
    }

    /** History, newest first. Without a status filter it shows what has happened (TAKEN and MISSED); ask for UPCOMING to see ahead. */
    public PagedResponse<DoseView> log(Instant from, Instant to, String status, int page, int size) {
        AppUser patient = requirePatient();
        if (page < 0 || size < 1 || size > AuditService.MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "page must be 0 or more and size between 1 and " + AuditService.MAX_PAGE_SIZE + ".");
        }
        List<DoseStatus> statuses = (status == null || status.isBlank())
                ? List.of(DoseStatus.TAKEN, DoseStatus.MISSED)
                : List.of(parseStatus(status));
        Query query = DoseQueries.log(patient.getId(), from, to, statuses);
        long total = mongoTemplate.count(query, DoseLog.class);
        query.with(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "scheduledAt")));
        List<DoseView> items = views(mongoTemplate.find(query, DoseLog.class));
        return new PagedResponse<>(page, size, total, items);
    }

    private DoseStatus parseStatus(String status) {
        try {
            return DoseStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "status must be UPCOMING, TAKEN or MISSED.");
        }
    }

    private ApiException alreadyTaken() {
        return new ApiException(HttpStatus.CONFLICT, "DOSE_ALREADY_TAKEN", "This dose is already marked as taken.");
    }

    private AppUser requirePatient() {
        AppUser user = currentUser.require();
        if (!"PATIENT".equals(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE", "Only a patient has doses of their own.");
        }
        return user;
    }

    // drug and dose text live on the prescription, so they are never out of date
    private List<DoseView> views(List<DoseLog> doses) {
        List<String> ids = doses.stream().map(DoseLog::getPrescriptionId).distinct().toList();
        Map<String, Prescription> byId = ids.isEmpty() ? Map.of() : prescriptionRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Prescription::getId, p -> p));
        return doses.stream().map(d -> {
            Prescription p = byId.get(d.getPrescriptionId());
            return new DoseView(d.getId(), d.getPrescriptionId(), p == null ? null : p.getDrug(), p == null ? null : p.getDose(),
                    d.getScheduledAt(), d.getStatus(), d.getTakenAt(), d.isLate(),
                    d.getTakenByUserId() == null ? null : new DoseView.MarkedBy(d.getTakenByUserId(), "PATIENT"));
        }).toList();
    }
}
