package com.curo.curo_backend.controller;

import com.curo.curo_backend.dto.NearbyPharmacyView;
import com.curo.curo_backend.service.NearbyPharmacyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class NearbyPharmacyController {

    @Autowired
    private NearbyPharmacyService nearbyPharmacyService;

    // Any signed-in user. Pharmacy locations and in-stock flags are not patient data.
    @GetMapping("/pharmacies/nearby")
    public List<NearbyPharmacyView> nearby(@RequestParam(required = false) Double lat,
                                           @RequestParam(required = false) Double lng,
                                           @RequestParam(required = false) String drug,
                                           @RequestParam(required = false) Double radiusKm,
                                           @RequestParam(required = false) Integer limit) {
        return nearbyPharmacyService.nearby(lat, lng, drug, radiusKm, limit);
    }
}
