package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.Visit;

import java.util.List;

public interface VisitRepository extends LegacyRepository<Visit, Integer> {
    List<Visit> findByHospitalIdAndStatus(Integer hospitalId, String status);
    List<Visit> findByHospitalIdAndDoctorIdAndStatus(Integer hospitalId, Integer doctorId, String status);
}