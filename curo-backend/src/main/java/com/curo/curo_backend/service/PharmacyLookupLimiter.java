package com.curo.curo_backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Stops a pharmacy account from probing for CareIDs: lookups of CareIDs that are not patients are capped per user in a
 * window. Same approach as ShareCodeAttemptLimiter (in memory, per instance), with its own counter so the two never mix.
 */
@Component
public class PharmacyLookupLimiter {

    @Value("${pharmacy.lookup.max-failed-lookups:10}")
    private int maxFailures;

    @Value("${pharmacy.lookup.window-minutes:15}")
    private int windowMinutes;

    @Autowired
    private Clock clock;

    private final Map<String, Deque<Instant>> failures = new HashMap<>();

    public synchronized void checkAllowed(String userId) {
        if (recentFailures(userId).size() >= maxFailures) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many lookups for unknown CareIDs. Try again in " + windowMinutes + " minutes.");
        }
    }

    public synchronized void recordFailure(String userId) {
        recentFailures(userId).addLast(clock.instant());
    }

    private Deque<Instant> recentFailures(String userId) {
        Deque<Instant> deque = failures.computeIfAbsent(userId, k -> new ArrayDeque<>());
        Instant cutoff = clock.instant().minus(Duration.ofMinutes(windowMinutes));
        while (!deque.isEmpty() && !deque.peekFirst().isAfter(cutoff)) {
            deque.removeFirst();
        }
        return deque;
    }
}
