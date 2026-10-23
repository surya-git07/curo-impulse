package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.AccessGrant;
import com.curo.curo_backend.model.GrantStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface AccessGrantRepository extends MongoRepository<AccessGrant, String> {
    List<AccessGrant> findByPatientIdAndGranteeIdAndStatus(String patientId, String granteeId, GrantStatus status);
    List<AccessGrant> findByPatientIdAndGranteeIdAndStatusIn(String patientId, String granteeId, Collection<GrantStatus> statuses);
}
