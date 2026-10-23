package com.curo.curo_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Quantities are never shown to patients: only whether the drug is in stock. */
@Data
@AllArgsConstructor
public class NearbyPharmacyView {

    @Data
    @AllArgsConstructor
    public static class Location {
        private double lat;
        private double lng;
    }

    private String id;
    private String name;
    private String address;
    private String state;
    private Location location;
    private double distanceKm;
    // true when a drug was asked for (only pharmacies that have it are listed); null when no drug was given
    private Boolean inStock;
}
