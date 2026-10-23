package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.ShareCode;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface ShareCodeRepository extends MongoRepository<ShareCode, String> {
    Optional<ShareCode> findByCode(String code);
}
