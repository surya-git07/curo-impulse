package com.curo.curo_backend.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/** Great-circle (straight-line) distance on a sphere. Active when location.distance.mode=haversine. Ignores roads, so it is a lower bound. */
@Service
@ConditionalOnProperty(name = "location.distance.mode", havingValue = "haversine", matchIfMissing = true)
public class HaversineDistanceService implements DistanceService {

    // mean Earth radius in kilometres
    static final double EARTH_RADIUS_KM = 6371.0088;

    @Override
    public List<Double> distancesKm(Point origin, List<Point> destinations) {
        return destinations.stream().map(d -> haversineKm(origin, d)).toList();
    }

    static double haversineKm(Point a, Point b) {
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(b.lng() - a.lng());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        // min() guards against h creeping just above 1 from rounding at opposite points
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }
}
