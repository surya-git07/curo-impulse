package com.curo.curo_backend.service;

import com.curo.curo_backend.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShareCodeAttemptLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-23T10:00:00Z"));
    private ShareCodeAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new ShareCodeAttemptLimiter();
        ReflectionTestUtils.setField(limiter, "maxFailures", 5);
        ReflectionTestUtils.setField(limiter, "windowMinutes", 15);
        ReflectionTestUtils.setField(limiter, "clock", clock);
    }

    private void fail(String user, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(user);
        }
    }

    @Test
    void fourFailuresStillAllowAFifthTry() {
        fail("d1", 4);
        assertThatCode(() -> limiter.checkAllowed("d1")).doesNotThrowAnyException();
    }

    @Test
    void fiveFailuresLockTheUserOutWith429() {
        fail("d1", 5);

        assertThatThrownBy(() -> limiter.checkAllowed("d1")).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(e.getCode()).isEqualTo("TOO_MANY_ATTEMPTS");
        });
    }

    @Test
    void lockLiftsOnceTheFailuresAgeOut() {
        fail("d1", 5);
        clock.advance(Duration.ofMinutes(14));
        assertThatThrownBy(() -> limiter.checkAllowed("d1")).isInstanceOf(ApiException.class);

        clock.advance(Duration.ofMinutes(1));
        assertThatCode(() -> limiter.checkAllowed("d1")).doesNotThrowAnyException();
    }

    @Test
    void failuresAreCountedPerUser() {
        fail("d1", 5);
        assertThatCode(() -> limiter.checkAllowed("ph1")).doesNotThrowAnyException();
    }

    @Test
    void successClearsTheCount() {
        fail("d1", 4);
        limiter.reset("d1");
        fail("d1", 4);
        assertThatCode(() -> limiter.checkAllowed("d1")).doesNotThrowAnyException();
    }

    @Test
    void failuresSpreadOverTimeSlideOutOfTheWindowOneByOne() {
        fail("d1", 3);
        clock.advance(Duration.ofMinutes(10));
        fail("d1", 2);
        assertThatThrownBy(() -> limiter.checkAllowed("d1")).isInstanceOf(ApiException.class);

        clock.advance(Duration.ofMinutes(6)); // the first three are now 16 minutes old
        assertThatCode(() -> limiter.checkAllowed("d1")).doesNotThrowAnyException();
    }
}
