package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.DispenseOrderRequest;
import com.curo.curo_backend.dto.DispenseView;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.DispenseStatus;
import com.curo.curo_backend.model.DispensingLog;
import com.curo.curo_backend.model.DrugMaster;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.Pharmacy;
import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.model.PrescriptionStatus;
import com.curo.curo_backend.repository.DispensingLogRepository;
import com.curo.curo_backend.repository.PharmacyRepository;
import com.curo.curo_backend.repository.PrescriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * A pharmacist dispenses a prescription: it is marked DISPENSED and a dispensing log entry is written. No stock is checked
 * or reduced. A pharmacist can only dispense as their own pharmacy, and each prescription is dispensed once.
 */
@Service
public class DispenseService {

    private static final Logger log = LoggerFactory.getLogger(DispenseService.class);

    static final int MAX_DISPENSE = 10_000;

    @Value("${app.time-zone:Asia/Kolkata}")
    private String timeZone;

    @Autowired
    private CurrentUserService currentUser;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private DispensingLogRepository dispensingLogRepository;

    @Autowired
    private AccessGuard accessGuard;

    @Autowired
    private DrugCatalog drugCatalog;

    @Autowired
    private Clock clock;

    public DispenseView dispense(DispenseOrderRequest request) {
        AppUser pharmacist = requirePharmacist();
        Pharmacy pharmacy = pharmacyOf(pharmacist);
        if (request == null || request.getPrescriptionId() == null || request.getPrescriptionId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRESCRIPTION_REQUIRED", "prescriptionId is required.");
        }
        if (request.getQuantity() == null || request.getQuantity() < 1 || request.getQuantity() > MAX_DISPENSE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "quantity must be between 1 and " + MAX_DISPENSE + ".");
        }
        // own pharmacy only: a pharmacy id that is not yours is refused, whether or not it exists
        if (request.getPharmacyId() != null && !request.getPharmacyId().isBlank() && !pharmacy.getId().equals(request.getPharmacyId().trim())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You can only dispense as your own pharmacy.");
        }
        int quantity = request.getQuantity();

        Prescription rx = prescriptionRepository.findById(request.getPrescriptionId().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRESCRIPTION_NOT_FOUND", "No such prescription."));
        // Dispensing changes the patient's record, so the pharmacy needs a grant covering prescriptions (and the attempt is logged)
        accessGuard.require(rx.getCareId(), GrantScope.PRESCRIPTIONS, AuditAction.WRITE, "DISPENSE", rx.getId());

        if (request.getDrug() != null && !request.getDrug().isBlank() && !sameDrug(request.getDrug(), rx.getDrug())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DRUG_MISMATCH", "That is not the drug on this prescription.");
        }
        ZoneId zone = ZoneId.of(timeZone);
        Instant now = clock.instant();
        LocalDate today = now.atZone(zone).toLocalDate();
        // One dispense per prescription: a second one would hand out a second course of the drug
        if (rx.getStatus() == PrescriptionStatus.DISPENSED || dispensingLogRepository.existsByPrescriptionIdAndStatus(rx.getId(), DispenseStatus.DISPENSED)) {
            throw alreadyDispensed();
        }
        if (rx.getStatus() != PrescriptionStatus.ACTIVE || !Courses.isCurrent(rx, today, zone)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "This prescription is discontinued or its course has ended.");
        }

        // The log row is the gate: the database allows only one DISPENSED row per prescription, so of two simultaneous
        // requests exactly one gets past this line.
        DispensingLog entry = new DispensingLog();
        entry.setPrescriptionId(rx.getId());
        entry.setPharmacyId(pharmacy.getId());
        entry.setCareId(rx.getCareId());
        entry.setDrug(rx.getDrug());
        entry.setQuantity(quantity);
        entry.setStatus(DispenseStatus.DISPENSED);
        entry.setDispensedByUserId(pharmacist.getId());
        entry.setAt(now);
        try {
            entry = dispensingLogRepository.save(entry);
        } catch (DuplicateKeyException e) {
            throw alreadyDispensed();
        }

        try {
            markDispensed(rx, pharmacy, now);
        } catch (RuntimeException e) {
            // No log without the matching status: take the entry back out so the pharmacist can simply try again.
            try {
                dispensingLogRepository.delete(entry);
            } catch (RuntimeException ignored) {
                log.warn("dispensing log {} could not be removed after a failed dispense ({})", entry.getId(), ignored.getClass().getSimpleName());
            }
            throw e;
        }
        return new DispenseView(entry.getId(), rx.getId(), pharmacy.getId(), quantity, now, DispenseStatus.DISPENSED, rx.getDrug(), rx.getCareId());
    }

    // The prescription may have been changed by its doctor a moment ago; reload once and apply on top of that
    private void markDispensed(Prescription rx, Pharmacy pharmacy, Instant now) {
        try {
            apply(rx, pharmacy, now);
            prescriptionRepository.save(rx);
        } catch (OptimisticLockingFailureException e) {
            Prescription fresh = prescriptionRepository.findById(rx.getId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRESCRIPTION_NOT_FOUND", "No such prescription."));
            if (fresh.getStatus() != PrescriptionStatus.ACTIVE) {
                throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "This prescription was changed and can no longer be dispensed.");
            }
            apply(fresh, pharmacy, now);
            prescriptionRepository.save(fresh);
        }
    }

    private void apply(Prescription rx, Pharmacy pharmacy, Instant now) {
        rx.setStatus(PrescriptionStatus.DISPENSED);
        rx.setDispensedAt(now);
        rx.setDispensedByPharmacyId(pharmacy.getId());
    }

    private boolean sameDrug(String sent, String onPrescription) {
        String a = drugCatalog.resolve(sent).map(DrugMaster::getNormalizedName).orElse(DrugCatalog.normalize(sent));
        String b = drugCatalog.resolve(onPrescription).map(DrugMaster::getNormalizedName).orElse(DrugCatalog.normalize(onPrescription));
        return a.equals(b);
    }

    private ApiException alreadyDispensed() {
        return new ApiException(HttpStatus.CONFLICT, "ALREADY_DISPENSED", "This prescription has already been dispensed.");
    }

    private AppUser requirePharmacist() {
        AppUser user = currentUser.require();
        if (!"PHARMACY".equals(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE", "Only a PHARMACY account can do this.");
        }
        return user;
    }

    private Pharmacy pharmacyOf(AppUser pharmacist) {
        if (pharmacist.getFacilityId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NO_PHARMACY", "Your account is not linked to a pharmacy.");
        }
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacist.getFacilityId())
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "NO_PHARMACY", "Your account is not linked to a pharmacy."));
        if (!pharmacy.isVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PHARMACY_NOT_VERIFIED", "Your pharmacy has not been verified yet.");
        }
        return pharmacy;
    }
}
