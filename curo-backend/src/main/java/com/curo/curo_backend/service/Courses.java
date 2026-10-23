package com.curo.curo_backend.service;

import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.model.PrescriptionStatus;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/** When a prescribed course starts and ends, and whether it is current. Days are counted in the app's local time zone. */
final class Courses {

    private Courses() {
    }

    static LocalDate startDate(Prescription p, ZoneId zone) {
        return p.getIssuedAt().atZone(zone).toLocalDate();
    }

    /** Last day of the course, inclusive: a 7-day course issued on the 23rd ends on the 29th. Empty when no duration was given. */
    static Optional<LocalDate> endDate(Prescription p, ZoneId zone) {
        return p.getDurationDays() == null
                ? Optional.empty()
                : Optional.of(startDate(p, zone).plusDays(p.getDurationDays() - 1L));
    }

    /** Not discontinued, and not past its last day. A prescription with no duration stays current until discontinued. */
    static boolean isCurrent(Prescription p, LocalDate today, ZoneId zone) {
        if (p.getStatus() != PrescriptionStatus.ACTIVE) {
            return false;
        }
        return endDate(p, zone).map(end -> !today.isAfter(end)).orElse(true);
    }
}
