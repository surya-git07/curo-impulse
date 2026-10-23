package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.Pharmacy;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PharmacyRepository extends MongoRepository<Pharmacy, String> {
    Optional<Pharmacy> findBySeedKey(String seedKey);

    /** Verified pharmacies inside a latitude/longitude box; the exact distance is checked afterwards. */
    List<Pharmacy> findByVerifiedTrueAndLatBetweenAndLngBetween(double minLat, double maxLat, double minLng, double maxLng);
}
