package com.curo.curo_backend.repository.legacy;

import com.curo.curo_backend.repository.LegacyRepository;
import com.curo.curo_backend.model.legacy.PharmacyStock;
import java.util.List;
import java.util.Optional;

public interface PharmacyStockRepository extends LegacyRepository<PharmacyStock, Integer> {
    List<PharmacyStock> findByPharmacyId(Integer pharmacyId);
    Optional<PharmacyStock> findByPharmacyIdAndDrug(Integer pharmacyId, String drug);
}