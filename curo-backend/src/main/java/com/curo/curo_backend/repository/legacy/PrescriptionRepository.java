package com.curo.curo_backend.repository.legacy;

import com.curo.curo_backend.repository.LegacyRepository;
import com.curo.curo_backend.model.legacy.Prescription;
import java.util.List;

public interface PrescriptionRepository extends LegacyRepository<Prescription, Integer> {
    List<Prescription> findByPatientAbha(String patientAbha);
    List<Prescription> findByPatientAbhaOrderByIssuedAtDesc(String patientAbha);
}