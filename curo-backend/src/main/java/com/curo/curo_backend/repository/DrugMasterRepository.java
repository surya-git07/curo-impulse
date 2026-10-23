package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.DrugMaster;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface DrugMasterRepository extends MongoRepository<DrugMaster, String> {
    Optional<DrugMaster> findByNormalizedName(String normalizedName);
    Optional<DrugMaster> findFirstByAliasesContaining(String alias);
    boolean existsByNormalizedName(String normalizedName);
}
