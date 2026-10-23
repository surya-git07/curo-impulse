package com.curo.curo_backend.service;

import com.curo.curo_backend.model.LicenceRegistryEntry;
import com.curo.curo_backend.repository.LicenceRegistryRepository;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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

class LicenceRegistrySeederTest {

    private LicenceRegistryRepository repo;
    private LicenceRegistrySeeder seeder;

    @BeforeEach
    void setUp() {
        repo = mock(LicenceRegistryRepository.class);
        seeder = new LicenceRegistrySeeder();
        ReflectionTestUtils.setField(seeder, "registryRepository", repo);
        ReflectionTestUtils.setField(seeder, "seedOnStartup", true);
    }

    @Test
    void insertsEveryEntryIntoAnEmptyCollection() {
        seeder.run(null);

        verify(repo, times(LicenceRegistrySeed.entries().size())).save(any(LicenceRegistryEntry.class));
    }

    @Test
    void secondRunAddsNothing() {
        when(repo.existsByTypeAndLicenceNumber(anyString(), anyString())).thenReturn(true);

        seeder.run(null);

        verify(repo, never()).save(any());
    }

    @Test
    void addsOnlyTheMissingRecords() {
        when(repo.existsByTypeAndLicenceNumber(anyString(), anyString())).thenReturn(true);
        when(repo.existsByTypeAndLicenceNumber("DOCTOR", "TN-48213")).thenReturn(false);

        seeder.run(null);

        verify(repo, times(1)).save(any(LicenceRegistryEntry.class));
    }

    private ListAppender<ILoggingEvent> captureLogs() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(LicenceRegistrySeeder.class)).addAppender(appender);
        return appender;
    }

    @Test
    void unreachableMongoDoesNotStopStartupAndLogsAWarningWithoutTheMessage() {
        ListAppender<ILoggingEvent> logs = captureLogs();
        String secretish = "mongodb://user:hunter2@db.example.net:27017";
        when(repo.existsByTypeAndLicenceNumber(anyString(), anyString()))
                .thenThrow(new DataAccessResourceFailureException("Timed out connecting to " + secretish));

        assertThatCode(() -> seeder.run(null)).doesNotThrowAnyException();

        assertThat(logs.list).hasSize(1);
        ILoggingEvent event = logs.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage())
                .contains("seed skipped", "DataAccessResourceFailureException")
                .doesNotContain("hunter2", "mongodb://", "db.example.net");
        assertThat(event.getThrowableProxy()).as("no stack trace, it would carry the message").isNull();
    }

    @Test
    void stopsAtTheFirstFailureInsteadOfRetryingEveryRecord() {
        captureLogs();
        when(repo.existsByTypeAndLicenceNumber(anyString(), anyString())).thenThrow(new IllegalStateException("down"));

        seeder.run(null);

        verify(repo, times(1)).existsByTypeAndLicenceNumber(anyString(), anyString());
        verify(repo, never()).save(any());
    }

    @Test
    void aFailurePartWayThroughKeepsWhatWasAddedAndReportsTheCount() {
        ListAppender<ILoggingEvent> logs = captureLogs();
        when(repo.existsByTypeAndLicenceNumber(anyString(), anyString())).thenReturn(false);
        when(repo.save(any(LicenceRegistryEntry.class)))
                .thenAnswer(inv -> inv.getArgument(0))
                .thenAnswer(inv -> inv.getArgument(0))
                .thenThrow(new IllegalStateException("down"));

        assertThatCode(() -> seeder.run(null)).doesNotThrowAnyException();

        verify(repo, times(3)).save(any(LicenceRegistryEntry.class));
        assertThat(logs.list.get(0).getFormattedMessage()).contains("after 2 records");
    }

    @Test
    void successStillLogsInfoNotAWarning() {
        ListAppender<ILoggingEvent> logs = captureLogs();

        seeder.run(null);

        assertThat(logs.list).extracting(ILoggingEvent::getLevel).containsExactly(Level.INFO);
    }

    @Test
    void canBeSwitchedOff() {
        ReflectionTestUtils.setField(seeder, "seedOnStartup", false);

        seeder.run(null);

        verify(repo, never()).save(any());
        verify(repo, never()).existsByTypeAndLicenceNumber(anyString(), anyString());
    }
}
