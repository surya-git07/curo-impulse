package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.AuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;

// Insert and read only. Audit entries are never updated or deleted by the application.
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
}
