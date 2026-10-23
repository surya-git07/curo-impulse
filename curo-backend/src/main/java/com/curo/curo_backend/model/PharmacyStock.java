package com.curo.curo_backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/** How many units of one drug one pharmacy holds. One row per pharmacy and drug. */
@Data
@Document("pharmacy_stock")
@CompoundIndex(name = "pharmacy_drug_unique", def = "{'pharmacyId': 1, 'normalizedDrug': 1}", unique = true)
public class PharmacyStock {

    @Id
    private String id;

    private String pharmacyId;

    // display name, e.g. "Amoxicillin"
    private String drug;

    // lower case; how lookups match (the canonical name when the drug is in drug_master)
    private String normalizedDrug;

    private int quantity;

    private Instant updatedAt;
}
