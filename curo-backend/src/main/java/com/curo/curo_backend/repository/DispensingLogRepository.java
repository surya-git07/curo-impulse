package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.DispensingLog;

import java.util.List;

public interface DispensingLogRepository extends LegacyRepository<DispensingLog, Integer> {
    List<DispensingLog> findByPharmacyIdOrderByDispensedAtDesc(Integer pharmacyId);
}