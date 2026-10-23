package com.curo.curo_backend.service;

import com.curo.curo_backend.model.LicenceRegistryEntry;
import com.curo.curo_backend.util.GstinValidator;
import com.curo.curo_backend.util.IndianStates;
import com.curo.curo_backend.util.LicenceNumbers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class LicenceRegistrySeedTest {

    private final List<LicenceRegistryEntry> all = LicenceRegistrySeed.entries();
    private final List<LicenceRegistryEntry> doctors = all.stream().filter(e -> LicenceRegistryEntry.DOCTOR.equals(e.getType())).toList();
    private final List<LicenceRegistryEntry> pharmacies = all.stream().filter(e -> LicenceRegistryEntry.PHARMACY.equals(e.getType())).toList();

    @Test
    void hasTenDoctorsAndFivePharmacies() {
        assertThat(doctors).hasSize(10);
        assertThat(pharmacies).hasSize(5);
        assertThat(all).hasSize(15);
    }

    @Test
    void licenceNumbersAreUniquePerTypeAndAlreadyNormalised() {
        for (List<LicenceRegistryEntry> group : List.of(doctors, pharmacies)) {
            Set<String> numbers = group.stream().map(LicenceRegistryEntry::getLicenceNumber).collect(Collectors.toSet());
            assertThat(numbers).hasSameSizeAs(group);
            group.forEach(e -> assertThat(e.getLicenceNumber()).isEqualTo(LicenceNumbers.normalize(e.getLicenceNumber())));
        }
    }

    @Test
    void doctorsHaveValidNumbersCouncilsAndStates() {
        for (LicenceRegistryEntry d : doctors) {
            assertThat(LicenceNumbers.isValidDoctorNumber(d.getLicenceNumber())).as(d.getLicenceNumber()).isTrue();
            assertThat(d.getName()).isNotBlank();
            assertThat(d.getStateCouncil()).endsWith("Medical Council");
            assertThat(IndianStates.isKnownIso(d.getState())).isTrue();
            assertThat(d.getGstin()).isNull();
        }
    }

    @Test
    void pharmaciesHaveValidNumbersAndGstinsMatchingTheirState() {
        for (LicenceRegistryEntry p : pharmacies) {
            assertThat(LicenceNumbers.isValidDrugLicenceNumber(p.getLicenceNumber())).as(p.getLicenceNumber()).isTrue();
            assertThat(p.getName()).isNotBlank();
            assertThat(GstinValidator.validate(p.getGstin(), p.getState())).as(p.getGstin())
                    .isEqualTo(GstinValidator.Result.VALID);
            assertThat(p.getStateCouncil()).isNull();
        }
    }

    @Test
    void everyRecordIsMarkedAsMockData() {
        assertThat(all).allSatisfy(e -> assertThat(e.getSource()).isEqualTo("MOCK"));
    }

    @Test
    void namesAreDistinct() {
        assertThat(all.stream().map(LicenceRegistryEntry::getName).collect(Collectors.toSet())).hasSize(15);
    }

    @Test
    void entriesAreFreshObjectsEachCall() {
        assertThat(LicenceRegistrySeed.entries()).isNotSameAs(LicenceRegistrySeed.entries());
    }
}
