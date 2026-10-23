package com.curo.curo_backend.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.curo.curo_backend.model.Pharmacy;
import com.curo.curo_backend.model.PharmacyStock;
import com.curo.curo_backend.repository.PharmacyRepository;
import com.curo.curo_backend.repository.PharmacyStockRepository;
import com.curo.curo_backend.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PharmacySeederTest {

    private final List<Pharmacy> pharmacies = new ArrayList<>();
    private final List<PharmacyStock> stock = new ArrayList<>();
    private PharmacyRepository pharmacyRepo;
    private PharmacyStockRepository stockRepo;
    private PharmacySeeder seeder;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        pharmacyRepo = mock(PharmacyRepository.class);
        stockRepo = mock(PharmacyStockRepository.class);
        when(pharmacyRepo.findBySeedKey(anyString())).thenAnswer(inv -> pharmacies.stream().filter(p -> inv.getArgument(0).equals(p.getSeedKey())).findFirst());
        when(pharmacyRepo.save(any(Pharmacy.class))).thenAnswer(inv -> {
            Pharmacy p = inv.getArgument(0);
            p.setId("ph" + (pharmacies.size() + 1));
            pharmacies.add(p);
            return p;
        });
        when(stockRepo.existsByPharmacyIdAndNormalizedDrug(anyString(), anyString())).thenAnswer(inv ->
                stock.stream().anyMatch(s -> s.getPharmacyId().equals(inv.getArgument(0)) && s.getNormalizedDrug().equals(inv.getArgument(1))));
        when(stockRepo.save(any(PharmacyStock.class))).thenAnswer(inv -> {
            stock.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        seeder = new PharmacySeeder();
        ReflectionTestUtils.setField(seeder, "seedOnStartup", true);
        ReflectionTestUtils.setField(seeder, "pharmacyRepository", pharmacyRepo);
        ReflectionTestUtils.setField(seeder, "stockRepository", stockRepo);
        ReflectionTestUtils.setField(seeder, "clock", new MutableClock(Instant.parse("2026-10-23T10:00:00Z")));
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(PharmacySeeder.class)).addAppender(logs);
    }

    @Test
    void createsVerifiedPharmaciesWithLocationAndStock() {
        seeder.run(null);

        assertThat(pharmacies).hasSameSizeAs(PharmacySeed.entries());
        assertThat(pharmacies).allSatisfy(p -> {
            assertThat(p.isVerified()).isTrue();
            assertThat(p.hasLocation()).isTrue();
            assertThat(p.getState()).isEqualTo("KA");
            assertThat(p.getOwnerUserId()).isNull();
        });
        int expectedRows = PharmacySeed.entries().stream().mapToInt(e -> e.stock().size()).sum();
        assertThat(stock).hasSize(expectedRows);
        assertThat(stock).allSatisfy(s -> {
            assertThat(s.getNormalizedDrug()).isEqualTo(s.getNormalizedDrug().toLowerCase());
            assertThat(s.getUpdatedAt()).isEqualTo(Instant.parse("2026-10-23T10:00:00Z"));
        });
    }

    @Test
    void runningTwiceAddsNothingTheSecondTime() {
        seeder.run(null);
        int pharmacyCount = pharmacies.size();
        int stockCount = stock.size();

        seeder.run(null);

        assertThat(pharmacies).hasSize(pharmacyCount);
        assertThat(stock).hasSize(stockCount);
    }

    @Test
    void anEditedQuantityIsNeverOverwritten() {
        seeder.run(null);
        PharmacyStock edited = stock.get(0);
        edited.setQuantity(7);

        seeder.run(null);

        assertThat(edited.getQuantity()).isEqualTo(7);
    }

    @Test
    void anUnreachableDatabaseIsOnlyAWarningWithNoConnectionDetails() {
        when(pharmacyRepo.findBySeedKey(anyString()))
                .thenThrow(new DataAccessResourceFailureException("Timed out connecting to mongodb://user:hunter2@db.example.net"));

        assertThatCode(() -> seeder.run(null)).doesNotThrowAnyException();

        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(logs.list.get(0).getFormattedMessage()).contains("DataAccessResourceFailureException")
                .doesNotContain("hunter2", "mongodb://", "db.example.net");
        assertThat(logs.list.get(0).getThrowableProxy()).isNull();
    }

    @Test
    void canBeSwitchedOff() {
        ReflectionTestUtils.setField(seeder, "seedOnStartup", false);

        seeder.run(null);

        verify(pharmacyRepo, never()).findBySeedKey(anyString());
        assertThat(Optional.of(pharmacies)).get().asList().isEmpty();
    }
}
