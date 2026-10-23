package com.curo.curo_backend.service;

import com.curo.curo_backend.model.AccessGrant;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.AuditOutcome;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.GrantStatus;
import com.curo.curo_backend.repository.AccessGrantRepository;
import com.curo.curo_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The one check every endpoint that returns or changes a patient's data must call before it touches the data:
 * the caller must have a suitable role AND an active, unexpired grant on that patient covering the scope needed.
 * Otherwise it throws 403 NO_ACTIVE_GRANT. The reason for a refusal is never given, so an unknown CareID and
 * an ungranted one look the same. ADMIN, SUPER_ADMIN and other patients never pass.
 *
 * Every attempt is written to audit_logs. If a successful access cannot be recorded, it is refused (500).
 */
@Service
public class AccessGuard {

    private static final Set<String> GRANTABLE_ROLES = Set.of("DOCTOR", "PHARMACY", "CAREGIVER");

    /** The patient whose data may now be used, and the grant that allows it. */
    public record Granted(AppUser patient, AccessGrant grant) {
    }

    @Autowired
    private CurrentUserService currentUser;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccessGrantRepository grantRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private Clock clock;

    /**
     * @param careId     the patient being accessed
     * @param scope      what part of the data the endpoint uses
     * @param action     READ or WRITE
     * @param resource   what kind of data, for the audit log (e.g. PROFILE, PRESCRIPTION)
     * @param resourceId id of the specific record, or null
     */
    public Granted require(String careId, GrantScope scope, AuditAction action, String resource, String resourceId) {
        AppUser actor = currentUser.require();
        Instant now = clock.instant();

        AppUser patient = careId == null ? null : userRepository.findByCareId(careId.trim().toUpperCase(Locale.ROOT))
                .filter(p -> "PATIENT".equals(p.getRole()) && p.getDeletedAt() == null).orElse(null);

        Optional<AccessGrant> usable = Optional.empty();
        if (patient != null && GRANTABLE_ROLES.contains(actor.getRole())) {
            usable = findUsableGrant(patient, actor, scope, now);
        }

        if (usable.isEmpty()) {
            if (patient != null) {
                auditService.recordQuietly(patient, actor, action, resource, resourceId, null, AuditOutcome.DENIED);
            }
            throw new ApiException(HttpStatus.FORBIDDEN, "NO_ACTIVE_GRANT",
                    "You do not have an active grant for this patient's data.");
        }

        try {
            auditService.record(patient, actor, action, resource, resourceId, usable.get().getId(), AuditOutcome.ALLOWED);
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIT_UNAVAILABLE",
                    "Access could not be recorded, so it was not allowed. Try again.");
        }
        return new Granted(patient, usable.get());
    }

    private Optional<AccessGrant> findUsableGrant(AppUser patient, AppUser actor, GrantScope scope, Instant now) {
        Optional<AccessGrant> match = Optional.empty();
        for (AccessGrant grant : grantRepository.findByPatientIdAndGranteeIdAndStatus(patient.getId(), actor.getId(), GrantStatus.ACTIVE)) {
            if (grant.isLapsedAt(now)) {
                expire(grant, patient);
            } else if (match.isEmpty() && actor.getRole().equals(grant.getRole())
                    && grant.getScope() != null && grant.getScope().contains(scope)) {
                match = Optional.of(grant);
            }
        }
        return match;
    }

    // Stored status catches up with the clock the first time an expired grant is seen
    private void expire(AccessGrant grant, AppUser patient) {
        try {
            grant.setStatus(GrantStatus.EXPIRED);
            grantRepository.save(grant);
            auditService.recordQuietly(patient, null, AuditAction.GRANT_EXPIRED, "ACCESS_GRANT", grant.getId(),
                    grant.getId(), AuditOutcome.ALLOWED);
        } catch (RuntimeException ignored) {
            // the grant is still refused; the status is fixed on a later request
        }
    }
}
