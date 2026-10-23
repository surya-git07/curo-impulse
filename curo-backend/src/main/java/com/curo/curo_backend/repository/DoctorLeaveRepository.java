package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.DoctorLeave;

import java.util.List;

public interface DoctorLeaveRepository extends LegacyRepository<DoctorLeave, Integer> {
    List<DoctorLeave> findByDoctorIdAndStatus(Integer doctorId, String status);
    List<DoctorLeave> findByStatus(String status);
}