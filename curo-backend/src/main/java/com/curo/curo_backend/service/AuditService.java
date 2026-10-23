package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.ActorView;
import com.curo.curo_backend.dto.AuditEntryView;
import com.curo.curo_backend.dto.PagedResponse;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.AuditLog;
import com.curo.curo_backend.model.AuditOutcome;
import com.curo.curo_backend.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Writes and reads audit_logs: who touched which patient's data, and when. */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    public static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private Clock clock;

    /**
     * Writes one entry and lets a failure propagate. Used when data is about to be released: if the access
     * cannot be recorded it must not happen.
     *
     * @param actor null for automatic events (a grant expiring)
     */
    public void record(AppUser patient, AppUser actor, AuditAction action, String resource, String resourceId,
                       String grantId, AuditOutcome outcome) {
        auditLogRepository.save(build(patient, actor, action, resource, resourceId, grantId, outcome));
    }

    /** Same, but a failure is only logged (class name only). For denials and grant bookkeeping, which must not change the response. */
    public void recordQuietly(AppUser patient, AppUser actor, AuditAction action, String resource, String resourceId,
                              String grantId, AuditOutcome outcome) {
        try {
            record(patient, actor, action, resource, resourceId, grantId, outcome);
        } catch (RuntimeException e) {
            log.warn("audit entry {} could not be written ({})", action, e.getClass().getSimpleName());
        }
    }

    public PagedResponse<AuditEntryView> listForPatient(String patientId, Instant from, Instant to, String actorRole,
                                                        int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "page must be 0 or more and size between 1 and " + MAX_PAGE_SIZE + ".");
        }
        List<Criteria> parts = new ArrayList<>();
        parts.add(Criteria.where("patientId").is(patientId));
        if (from != null) {
            parts.add(Criteria.where("at").gte(from));
        }
        if (to != null) {
            parts.add(Criteria.where("at").lt(to));
        }
        if (actorRole != null && !actorRole.isBlank()) {
            parts.add(Criteria.where("actorRole").is(actorRole.trim().toUpperCase(Locale.ROOT)));
        }
        Query query = new Query(new Criteria().andOperator(parts));
        long total = mongoTemplate.count(query, AuditLog.class);
        query.with(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "at")));
        List<AuditEntryView> items = mongoTemplate.find(query, AuditLog.class).stream().map(AuditService::toView).toList();
        return new PagedResponse<>(page, size, total, items);
    }

    private AuditLog build(AppUser patient, AppUser actor, AuditAction action, String resource, String resourceId,
                           String grantId, AuditOutcome outcome) {
        AuditLog entry = new AuditLog();
        entry.setPatientId(patient.getId());
        entry.setCareId(patient.getCareId());
        if (actor == null) {
            entry.setActorRole("SYSTEM");
            entry.setActorName("system");
        } else {
            entry.setActorUserId(actor.getId());
            entry.setActorName(actor.getName());
            entry.setActorRole(actor.getRole());
            entry.setActorFacilityName(actor.getFacilityName());
        }
        entry.setAction(action);
        entry.setResource(resource);
        entry.setResourceId(resourceId);
        entry.setGrantId(grantId);
        entry.setOutcome(outcome);
        entry.setAt(clock.instant());
        return entry;
    }

    private static AuditEntryView toView(AuditLog e) {
        return new AuditEntryView(e.getId(), e.getAt(),
                new ActorView(e.getActorUserId(), e.getActorRole(), e.getActorName(), e.getActorFacilityName()),
                e.getAction(), e.getResource(), e.getResourceId(), e.getGrantId(), e.getOutcome());
    }
}
