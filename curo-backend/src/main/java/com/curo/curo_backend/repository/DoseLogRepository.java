package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.DoseLog;
import com.curo.curo_backend.model.DoseStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface DoseLogRepository extends MongoRepository<DoseLog, String> {
    List<DoseLog> findByPrescriptionIdAndScheduledAtAfter(String prescriptionId, Instant after);
    long deleteByPrescriptionIdAndStatusAndScheduledAtAfter(String prescriptionId, DoseStatus status, Instant after);
    List<DoseLog> findByPatientIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAt(String patientId, Instant from, Instant to);
}
