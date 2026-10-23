package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.LicenceRegistryEntry;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface LicenceRegistryRepository extends MongoRepository<LicenceRegistryEntry, String> {
    Optional<LicenceRegistryEntry> findByTypeAndLicenceNumber(String type, String licenceNumber);
    boolean existsByTypeAndLicenceNumber(String type, String licenceNumber);
}
