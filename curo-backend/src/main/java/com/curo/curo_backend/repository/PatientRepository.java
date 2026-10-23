package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.Patient;

import java.util.Optional;

public interface PatientRepository extends LegacyRepository<Patient, Integer> {
    Optional<Patient> findByAbha(String abha);
}