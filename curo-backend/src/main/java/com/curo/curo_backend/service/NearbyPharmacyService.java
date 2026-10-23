package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.NearbyPharmacyView;
import com.curo.curo_backend.model.DrugMaster;
import com.curo.curo_backend.model.Pharmacy;
import com.curo.curo_backend.model.PharmacyStock;
import com.curo.curo_backend.repository.PharmacyRepository;
import com.curo.curo_backend.repository.PharmacyStockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Finds verified pharmacies near a point, optionally only those with a drug in stock, nearest first. */
@Service
public class NearbyPharmacyService {

    public static final double DEFAULT_RADIUS_KM = 5;
    public static final double MAX_RADIUS_KM = 50;
    public static final int DEFAULT_LIMIT = 20;
    public static final int MAX_LIMIT = 50;

    // a degree of latitude is about 111.2 km; 111.0 makes the search box a little roomy, never too small
    private static final double KM_PER_DEGREE = 111.0;

    private record Candidate(Pharmacy pharmacy, double km) {
    }

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private PharmacyStockRepository stockRepository;

    @Autowired
    private DistanceService distanceService;

    @Autowired
    private DrugCatalog drugCatalog;

    public List<NearbyPharmacyView> nearby(Double lat, Double lng, String drug, Double radiusKm, Integer limit) {
        if (lat == null || lng == null || !Double.isFinite(lat) || !Double.isFinite(lng)
                || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COORDINATES",
                    "lat must be between -90 and 90 and lng between -180 and 180.");
        }
        double radius = radiusKm == null ? DEFAULT_RADIUS_KM : radiusKm;
        if (!Double.isFinite(radius) || radius <= 0 || radius > MAX_RADIUS_KM) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "radiusKm must be above 0 and at most " + (int) MAX_RADIUS_KM + ".");
        }
        int max = limit == null ? DEFAULT_LIMIT : limit;
        if (max < 1 || max > MAX_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "limit must be between 1 and " + MAX_LIMIT + ".");
        }

        // 1. cheap database filter: a box around the point
        double dLat = radius / KM_PER_DEGREE;
        double cos = Math.cos(Math.toRadians(lat));
        double dLng = cos < 0.01 ? 360 : radius / (KM_PER_DEGREE * cos);
        List<Pharmacy> inBox = pharmacyRepository.findByVerifiedTrueAndLatBetweenAndLngBetween(lat - dLat, lat + dLat, lng - dLng, lng + dLng)
                .stream().filter(Pharmacy::hasLocation).toList();
        if (inBox.isEmpty()) {
            return List.of();
        }

        // 2. exact distance, then the real radius
        List<Double> distances = distanceService.distancesKm(new DistanceService.Point(lat, lng),
                inBox.stream().map(p -> new DistanceService.Point(p.getLat(), p.getLng())).toList());
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < inBox.size(); i++) {
            if (distances.get(i) <= radius) {
                candidates.add(new Candidate(inBox.get(i), distances.get(i)));
            }
        }

        // 3. stock: with a drug, keep only pharmacies that hold at least one unit
        boolean byDrug = drug != null && !drug.isBlank();
        if (byDrug) {
            Optional<DrugMaster> resolved = drugCatalog.resolve(drug);
            String normalized = resolved.map(DrugMaster::getNormalizedName).orElse(DrugCatalog.normalize(drug));
            Set<String> withStock = stockRepository.findByPharmacyIdInAndNormalizedDrugAndQuantityGreaterThan(
                            candidates.stream().map(c -> c.pharmacy().getId()).toList(), normalized, 0)
                    .stream().map(PharmacyStock::getPharmacyId).collect(Collectors.toSet());
            candidates.removeIf(c -> !withStock.contains(c.pharmacy().getId()));
        }

        // 4. nearest first; equal distances in name order so the answer is stable
        return candidates.stream()
                .sorted(Comparator.comparingDouble(Candidate::km).thenComparing(c -> c.pharmacy().getName()))
                .limit(max)
                .map(c -> new NearbyPharmacyView(c.pharmacy().getId(), c.pharmacy().getName(), c.pharmacy().getAddress(),
                        c.pharmacy().getState(), new NearbyPharmacyView.Location(c.pharmacy().getLat(), c.pharmacy().getLng()),
                        Math.round(c.km() * 100.0) / 100.0, byDrug ? Boolean.TRUE : null))
                .toList();
    }
}
