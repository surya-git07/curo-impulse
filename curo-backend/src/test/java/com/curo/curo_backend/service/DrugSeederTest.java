package com.curo.curo_backend.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.curo.curo_backend.model.DrugMaster;
import com.curo.curo_backend.repository.DrugMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DrugSeederTest {

    private DrugMasterRepository repo;
    private DrugSeeder seeder;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        repo = mock(DrugMasterRepository.class);
        seeder = new DrugSeeder();
        ReflectionTestUtils.setField(seeder, "drugMasterRepository", repo);
        ReflectionTestUtils.setField(seeder, "seedOnStartup", true);
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(DrugSeeder.class)).addAppender(logs);
    }

    @Test
    void insertsEveryDrugIntoAnEmptyCollection() {
        seeder.run(null);

        verify(repo, times(DrugSeed.entries().size())).save(any(DrugMaster.class));
        assertThat(logs.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.INFO);
    }

    @Test
    void secondRunAddsNothing() {
        when(repo.existsByNormalizedName(anyString())).thenReturn(true);

        seeder.run(null);

        verify(repo, never()).save(any());
    }

    @Test
    void unreachableMongoIsOnlyAWarningAndStopsAtTheFirstFailure() {
        when(repo.existsByNormalizedName(anyString()))
                .thenThrow(new DataAccessResourceFailureException("Timed out connecting to mongodb://user:hunter2@db.example.net"));

        assertThatCode(() -> seeder.run(null)).doesNotThrowAnyException();

        verify(repo, times(1)).existsByNormalizedName(anyString());
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

        verify(repo, never()).existsByNormalizedName(anyString());
    }
}
