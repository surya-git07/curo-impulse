package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.CaregiverLinkRequest;
import com.curo.curo_backend.dto.CaregiverLinkView;
import com.curo.curo_backend.model.AccessGrant;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.AuditOutcome;
import com.curo.curo_backend.model.CaregiverLink;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.GrantStatus;
import com.curo.curo_backend.model.LinkStatus;
import com.curo.curo_backend.repository.AccessGrantRepository;
import com.curo.curo_backend.repository.CaregiverLinkRepository;
import com.curo.curo_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * A patient adds a caregiver by CareID. The caregiver can then see the patient's dose schedule and gets their missed-dose alerts,
 * and nothing more: the access grant created has only the MEDICATIONS and DOSES scopes, so a caregiver cannot prescribe or read
 * prescriptions or the profile.
 */
@Service
public class CaregiverLinkService {

    static final List<GrantScope> CAREGIVER_SCOPE = List.of(GrantScope.MEDICATIONS, GrantScope.DOSES);

    @Autowired
    private CurrentUserService currentUser;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CaregiverLinkRepository linkRepository;

    @Autowired
    private AccessGrantRepository grantRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private Clock clock;

    public CaregiverLinkView add(CaregiverLinkRequest request) {
        AppUser patient = currentUser.require();
        if (!"PATIENT".equals(patient.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE", "Only a patient can add a caregiver.");
        }
        String careId = request.getCareId().trim().toUpperCase(Locale.ROOT);
        AppUser caregiver = userRepository.findByCareId(careId)
                .filter(u -> "CAREGIVER".equals(u.getRole()) && u.getDeletedAt() == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CAREGIVER_NOT_FOUND",
                        "No caregiver account has that CareID. They need to sign up as a caregiver first."));
        if (linkRepository.existsByPatientIdAndCaregiverIdAndStatus(patient.getId(), caregiver.getId(), LinkStatus.ACTIVE)) {
            throw new ApiException(HttpStatus.CONFLICT, "LINK_EXISTS", "That caregiver is already linked.");
        }
        Instant now = clock.instant();

        AccessGrant grant = new AccessGrant();
        grant.setPatientId(patient.getId());
        grant.setCareId(patient.getCareId());
        grant.setGranteeId(caregiver.getId());
        grant.setRole("CAREGIVER");
        grant.setGranteeName(caregiver.getName());
        grant.setScope(CAREGIVER_SCOPE);
        grant.setStatus(GrantStatus.ACTIVE);
        grant.setReason("Caregiver link");
        grant.setCreatedAt(now);
        grant.setApprovedAt(now);
        // no end date: it lasts until the patient or the caregiver ends the link
        grant = grantRepository.save(grant);

        CaregiverLink link = new CaregiverLink();
        link.setPatientId(patient.getId());
        link.setPatientCareId(patient.getCareId());
        link.setPatientName(patient.getName());
        link.setCaregiverId(caregiver.getId());
        link.setCaregiverCareId(caregiver.getCareId());
        link.setCaregiverName(caregiver.getName());
        link.setRelationship(request.getRelationship() == null || request.getRelationship().isBlank() ? null : request.getRelationship().trim());
        link.setStatus(LinkStatus.ACTIVE);
        link.setGrantId(grant.getId());
        link.setCreatedAt(now);
        link = linkRepository.save(link);

        auditService.recordQuietly(patient, patient, AuditAction.GRANT_APPROVED, "CAREGIVER_LINK", link.getId(), grant.getId(), AuditOutcome.ALLOWED);
        return view(link);
    }

    /** A patient sees the caregivers they added; a caregiver sees the patients who added them. */
    public List<CaregiverLinkView> list() {
        AppUser user = currentUser.require();
        List<CaregiverLink> links;
        if ("PATIENT".equals(user.getRole())) {
            links = linkRepository.findByPatientIdOrderByCreatedAtDesc(user.getId());
        } else if ("CAREGIVER".equals(user.getRole())) {
            links = linkRepository.findByCaregiverIdOrderByCreatedAtDesc(user.getId());
        } else {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE", "Only patients and caregivers have caregiver links.");
        }
        return links.stream().map(CaregiverLinkService::view).toList();
    }

    /** Either side can end it. Ending the link ends the caregiver's access at once. Doing it again changes nothing. */
    public CaregiverLinkView revoke(String linkId) {
        AppUser user = currentUser.require();
        CaregiverLink link = linkRepository.findById(linkId)
                .filter(l -> user.getId().equals(l.getPatientId()) || user.getId().equals(l.getCaregiverId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LINK_NOT_FOUND", "No such caregiver link."));
        if (link.getStatus() == LinkStatus.ACTIVE) {
            Instant now = clock.instant();
            link.setStatus(LinkStatus.REVOKED);
            link.setRevokedAt(now);
            final CaregiverLink saved = linkRepository.save(link);
            grantRepository.findById(saved.getGrantId()).ifPresent(g -> {
                if (g.getStatus() == GrantStatus.ACTIVE) {
                    g.setStatus(GrantStatus.REVOKED);
                    g.setRevokedAt(now);
                    grantRepository.save(g);
                }
            });
            userRepository.findById(saved.getPatientId()).ifPresent(patient ->
                    auditService.recordQuietly(patient, user, AuditAction.GRANT_REVOKED, "CAREGIVER_LINK", linkId, saved.getGrantId(), AuditOutcome.ALLOWED));
            return view(saved);
        }
        return view(link);
    }

    static CaregiverLinkView view(CaregiverLink l) {
        return new CaregiverLinkView(l.getId(), l.getPatientCareId(), l.getPatientName(), l.getCaregiverCareId(), l.getCaregiverName(),
                l.getRelationship(), l.getStatus(), l.getCreatedAt(), l.getRevokedAt());
    }
}
