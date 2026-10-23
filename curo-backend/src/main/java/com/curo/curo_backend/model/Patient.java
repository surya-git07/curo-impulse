package com.curo.curo_backend.model;

import lombok.Data;

import java.time.LocalDate;

@Data
public class Patient {

    private Integer id;

    private String abha;

    private String name;

    private LocalDate dob;

    private String bloodGroup;
}