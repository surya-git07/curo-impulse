package com.curo.curo_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DrugView {
    private String id;
    private String name;
    private String drugClass;
    private String scheduleType;
}
