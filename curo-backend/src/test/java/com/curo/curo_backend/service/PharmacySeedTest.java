package com.curo.curo_backend.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PharmacySeedTest {

    private final List<PharmacySeed.Entry> entries = PharmacySeed.entries();

    @Test
    void thereAreAFewPharmacies() {
        assertThat(entries.size()).isBetween(3, 8);
    }

    @Test
    void everyPharmacyIsInsideBengaluru() {
        for (PharmacySeed.Entry e : entries) {
            assertThat(e.lat()).as(e.name()).isBetween(12.80, 13.20);
            assertThat(e.lng()).as(e.name()).isBetween(77.40, 77.80);
            assertThat(e.address()).as(e.name()).contains("Bengaluru");
        }
    }

    @Test
    void namesKeysAndPlacesAreDistinct() {
        assertThat(entries.stream().map(PharmacySeed.Entry::seedKey).collect(Collectors.toSet())).hasSameSizeAs(entries);
        assertThat(entries.stream().map(PharmacySeed.Entry::name).collect(Collectors.toSet())).hasSameSizeAs(entries);
        Set<String> places = new HashSet<>();
        for (PharmacySeed.Entry e : entries) {
            assertThat(places.add(e.lat() + "," + e.lng())).as("coordinates of " + e.name()).isTrue();
        }
    }

    @Test
    void everyStockedDrugIsInTheDrugList() {
        Set<String> known = DrugSeed.entries().stream().map(d -> d.getName()).collect(Collectors.toSet());
        for (PharmacySeed.Entry e : entries) {
            assertThat(known).as(e.name()).containsAll(e.stock().keySet());
        }
    }

    @Test
    void stockIsRealisticAndIncludesDeliberateOutOfStockCases() {
        for (PharmacySeed.Entry e : entries) {
            assertThat(e.stock().size()).as(e.name()).isBetween(8, 20);
            assertThat(e.stock().values()).as(e.name()).allSatisfy(q -> assertThat(q).isBetween(0, 1000));
            assertThat(e.stock().get("Paracetamol")).as(e.name() + " sells paracetamol").isPositive();
        }
        long zeroes = entries.stream().flatMap(e -> e.stock().values().stream()).filter(q -> q == 0).count();
        assertThat(zeroes).as("out-of-stock demo rows").isGreaterThanOrEqualTo(2);
    }

    @Test
    void amoxicillinIsInStockAtSomeButNotAll() {
        long inStock = entries.stream().filter(e -> e.stock().getOrDefault("Amoxicillin", 0) > 0).count();
        assertThat(inStock).isBetween(2L, (long) entries.size() - 1);
    }

    @Test
    void namesAreMarkedAsInventedInTheSource() {
        // the doc comment is the record of that; this just keeps the demo names from looking like a real chain
        assertThat(entries).allSatisfy(e -> assertThat(e.seedKey()).startsWith("demo-"));
    }
}
