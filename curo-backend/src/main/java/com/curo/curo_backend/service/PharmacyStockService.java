package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.DispenseOrderRequest;
import com.curo.curo_backend.dto.DispenseView;
import com.curo.curo_backend.dto.StockUpdateRequest;
import com.curo.curo_backend.dto.StockView;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.DispenseStatus;
import com.curo.curo_backend.model.DispensingLog;
import com.curo.curo_backend.model.DrugMaster;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.Pharmacy;
import com.curo.curo_backend.model.PharmacyStock;
import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.model.PrescriptionStatus;
import com.curo.curo_backend.repository.DispensingLogRepository;
import com.curo.curo_backend.repository.PharmacyRepository;
import com.curo.curo_backend.repository.PharmacyStockRepository;
import com.curo.curo_backend.repository.PrescriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/** A pharmacist's stock list, stock updates, and dispensing against a prescription. A pharmacist only ever touches their own pharmacy. */
@Service
public class PharmacyStockService {

    private static final Logger log = LoggerFactory.getLogger(PharmacyStockService.class);

    static final int MAX_STOCK = 100_000;
    static final int MAX_DISPENSE = 10_000;

    @Value("${app.time-zone:Asia/Kolkata}")
    private String timeZone;

    @Autowired
    private CurrentUserService currentUser;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private PharmacyStockRepository stockRepository;

    @Autowired
    private StockUpdates stockUpdates;

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

    // ---- stock

    public List<StockView> stock(String pharmacyId) {
        Pharmacy pharmacy = ownPharmacy(pharmacyId);
        return stockRepository.findByPharmacyIdOrderByDrug(pharmacy.getId()).stream().map(this::view).toList();
    }

    public StockView updateStock(String pharmacyId, StockUpdateRequest request) {
        Pharmacy pharmacy = ownPharmacy(pharmacyId);
        if (request == null || request.getDrug() == null || request.getDrug().isBlank() || request.getDrug().trim().length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DRUG_REQUIRED", "drug is required (up to 100 characters).");
        }
        if (request.getQuantity() == null || request.getQuantity() < 0 || request.getQuantity() > MAX_STOCK) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "quantity must be between 0 and " + MAX_STOCK + ".");
        }
        Optional<DrugMaster> known = drugCatalog.resolve(request.getDrug());
        String display = known.map(DrugMaster::getName).orElse(request.getDrug().trim());
        String normalized = known.map(DrugMaster::getNormalizedName).orElse(DrugCatalog.normalize(request.getDrug()));

        Instant now = clock.instant();
        stockUpdates.set(pharmacy.getId(), normalized, display, request.getQuantity(), now);
        return stockRepository.findByPharmacyIdAndNormalizedDrug(pharmacy.getId(), normalized).map(this::view)
                .orElseGet(() -> new StockView(null, pharmacy.getId(), display, request.getQuantity(), now));
    }

    // ---- dispense

    public DispenseView dispense(DispenseOrderRequest request) {
        AppUser pharmacist = requirePharmacist();
        Pharmacy pharmacy = pharmacyOf(pharmacist);
        if (request == null || request.getPrescriptionId() == null || request.getPrescriptionId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRESCRIPTION_REQUIRED", "prescriptionId is required.");
        }
        if (request.getQuantity() == null || request.getQuantity() < 1 || request.getQuantity() > MAX_DISPENSE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "quantity must be between 1 and " + MAX_DISPENSE + ".");
        }
        int quantity = request.getQuantity();

        Prescription rx = prescriptionRepository.findById(request.getPrescriptionId().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRESCRIPTION_NOT_FOUND", "No such prescription."));
        // Dispensing writes to the patient's record, so the pharmacy needs a grant that covers prescriptions (and the attempt is logged)
        accessGuard.require(rx.getCareId(), GrantScope.PRESCRIPTIONS, AuditAction.WRITE, "DISPENSE", rx.getId());

        String normalized = drugCatalog.resolve(rx.getDrug()).map(DrugMaster::getNormalizedName).orElse(DrugCatalog.normalize(rx.getDrug()));
        if (request.getDrug() != null && !request.getDrug().isBlank()) {
            String sent = drugCatalog.resolve(request.getDrug()).map(DrugMaster::getNormalizedName).orElse(DrugCatalog.normalize(request.getDrug()));
            if (!sent.equals(normalized)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "DRUG_MISMATCH", "That is not the drug on this prescription.");
            }
        }
        ZoneId zone = ZoneId.of(timeZone);
        Instant now = clock.instant();
        LocalDate today = now.atZone(zone).toLocalDate();
        if (rx.getStatus() != PrescriptionStatus.ACTIVE || !Courses.isCurrent(rx, today, zone)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "This prescription is discontinued or its course has ended.");
        }
        // One dispense per prescription: a second one would hand out a second course of the drug
        if (dispensingLogRepository.existsByPrescriptionIdAndStatus(rx.getId(), DispenseStatus.DISPENSED)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_DISPENSED", "This prescription has already been dispensed.");
        }

        if (!stockRepository.existsByPharmacyIdAndNormalizedDrug(pharmacy.getId(), normalized)) {
            reject(rx, pharmacy, pharmacist, quantity, "NOT_STOCKED", now);
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_STOCKED", "This pharmacy does not stock that drug.");
        }
        if (!stockUpdates.decrement(pharmacy.getId(), normalized, quantity, now)) {
            reject(rx, pharmacy, pharmacist, quantity, "INSUFFICIENT_STOCK", now);
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Not enough stock to dispense " + quantity + " unit(s).");
        }

        DispensingLog entry = entry(rx, pharmacy, pharmacist, quantity, DispenseStatus.DISPENSED, null, now);
        try {
            entry = dispensingLogRepository.save(entry);
        } catch (DuplicateKeyException e) {
            // another request dispensed it at the same moment; give the units back
            stockUpdates.increment(pharmacy.getId(), normalized, quantity, now);
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_DISPENSED", "This prescription has already been dispensed.");
        } catch (RuntimeException e) {
            stockUpdates.increment(pharmacy.getId(), normalized, quantity, now);
            throw e;
        }
        return new DispenseView(entry.getId(), rx.getId(), pharmacy.getId(), quantity, now, DispenseStatus.DISPENSED, rx.getDrug(), rx.getCareId());
    }

    // ---- helpers

    private void reject(Prescription rx, Pharmacy pharmacy, AppUser by, int quantity, String reason, Instant now) {
        try {
            dispensingLogRepository.save(entry(rx, pharmacy, by, quantity, DispenseStatus.REJECTED, reason, now));
        } catch (RuntimeException e) {
            log.warn("rejected dispense could not be logged ({})", e.getClass().getSimpleName());
        }
    }

    private DispensingLog entry(Prescription rx, Pharmacy pharmacy, AppUser by, int quantity, DispenseStatus status, String reason, Instant now) {
        DispensingLog d = new DispensingLog();
        d.setPrescriptionId(rx.getId());
        d.setPharmacyId(pharmacy.getId());
        d.setCareId(rx.getCareId());
        d.setDrug(rx.getDrug());
        d.setQuantity(quantity);
        d.setStatus(status);
        d.setRejectReason(reason);
        d.setDispensedByUserId(by.getId());
        d.setAt(now);
        return d;
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

    // another pharmacy's id gets the same answer as one that does not exist
    private Pharmacy ownPharmacy(String pharmacyId) {
        Pharmacy pharmacy = pharmacyOf(requirePharmacist());
        if (!pharmacy.getId().equals(pharmacyId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You can only manage your own pharmacy's stock.");
        }
        return pharmacy;
    }

    private StockView view(PharmacyStock s) {
        return new StockView(s.getId(), s.getPharmacyId(), s.getDrug(), s.getQuantity(), s.getUpdatedAt());
    }
}
