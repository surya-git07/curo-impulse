package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.model.PrescriptionStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PrescriptionRepository extends MongoRepository<Prescription, String> {
    List<Prescription> findByCareIdAndStatus(String careId, PrescriptionStatus status);
}
