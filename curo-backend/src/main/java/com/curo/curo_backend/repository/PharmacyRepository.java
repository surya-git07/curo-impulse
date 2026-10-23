package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.Pharmacy;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PharmacyRepository extends MongoRepository<Pharmacy, String> {
    Optional<Pharmacy> findBySeedKey(String seedKey);
}
