package com.curo.curo_backend.model;

import lombok.Data;

@Data
public class DrugMaster {
    private Integer id;

    private String name;

    private String drugClass;

    private String scheduleType;
}