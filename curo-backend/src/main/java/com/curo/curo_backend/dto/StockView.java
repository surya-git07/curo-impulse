package com.curo.curo_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class StockView {
    private String id;
    private String pharmacyId;
    private String drug;
    private int quantity;
    private Instant updatedAt;
}
