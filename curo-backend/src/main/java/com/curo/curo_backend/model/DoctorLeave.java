package com.curo.curo_backend.model;

import lombok.Data;

import java.time.LocalDate;

@Data
public class DoctorLeave {

    private Integer id;

    private Integer doctorId;

    private LocalDate leaveFrom;

    private LocalDate leaveUntil;

    private String reason;

    private Integer substituteDoctorId;

    private String status;
}