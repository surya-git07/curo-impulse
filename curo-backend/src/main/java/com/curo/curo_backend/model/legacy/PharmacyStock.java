package com.curo.curo_backend.model.legacy;

import lombok.Data;

@Data
public class PharmacyStock {

    private Integer id;

    private Integer pharmacyId;

    private String drug;

    private Integer quantity;
}