package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.Prescription;
import java.util.List;

public interface PrescriptionRepository extends LegacyRepository<Prescription, Integer> {
    List<Prescription> findByPatientAbha(String patientAbha);
    List<Prescription> findByPatientAbhaOrderByIssuedAtDesc(String patientAbha);
}