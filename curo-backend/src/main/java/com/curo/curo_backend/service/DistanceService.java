package com.curo.curo_backend.service;

import java.util.List;

/**
 * Works out how far places are from a starting point. The nearby finder depends only on this interface, so the
 * straight-line maths in HaversineDistanceService can be swapped for Amazon Location Service (a route matrix gives
 * road distance) without touching anything else. It takes a batch because that is how a route matrix is asked.
 */
public interface DistanceService {

    record Point(double lat, double lng) {
    }

    /** @return one distance in kilometres per destination, in the same order */
    List<Double> distancesKm(Point origin, List<Point> destinations);
}
