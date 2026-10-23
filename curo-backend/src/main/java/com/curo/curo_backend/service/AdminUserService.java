package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.AdminUserView;
import com.curo.curo_backend.dto.PagedResponse;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.VerificationStatus;
import com.curo.curo_backend.repository.PharmacyRepository;
import com.curo.curo_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Locale;

/** SUPER_ADMIN review of DOCTOR and PHARMACY accounts that signed up as PENDING. The route rule lives in SecurityConfig. */
@Service
public class AdminUserService {

    static final int MAX_REASON = 300;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private CurrentUserService currentUser;

    public PagedResponse<AdminUserView> list(String status, int page, int size) {
        requireSuperAdmin();
        if (page < 0 || size < 1 || size > AuditService.MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "page must be 0 or more and size between 1 and " + AuditService.MAX_PAGE_SIZE + ".");
        }
        VerificationStatus wanted = VerificationStatus.PENDING;
        if (status != null && !status.isBlank()) {
            try {
                wanted = VerificationStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "verificationStatus must be PENDING, VERIFIED or REJECTED.");
            }
        }
        Page<AppUser> result = userRepository.findByVerificationStatusAndDeletedAtIsNull(
                wanted, PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt")));
        return new PagedResponse<>(page, size, result.getTotalElements(), result.getContent().stream().map(AdminUserService::view).toList());
    }

    public AdminUserView verify(String id) {
        requireSuperAdmin();
        AppUser user = requirePending(id);
        user.setVerificationStatus(VerificationStatus.VERIFIED);
        user.setVerificationReasonCode("APPROVED_BY_ADMIN");
        user.setVerificationReason("Approved by a SUPER_ADMIN.");
        AppUser saved = userRepository.save(user);
        // a pharmacy can only dispense once its record is switched on
        if (saved.getFacilityId() != null && "PHARMACY".equals(saved.getRole())) {
            pharmacyRepository.findById(saved.getFacilityId()).ifPresent(p -> {
                p.setVerified(true);
                pharmacyRepository.save(p);
            });
        }
        return view(saved);
    }

    public AdminUserView reject(String id, String reason) {
        requireSuperAdmin();
        String text = reason == null ? "" : reason.trim();
        if (text.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "A reason is required.");
        }
        if (text.length() > MAX_REASON) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "reason must be at most " + MAX_REASON + " characters.");
        }
        AppUser user = requirePending(id);
        user.setVerificationStatus(VerificationStatus.REJECTED);
        user.setVerificationReasonCode("REJECTED_BY_ADMIN");
        user.setVerificationReason(text);
        return view(userRepository.save(user));
    }

    private AppUser requirePending(String id) {
        AppUser user = userRepository.findById(id).filter(u -> u.getDeletedAt() == null && u.getVerificationStatus() != null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "No such account to review."));
        if (user.getVerificationStatus() != VerificationStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_PENDING", "This account is not waiting for review.");
        }
        return user;
    }

    // belt and braces: the route is already limited to SUPER_ADMIN
    private void requireSuperAdmin() {
        if (!"SUPER_ADMIN".equals(currentUser.require().getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE", "Only a SUPER_ADMIN can review accounts.");
        }
    }

    private static AdminUserView view(AppUser u) {
        return new AdminUserView(u.getId(), u.getCareId(), u.getRole(), u.getName(), u.getFacilityId(), u.getLicenceNumber(),
                u.getStateCouncil(), u.getDrugLicenceNumber(), u.getGstin(), u.getState(), u.getVerificationStatus(),
                u.getVerificationReasonCode(), u.getVerificationReason(), u.getCreatedAt());
    }
}
