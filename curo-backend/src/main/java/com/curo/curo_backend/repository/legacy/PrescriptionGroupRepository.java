package com.curo.curo_backend.repository.legacy;

import com.curo.curo_backend.repository.LegacyRepository;
import com.curo.curo_backend.model.legacy.PrescriptionGroup;

import java.util.List;
import java.util.Optional;

public interface PrescriptionGroupRepository extends LegacyRepository<PrescriptionGroup, Integer> {
    Optional<PrescriptionGroup> findByQrCode(String qrCode);
    List<PrescriptionGroup> findByPharmacyId(Integer pharmacyId);
    List<PrescriptionGroup> findByPatientAbhaOrderByIssuedAtDesc(String patientAbha);
}