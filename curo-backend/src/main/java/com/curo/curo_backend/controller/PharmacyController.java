package com.curo.curo_backend.controller;

import org.springframework.context.annotation.Profile;
import com.curo.curo_backend.dto.DispenseRequest;
import com.curo.curo_backend.model.DispensingLog;
import com.curo.curo_backend.model.PharmacyStock;
import com.curo.curo_backend.model.legacy.Prescription;
import com.curo.curo_backend.model.legacy.PrescriptionGroup;
import com.curo.curo_backend.repository.DispensingLogRepository;
import com.curo.curo_backend.repository.PharmacyStockRepository;
import com.curo.curo_backend.repository.legacy.PrescriptionGroupRepository;
import com.curo.curo_backend.repository.legacy.PrescriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Profile("legacy-jpa")
@RestController
@RequestMapping("/pharmacy")
public class PharmacyController {

    @Autowired
    private PharmacyStockRepository stockRepository;

    @Autowired
    private DispensingLogRepository dispensingLogRepository;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private PrescriptionGroupRepository prescriptionGroupRepository;

    @GetMapping("/stock/{pharmacyId}")
    public ResponseEntity<List<PharmacyStock>> getStock(@PathVariable Integer pharmacyId) {
        return ResponseEntity.ok(stockRepository.findByPharmacyId(pharmacyId));
    }

    @GetMapping("/prescription/{id}")
    public ResponseEntity<?> lookupPrescription(@PathVariable Integer id) {
        Optional<Prescription> prescriptionOpt = prescriptionRepository.findById(id);
        if (prescriptionOpt.isEmpty()) {
            return ResponseEntity.status(404).body("Prescription not found");
        }
        return ResponseEntity.ok(prescriptionOpt.get());
    }

    @PostMapping("/dispense")
    public ResponseEntity<?> dispense(@RequestBody DispenseRequest request) {
        Optional<PharmacyStock> stockOpt = stockRepository.findByPharmacyIdAndDrug(
                request.getPharmacyId(), request.getDrug());

        if (stockOpt.isEmpty()) {
            logRejection(request.getPharmacyId(), request.getPrescriptionId(), request.getDrug(), "Drug not stocked at this pharmacy");
            return ResponseEntity.status(404).body("Drug not stocked at this pharmacy");
        }

        PharmacyStock stock = stockOpt.get();

        if (stock.getQuantity() < request.getQuantity()) {
            logRejection(request.getPharmacyId(), request.getPrescriptionId(), request.getDrug(),
                    "Insufficient stock. Available: " + stock.getQuantity() + ", Requested: " + request.getQuantity());
            return ResponseEntity.status(409).body(
                    "Insufficient stock. Available: " + stock.getQuantity() + ", Requested: " + request.getQuantity()
            );
        }

        stock.setQuantity(stock.getQuantity() - request.getQuantity());
        stockRepository.save(stock);

        DispensingLog log = new DispensingLog();
        log.setPrescriptionId(request.getPrescriptionId());
        log.setPharmacyId(request.getPharmacyId());
        log.setQtyDispensed(request.getQuantity());
        log.setDispensedAt(LocalDateTime.now());
        log.setStatus("DISPENSED");
        log.setDrugName(request.getDrug());

        Optional<Prescription> pres = prescriptionRepository.findById(request.getPrescriptionId());
        pres.ifPresent(p -> log.setPatientAbha(p.getPatientAbha()));

        DispensingLog savedLog = dispensingLogRepository.save(log);

        prescriptionRepository.findById(request.getPrescriptionId()).ifPresent(p -> {
            p.setStatus("DISPENSED");
            prescriptionRepository.save(p);
        });

        return ResponseEntity.ok(savedLog);
    }

    private void logRejection(Integer pharmacyId, Integer prescriptionId, String drug, String reason) {
        DispensingLog log = new DispensingLog();
        log.setPharmacyId(pharmacyId);
        log.setPrescriptionId(prescriptionId);
        log.setDrugName(drug);
        log.setStatus("REJECTED");
        log.setRejectReason(reason);
        log.setDispensedAt(LocalDateTime.now());
        dispensingLogRepository.save(log);
    }

    @GetMapping("/log/{pharmacyId}")
    public ResponseEntity<?> getDispensingLog(@PathVariable Integer pharmacyId) {
        List<DispensingLog> logs = dispensingLogRepository.findByPharmacyIdOrderByDispensedAtDesc(pharmacyId);

        long dispensedCount = logs.stream().filter(l -> "DISPENSED".equals(l.getStatus())).count();
        long rejectedCount = logs.stream().filter(l -> "REJECTED".equals(l.getStatus())).count();

        List<PrescriptionGroup> pending = prescriptionGroupRepository.findByPharmacyId(pharmacyId).stream()
                .filter(g -> "ISSUED".equals(g.getStatus()))
                .toList();

        List<Map<String, Object>> entries = new ArrayList<>();
        for (DispensingLog log : logs) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("id", log.getId());
            entry.put("qtyDispensed", log.getQtyDispensed());
            entry.put("dispensedAt", log.getDispensedAt());
            entry.put("status", log.getStatus());
            entry.put("rejectReason", log.getRejectReason());
            entry.put("drug", log.getDrugName());
            entry.put("patientAbha", log.getPatientAbha());
            entries.add(entry);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("dispensedCount", dispensedCount);
        response.put("rejectedCount", rejectedCount);
        response.put("pendingCount", pending.size());
        response.put("entries", entries);
        return ResponseEntity.ok(response);
    }
}