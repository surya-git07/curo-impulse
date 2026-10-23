package com.curo.curo_backend.repository.legacy;

import com.curo.curo_backend.repository.LegacyRepository;
import com.curo.curo_backend.model.legacy.DrugMaster;

import java.util.Optional;

public interface DrugMasterRepository extends LegacyRepository<DrugMaster, Integer> {
    Optional<DrugMaster> findByName(String name);
}