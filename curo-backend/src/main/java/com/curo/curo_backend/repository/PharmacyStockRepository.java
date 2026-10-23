package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.PharmacyStock;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PharmacyStockRepository extends MongoRepository<PharmacyStock, String> {
    List<PharmacyStock> findByPharmacyIdOrderByDrug(String pharmacyId);
    Optional<PharmacyStock> findByPharmacyIdAndNormalizedDrug(String pharmacyId, String normalizedDrug);
    boolean existsByPharmacyIdAndNormalizedDrug(String pharmacyId, String normalizedDrug);
    List<PharmacyStock> findByPharmacyIdInAndNormalizedDrugAndQuantityGreaterThan(Collection<String> pharmacyIds, String normalizedDrug, int quantity);
}
