package com.curo.curo_backend.repository;

import java.util.List;
import java.util.Optional;

/**
 * Stand-in for JpaRepository while the old hospital code is migrated to MongoDB.
 * Declares only the CRUD methods the legacy controllers call. No bean implements it,
 * so the legacy classes are disabled via @Profile("legacy-jpa") until they are ported.
 */
public interface LegacyRepository<T, ID> {
    Optional<T> findById(ID id);
    List<T> findAll();
    <S extends T> S save(S entity);
    void deleteById(ID id);
    boolean existsById(ID id);
}
