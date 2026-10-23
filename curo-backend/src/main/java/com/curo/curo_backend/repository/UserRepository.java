package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.AppUser;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface UserRepository extends MongoRepository<AppUser, String> {
    Optional<AppUser> findByCareId(String careId);
    Optional<AppUser> findByPhone(String phone);
    boolean existsByCareId(String careId);
    boolean existsByPhone(String phone);
}
