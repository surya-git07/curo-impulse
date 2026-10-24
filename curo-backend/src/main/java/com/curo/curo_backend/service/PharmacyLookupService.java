package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.ActivePrescriptionView;
import com.curo.curo_backend.dto.ActivePrescriptionsResponse;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.AuditOutcome;
import com.curo.curo_backend.model.DispenseStatus;
import com.curo.curo_backend.model.Pharmacy;
import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.model.PrescriptionStatus;
import com.curo.curo_backend.repository.DispensingLogRepository;
import com.curo.curo_backend.repository.PharmacyRepository;
import com.curo.curo_backend.repository.PrescriptionRepository;
import com.curo.curo_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A pharmacist looks up what is waiting to be handed over for a patient. Only the prescriptions that can still be
 * dispensed come back, with the fields needed to hand them over. Every lookup is written to the patient's access log.
 * Dispensing itself is still checked in full by DispenseService.
 */
@Service
public class PharmacyLookupService {

    @Value("${app.time-zone:Asia/Kolkata}")
    private String timeZone;

    @Autowired
    private CurrentUserService currentUser;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private DispensingLogRepository dispensingLogRepository;

    @Autowired
    private AuditService auditService;

    @Autowired
    private PharmacyLookupLimiter limiter;

    @Autowired
    private Clock clock;

    public ActivePrescriptionsResponse activePrescriptions(String careIdRaw) {
        AppUser pharmacist = currentUser.require();
        if (!"PHARMACY".equals(pharmacist.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE", "Only a PHARMACY account can do this.");
        }
        requireVerifiedPharmacy(pharmacist);
        limiter.checkAllowed(pharmacist.getId());

        String careId = careIdRaw == null ? "" : careIdRaw.trim().toUpperCase(Locale.ROOT);
        AppUser patient = CareIdGenerator.isCareId(careId)
                ? userRepository.findByCareId(careId).filter(u -> "PATIENT".equals(u.getRole()) && u.getDeletedAt() == null).orElse(null)
                : null;
        if (patient == null) {
            limiter.recordFailure(pharmacist.getId());
            throw new ApiException(HttpStatus.NOT_FOUND, "PATIENT_NOT_FOUND", "No patient with that CareID.");
        }

        // The patient data is about to be released, so if the lookup cannot be recorded it does not happen
        try {
            auditService.record(patient, pharmacist, AuditAction.PHARMACY_LOOKUP, "PRESCRIPTIONS", null, null, AuditOutcome.ALLOWED);
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "AUDIT_UNAVAILABLE",
                    "The lookup could not be recorded, so it was not allowed. Try again.");
        }

        ZoneId zone = ZoneId.of(timeZone);
        LocalDate today = clock.instant().atZone(zone).toLocalDate();
        List<ActivePrescriptionView> active = prescriptionRepository.findByCareIdAndStatus(careId, PrescriptionStatus.ACTIVE).stream()
                .filter(p -> Courses.isCurrent(p, today, zone))
                .filter(p -> !dispensingLogRepository.existsByPrescriptionIdAndStatus(p.getId(), DispenseStatus.DISPENSED))
                .sorted(Comparator.comparing(Prescription::getIssuedAt).thenComparing(Prescription::getDrug))
                .map(p -> new ActivePrescriptionView(p.getId(), p.getDrug(), p.getDose(), p.getFrequency(), p.getDuration(),
                        Courses.endDate(p, zone).orElse(null), p.getDoctorName(), "NOT_DISPENSED"))
                .toList();
        return new ActivePrescriptionsResponse(careId, active);
    }

    private void requireVerifiedPharmacy(AppUser pharmacist) {
        Pharmacy pharmacy = pharmacist.getFacilityId() == null ? null : pharmacyRepository.findById(pharmacist.getFacilityId()).orElse(null);
        if (pharmacy == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NO_PHARMACY", "Your account is not linked to a pharmacy.");
        }
        if (!pharmacy.isVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PHARMACY_NOT_VERIFIED", "Your pharmacy has not been verified yet.");
        }
    }
}
