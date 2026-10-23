package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.Alert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AlertRepository extends MongoRepository<Alert, String> {
    Page<Alert> findByRecipientIdOrderByCreatedAtDesc(String recipientId, Pageable pageable);
    Page<Alert> findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(String recipientId, Pageable pageable);
}
