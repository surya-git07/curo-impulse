package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.Staff;

import java.util.Optional;

public interface StaffRepository extends LegacyRepository<Staff, Integer> {
    Optional<Staff> findByUsername(String username);
}