package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.CaregiverLink;
import com.curo.curo_backend.model.LinkStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CaregiverLinkRepository extends MongoRepository<CaregiverLink, String> {
    List<CaregiverLink> findByPatientIdOrderByCreatedAtDesc(String patientId);
    List<CaregiverLink> findByCaregiverIdOrderByCreatedAtDesc(String caregiverId);
    boolean existsByPatientIdAndCaregiverIdAndStatus(String patientId, String caregiverId, LinkStatus status);
}
