package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.DrugMaster;

import java.util.Optional;

public interface DrugMasterRepository extends LegacyRepository<DrugMaster, Integer> {
    Optional<DrugMaster> findByName(String name);
}