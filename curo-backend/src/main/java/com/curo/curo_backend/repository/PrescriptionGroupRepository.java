package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.PrescriptionGroup;

import java.util.List;
import java.util.Optional;

public interface PrescriptionGroupRepository extends LegacyRepository<PrescriptionGroup, Integer> {
    Optional<PrescriptionGroup> findByQrCode(String qrCode);
    List<PrescriptionGroup> findByPharmacyId(Integer pharmacyId);
    List<PrescriptionGroup> findByPatientAbhaOrderByIssuedAtDesc(String patientAbha);
}