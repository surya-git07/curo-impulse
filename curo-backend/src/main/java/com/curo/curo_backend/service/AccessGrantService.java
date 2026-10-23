package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.AccessGrantView;
import com.curo.curo_backend.dto.AccessRequest;
import com.curo.curo_backend.dto.ActorView;
import com.curo.curo_backend.dto.ApproveRequest;
import com.curo.curo_backend.dto.PagedResponse;
import com.curo.curo_backend.dto.ShareCodeRequest;
import com.curo.curo_backend.dto.ShareCodeResponse;
import com.curo.curo_backend.model.AccessGrant;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.AuditOutcome;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.GrantStatus;
import com.curo.curo_backend.model.ShareCode;
import com.curo.curo_backend.repository.AccessGrantRepository;
import com.curo.curo_backend.repository.UserRepository;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Request, approve, revoke and list AccessGrants (docs/API_CONTRACT.md section 3). */
@Service
public class AccessGrantService {

    private static final String PATIENT = "PATIENT";
    private static final Set<String> REQUESTER_ROLES = Set.of("DOCTOR", "PHARMACY");

    @Value("${access.grant.default-hours:24}")
    private int defaultHours;

    @Value("${access.grant.max-hours:720}")
    private int maxHours;

    @Autowired
    private AccessGrantRepository grantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CurrentUserService currentUser;

    @Autowired
    private ShareCodeService shareCodeService;

    @Autowired
    private ShareCodeAttemptLimiter attemptLimiter;

    @Autowired
    private AuditService auditService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private Clock clock;

    // ---- patient: share code

    public ShareCodeResponse createShareCode(ShareCodeRequest request) {
        AppUser patient = requireRole(PATIENT, "Only a patient can create a share code.");
        List<GrantScope> scope = GrantScopes.parse(request.getScope());
        int hours = hours(request.getDurationHours(), defaultHours);

        ShareCode code = shareCodeService.create(patient, scope, hours);
        return new ShareCodeResponse(code.getCode(), code.getExpiresAt());
    }

    // ---- clinician / pharmacy: request

    public AccessGrantView requestAccess(AccessRequest request) {
        AppUser requester = currentUser.require();
        if (!REQUESTER_ROLES.contains(requester.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE", "Only a DOCTOR or PHARMACY can request access.");
        }
        boolean hasCareId = notBlank(request.getCareId());
        boolean hasCode = notBlank(request.getShareCode());
        if (hasCareId == hasCode) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Send exactly one of careId or shareCode.");
        }
        return hasCode ? redeemShareCode(requester, request) : requestByCareId(requester, request);
    }

    private AccessGrantView requestByCareId(AppUser requester, AccessRequest request) {
        List<GrantScope> scope = GrantScopes.parse(request.getScope());
        int hours = hours(request.getRequestedDurationHours(), defaultHours);
        String careId = request.getCareId().trim().toUpperCase(Locale.ROOT);
        Instant now = clock.instant();

        Optional<AppUser> patient = userRepository.findByCareId(careId)
                .filter(p -> PATIENT.equals(p.getRole()) && p.getDeletedAt() == null);

        AccessGrant grant = newGrant(requester, careId, scope, request.getReason(), now);
        grant.setStatus(GrantStatus.PENDING);
        grant.setRequestedDurationHours(hours);

        if (patient.isEmpty()) {
            // Same 201 as a real request, so CareIDs cannot be probed. Nothing is stored.
            grant.setId(new ObjectId().toHexString());
            return toView(grant, now);
        }

        grant.setPatientId(patient.get().getId());
        failIfGrantExists(patient.get(), requester, now);
        AccessGrant saved = grantRepository.save(grant);
        auditService.recordQuietly(patient.get(), requester, AuditAction.GRANT_REQUESTED, "ACCESS_GRANT", saved.getId(),
                saved.getId(), AuditOutcome.ALLOWED);
        return toView(saved, now);
    }

    private AccessGrantView redeemShareCode(AppUser requester, AccessRequest request) {
        attemptLimiter.checkAllowed(requester.getId());
        Instant now = clock.instant();
        String codeText = request.getShareCode().trim();

        Optional<ShareCode> found = ShareCodeService.isWellFormed(codeText) ? shareCodeService.peek(codeText) : Optional.empty();
        if (found.isEmpty()) {
            attemptLimiter.recordFailure(requester.getId());
            throw new ApiException(HttpStatus.NOT_FOUND, "INVALID_SHARE_CODE", "That share code is not valid.");
        }
        if (shareCodeService.isExpired(found.get())) {
            shareCodeService.consume(codeText);
            attemptLimiter.recordFailure(requester.getId());
            throw new ApiException(HttpStatus.GONE, "SHARE_CODE_EXPIRED", "That share code has expired.");
        }

        AppUser patient = userRepository.findById(found.get().getPatientId())
                .filter(p -> p.getDeletedAt() == null).orElse(null);
        if (patient == null) {
            shareCodeService.consume(codeText);
            attemptLimiter.recordFailure(requester.getId());
            throw new ApiException(HttpStatus.NOT_FOUND, "INVALID_SHARE_CODE", "That share code is not valid.");
        }

        failIfGrantExists(patient, requester, now);

        Optional<ShareCode> consumed = shareCodeService.consume(codeText);
        if (consumed.isEmpty()) {
            // someone else redeemed it between the lookup and now
            attemptLimiter.recordFailure(requester.getId());
            throw new ApiException(HttpStatus.NOT_FOUND, "INVALID_SHARE_CODE", "That share code is not valid.");
        }

        AccessGrant grant = newGrant(requester, patient.getCareId(), consumed.get().getScope(), request.getReason(), now);
        grant.setPatientId(patient.getId());
        grant.setStatus(GrantStatus.ACTIVE);
        grant.setApprovedAt(now);
        grant.setExpiresAt(now.plus(Duration.ofHours(consumed.get().getDurationHours())));
        AccessGrant saved = grantRepository.save(grant);

        attemptLimiter.reset(requester.getId());
        auditService.recordQuietly(patient, requester, AuditAction.GRANT_REQUESTED, "ACCESS_GRANT", saved.getId(),
                saved.getId(), AuditOutcome.ALLOWED);
        // the patient approved in advance by handing over the code
        auditService.recordQuietly(patient, patient, AuditAction.GRANT_APPROVED, "ACCESS_GRANT", saved.getId(),
                saved.getId(), AuditOutcome.ALLOWED);
        return toView(saved, now);
    }

    // ---- patient: approve / revoke

    public AccessGrantView approve(String grantId, ApproveRequest request) {
        AppUser patient = requireRole(PATIENT, "Only the patient can approve access.");
        Instant now = clock.instant();
        AccessGrant grant = find(grantId);
        if (!patient.getId().equals(grant.getPatientId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "This grant is not for your data.");
        }
        if (grant.getStatus() != GrantStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_PENDING", "Only a pending request can be approved.");
        }

        List<GrantScope> scope = grant.getScope();
        if (request != null && request.getScope() != null) {
            List<GrantScope> narrowed = GrantScopes.parse(request.getScope());
            if (!scope.containsAll(narrowed)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SCOPE_NOT_SUBSET", "Approval can only narrow the requested scope.");
            }
            scope = narrowed;
        }
        int fallback = grant.getRequestedDurationHours() != null ? grant.getRequestedDurationHours() : defaultHours;
        int hours = hours(request == null ? null : request.getDurationHours(), fallback);

        grant.setScope(scope);
        grant.setStatus(GrantStatus.ACTIVE);
        grant.setApprovedAt(now);
        grant.setExpiresAt(now.plus(Duration.ofHours(hours)));
        AccessGrant saved = grantRepository.save(grant);

        auditService.recordQuietly(patient, patient, AuditAction.GRANT_APPROVED, "ACCESS_GRANT", saved.getId(),
                saved.getId(), AuditOutcome.ALLOWED);
        return toView(saved, now);
    }

    /** The patient, or the person who holds the grant, can end it. On a pending request this is the deny action. */
    public AccessGrantView revoke(String grantId) {
        AppUser user = currentUser.require();
        Instant now = clock.instant();
        AccessGrant grant = find(grantId);
        boolean owner = user.getId().equals(grant.getPatientId());
        boolean holder = user.getId().equals(grant.getGranteeId());
        if (!owner && !holder) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "This grant is not yours.");
        }

        if (grant.getStatus() == GrantStatus.PENDING || grant.isUsableAt(now)) {
            grant.setStatus(GrantStatus.REVOKED);
            grant.setRevokedAt(now);
            grant = grantRepository.save(grant);
            AppUser patient = owner ? user : userRepository.findById(grant.getPatientId()).orElse(null);
            if (patient != null) {
                auditService.recordQuietly(patient, user, AuditAction.GRANT_REVOKED, "ACCESS_GRANT", grant.getId(),
                        grant.getId(), AuditOutcome.ALLOWED);
            }
        }
        return toView(grant, now);
    }

    // ---- list

    public PagedResponse<AccessGrantView> list(String status, String careId, int page, int size) {
        AppUser user = currentUser.require();
        boolean patient = PATIENT.equals(user.getRole());
        if (!patient && !Set.of("DOCTOR", "PHARMACY", "CAREGIVER").contains(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE", "Your role has no access grants.");
        }
        if (page < 0 || size < 1 || size > AuditService.MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "page must be 0 or more and size between 1 and " + AuditService.MAX_PAGE_SIZE + ".");
        }
        Instant now = clock.instant();

        List<Criteria> parts = new ArrayList<>();
        parts.add(Criteria.where(patient ? "patientId" : "granteeId").is(user.getId()));
        if (notBlank(careId)) {
            parts.add(Criteria.where("careId").is(careId.trim().toUpperCase(Locale.ROOT)));
        }
        if (notBlank(status)) {
            parts.add(statusCriteria(parseStatus(status), now));
        }
        Query query = new Query(new Criteria().andOperator(parts));
        long total = mongoTemplate.count(query, AccessGrant.class);
        query.with(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<AccessGrantView> items = mongoTemplate.find(query, AccessGrant.class).stream().map(g -> toView(g, now)).toList();
        return new PagedResponse<>(page, size, total, items);
    }

    // ---- helpers

    private Criteria statusCriteria(GrantStatus wanted, Instant now) {
        return switch (wanted) {
            case PENDING, REVOKED -> Criteria.where("status").is(wanted);
            case ACTIVE -> new Criteria().andOperator(Criteria.where("status").is(GrantStatus.ACTIVE),
                    new Criteria().orOperator(Criteria.where("expiresAt").is(null), Criteria.where("expiresAt").gt(now)));
            // stored EXPIRED, or ACTIVE whose time has run out but has not been rewritten yet
            case EXPIRED -> new Criteria().orOperator(Criteria.where("status").is(GrantStatus.EXPIRED),
                    new Criteria().andOperator(Criteria.where("status").is(GrantStatus.ACTIVE), Criteria.where("expiresAt").lte(now)));
        };
    }

    private GrantStatus parseStatus(String status) {
        try {
            return GrantStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "status must be PENDING, ACTIVE, REVOKED or EXPIRED.");
        }
    }

    private void failIfGrantExists(AppUser patient, AppUser requester, Instant now) {
        grantRepository.findByPatientIdAndGranteeIdAndStatusIn(patient.getId(), requester.getId(),
                        List.of(GrantStatus.PENDING, GrantStatus.ACTIVE)).stream()
                .filter(g -> g.getStatus() == GrantStatus.PENDING || g.isUsableAt(now))
                .findFirst()
                .ifPresent(g -> {
                    throw new GrantExistsException(g.getId());
                });
    }

    private AccessGrant newGrant(AppUser requester, String careId, List<GrantScope> scope, String reason, Instant now) {
        AccessGrant grant = new AccessGrant();
        grant.setCareId(careId);
        grant.setGranteeId(requester.getId());
        grant.setRole(requester.getRole());
        grant.setGranteeName(requester.getName());
        grant.setGranteeFacilityName(requester.getFacilityName());
        grant.setScope(scope);
        grant.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        grant.setCreatedAt(now);
        return grant;
    }

    private AccessGrant find(String id) {
        return grantRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GRANT_NOT_FOUND", "No such grant."));
    }

    private AppUser requireRole(String role, String message) {
        AppUser user = currentUser.require();
        if (!role.equals(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE", message);
        }
        return user;
    }

    private int hours(Integer requested, int fallback) {
        int hours = requested == null ? fallback : requested;
        if (hours < 1 || hours > maxHours) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DURATION", "Duration must be between 1 and " + maxHours + " hours.");
        }
        return hours;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    static String typeFor(String role) {
        return switch (role) {
            case "DOCTOR" -> "CLINICAL";
            case "PHARMACY" -> "PHARMACY";
            default -> "CAREGIVER";
        };
    }

    AccessGrantView toView(AccessGrant g, Instant now) {
        return new AccessGrantView(g.getId(), g.getCareId(), typeFor(g.getRole()), g.getScope(), g.effectiveStatus(now),
                new ActorView(g.getGranteeId(), g.getRole(), g.getGranteeName(), g.getGranteeFacilityName()),
                g.getReason(), g.getCreatedAt(), g.getApprovedAt(), g.getExpiresAt(), g.getRevokedAt());
    }
}
