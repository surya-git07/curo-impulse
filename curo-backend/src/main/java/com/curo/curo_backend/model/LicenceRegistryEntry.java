package com.curo.curo_backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One record in the mock licence registry. All seeded data is invented.
 * type is DOCTOR or PHARMACY; stateCouncil is set for doctors, gstin for pharmacies.
 */
@Data
@Document("licence_registry")
@CompoundIndex(name = "type_licence_unique", def = "{'type': 1, 'licenceNumber': 1}", unique = true)
public class LicenceRegistryEntry {

    public static final String DOCTOR = "DOCTOR";
    public static final String PHARMACY = "PHARMACY";

    @Id
    private String id;

    private String type;

    private String licenceNumber;

    private String name;

    private String stateCouncil;

    // ISO 3166-2:IN code, e.g. TN
    private String state;

    private String gstin;

    private String source = "MOCK";
}
