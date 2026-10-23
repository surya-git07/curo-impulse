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
 * Six-digit codes can be guessed, so wrong attempts per user are capped. Held in memory: it resets on restart
 * and is per instance, which is enough while there is one server.
 */
@Component
public class ShareCodeAttemptLimiter {

    @Value("${access.share-code.max-failed-attempts:5}")
    private int maxFailures;

    @Value("${access.share-code.attempt-window-minutes:15}")
    private int windowMinutes;

    @Autowired
    private Clock clock;

    private final Map<String, Deque<Instant>> failures = new HashMap<>();

    public synchronized void checkAllowed(String userId) {
        if (recentFailures(userId).size() >= maxFailures) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many wrong share codes. Try again in " + windowMinutes + " minutes.");
        }
    }

    public synchronized void recordFailure(String userId) {
        recentFailures(userId).addLast(clock.instant());
    }

    public synchronized void reset(String userId) {
        failures.remove(userId);
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
