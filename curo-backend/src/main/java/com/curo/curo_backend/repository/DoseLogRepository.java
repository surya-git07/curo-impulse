package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.DoseLog;
import com.curo.curo_backend.model.DoseStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface DoseLogRepository extends MongoRepository<DoseLog, String>, DoseLogRepositoryCustom {
    List<DoseLog> findByPrescriptionIdAndScheduledAtAfter(String prescriptionId, Instant after);
    long deleteByPrescriptionIdAndStatusAndScheduledAtAfter(String prescriptionId, DoseStatus status, Instant after);
    /** Doses in a status that were due at or before the time (used to find the overdue ones). */
    List<DoseLog> findByStatusAndScheduledAtLessThanEqual(DoseStatus status, Instant at);

    /** A prescription's doses up to a time; ask for newest first with a sorted Pageable. Used to count consecutive misses. */
    List<DoseLog> findByPrescriptionIdAndScheduledAtLessThanEqual(String prescriptionId, Instant at, Pageable pageable);
}
