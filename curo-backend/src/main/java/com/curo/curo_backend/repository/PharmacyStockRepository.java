package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.PharmacyStock;
import java.util.List;
import java.util.Optional;

public interface PharmacyStockRepository extends LegacyRepository<PharmacyStock, Integer> {
    List<PharmacyStock> findByPharmacyId(Integer pharmacyId);
    Optional<PharmacyStock> findByPharmacyIdAndDrug(Integer pharmacyId, String drug);
}