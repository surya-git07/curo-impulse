package com.curo.curo_backend.repository.legacy;

import com.curo.curo_backend.repository.LegacyRepository;
import com.curo.curo_backend.model.legacy.DispensingLog;

import java.util.List;

public interface DispensingLogRepository extends LegacyRepository<DispensingLog, Integer> {
    List<DispensingLog> findByPharmacyIdOrderByDispensedAtDesc(Integer pharmacyId);
}