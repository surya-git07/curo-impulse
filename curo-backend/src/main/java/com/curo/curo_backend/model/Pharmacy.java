package com.curo.curo_backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A pharmacy that can hold stock and appear in the nearby finder. lat and lng are plain degrees (distance is worked
 * out in code, see DistanceService). Only verified pharmacies with a location are ever returned to patients.
 */
@Data
@Document("pharmacies")
public class Pharmacy {

    @Id
    private String id;

    private String name;

    private String address;

    // ISO 3166-2:IN code, e.g. KA
    private String state;

    private Double lat;

    private Double lng;

    // true once licence verification has passed (VERIFIED), false while PENDING
    private boolean verified;

    // the PHARMACY user who runs it; null for the invented demo pharmacies
    private String ownerUserId;

    // identifies an invented demo pharmacy so seeding never duplicates it
    @Indexed(unique = true, sparse = true)
    private String seedKey;

    public boolean hasLocation() {
        return lat != null && lng != null;
    }
}
