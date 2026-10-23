package com.curo.curo_backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** A pharmacy, created when a PHARMACY account signs up. A pharmacist can only dispense as their own pharmacy. */
@Data
@Document("pharmacies")
public class Pharmacy {

    @Id
    private String id;

    private String name;

    private String address;

    // ISO 3166-2:IN code, e.g. KA
    private String state;

    // true once licence verification has passed (VERIFIED), false while PENDING
    private boolean verified;

    // the PHARMACY user who runs it
    private String ownerUserId;

    // identifies an invented demo pharmacy so seeding never duplicates it
    @Indexed(unique = true, sparse = true)
    private String seedKey;
}
