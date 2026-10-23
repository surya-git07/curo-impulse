package com.curo.curo_backend.dto;

import lombok.Data;

/** Sets the quantity on hand (not a change by an amount). */
@Data
public class StockUpdateRequest {
    private String drug;
    private Integer quantity;
}
