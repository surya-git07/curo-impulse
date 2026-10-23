package com.curo.curo_backend.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Visit {

    private Integer id;

    private String patientAbha;

    private Integer hospitalId;

    private Integer doctorId;

    private String status;

    private LocalDateTime checkedInAt;
}