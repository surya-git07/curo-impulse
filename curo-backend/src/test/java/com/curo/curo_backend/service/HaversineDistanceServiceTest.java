package com.curo.curo_backend.service;

import com.curo.curo_backend.service.DistanceService.Point;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HaversineDistanceServiceTest {

    private final HaversineDistanceService service = new HaversineDistanceService();

    private double km(double lat1, double lng1, double lat2, double lng2) {
        return service.distancesKm(new Point(lat1, lng1), List.of(new Point(lat2, lng2))).get(0);
    }

    @Test
    void theSamePointIsZeroKilometresAway() {
        assertThat(km(12.9352, 77.6245, 12.9352, 77.6245)).isZero();
    }

    @Test
    void oneDegreeOfLatitudeIsAbout111Kilometres() {
        // 2 * pi * 6371.0088 / 360
        assertThat(km(0, 0, 1, 0)).isCloseTo(111.195, within(0.01));
        assertThat(km(12, 77, 13, 77)).isCloseTo(111.195, within(0.01));
    }

    @Test
    void oneDegreeOfLongitudeShrinksTowardsThePoles() {
        assertThat(km(0, 0, 0, 1)).isCloseTo(111.195, within(0.01));
        // at 12.97 N a degree of longitude is 111.195 * cos(12.97 degrees)
        assertThat(km(12.97, 77.5, 12.97, 78.5)).isCloseTo(111.195 * Math.cos(Math.toRadians(12.97)), within(0.1));
        assertThat(km(60, 0, 60, 1)).isCloseTo(55.6, within(0.1));
    }

    @Test
    void theDistanceIsTheSameBothWays() {
        assertThat(km(12.9352, 77.6245, 12.9784, 77.6408)).isEqualTo(km(12.9784, 77.6408, 12.9352, 77.6245));
    }

    @Test
    void bengaluruNeighbourhoodsAreWhereTheyShouldBe() {
        // Koramangala to Indiranagar is about 5 km as the crow flies; Bengaluru to Mysuru is about 126 km
        assertThat(km(12.9352, 77.6245, 12.9784, 77.6408)).isBetween(4.8, 5.4);
        assertThat(km(12.9716, 77.5946, 12.2958, 76.6394)).isBetween(124.0, 129.0);
    }

    @Test
    void oppositePointsAreHalfTheEarthsCircumferenceApart() {
        assertThat(km(0, 0, 0, 180)).isCloseTo(Math.PI * 6371.0088, within(0.01));
        assertThat(km(90, 0, -90, 0)).isCloseTo(Math.PI * 6371.0088, within(0.01));
    }

    @Test
    void crossingTheDateLineTakesTheShortWay() {
        assertThat(km(0, 179.5, 0, -179.5)).isCloseTo(111.195, within(0.01));
    }

    @Test
    void aBatchReturnsOneDistancePerDestinationInOrder() {
        List<Double> d = service.distancesKm(new Point(0, 0), List.of(new Point(0, 2), new Point(0, 1), new Point(0, 0)));

        assertThat(d).hasSize(3);
        assertThat(d.get(0)).isGreaterThan(d.get(1));
        assertThat(d.get(2)).isZero();
        assertThat(service.distancesKm(new Point(0, 0), List.of())).isEmpty();
    }
}
