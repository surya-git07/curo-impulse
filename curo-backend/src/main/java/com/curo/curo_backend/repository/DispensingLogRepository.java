package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.DispenseStatus;
import com.curo.curo_backend.model.DispensingLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DispensingLogRepository extends MongoRepository<DispensingLog, String> {
    boolean existsByPrescriptionIdAndStatus(String prescriptionId, DispenseStatus status);
}
