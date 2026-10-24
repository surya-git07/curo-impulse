package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<AppUser, String> {
    Optional<AppUser> findByCareId(String careId);
    Optional<AppUser> findByPhone(String phone);
    boolean existsByCareId(String careId);
    boolean existsByPhone(String phone);
    boolean existsByLicenceNumber(String licenceNumber);
    boolean existsByDrugLicenceNumber(String drugLicenceNumber);
    List<AppUser> findByRoleAndVerificationStatusAndDeletedAtIsNull(String role, VerificationStatus status);
    Page<AppUser> findByVerificationStatusAndDeletedAtIsNull(VerificationStatus status, Pageable pageable);
}
