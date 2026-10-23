package com.curo.curo_backend.service;

import org.springframework.context.annotation.Profile;
import com.curo.curo_backend.model.legacy.DrugMaster;
import com.curo.curo_backend.model.legacy.Prescription;
import com.curo.curo_backend.repository.legacy.DrugMasterRepository;
import com.curo.curo_backend.repository.legacy.PrescriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Profile("legacy-jpa")
@Service
public class RiskScoringService {

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private DrugMasterRepository drugMasterRepository;

    public String calculateRisk(String patientAbha, String newDrugName) {
        Optional<DrugMaster> newDrugOpt = drugMasterRepository.findByName(newDrugName);
        if (newDrugOpt.isEmpty()) return "UNKNOWN";
        String newDrugClass = newDrugOpt.get().getDrugClass();
        if (newDrugClass == null) return "UNKNOWN";

        List<Prescription> history = prescriptionRepository.findByPatientAbhaOrderByIssuedAtDesc(patientAbha);

        for (Prescription past : history) {
            if (past.getIssuedAt() == null) continue;

            Optional<DrugMaster> pastDrugOpt = drugMasterRepository.findByName(past.getDrug());
            if (pastDrugOpt.isEmpty()) continue;

            String pastDrugClass = pastDrugOpt.get().getDrugClass();
            if (pastDrugClass == null || !pastDrugClass.equals(newDrugClass)) continue;

            long daysSince = ChronoUnit.DAYS.between(past.getIssuedAt().toLocalDate(), java.time.LocalDate.now());
            if (daysSince <= 15) return "HIGH";
            if (daysSince <= 30) return "MEDIUM";
        }
        return "LOW";
    }
}