package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.DoseLog;
import com.curo.curo_backend.model.DoseStatus;

import java.time.Instant;
import java.util.List;

/** The two dose queries that bound scheduledAt on both sides, built explicitly (see DoseQueries). */
public interface DoseLogRepositoryCustom {

    /** A patient's doses with from <= scheduledAt < to, earliest first. */
    List<DoseLog> findForPatientBetween(String patientId, Instant from, Instant to);

    /** Doses in a status due after {@code after} and up to {@code upTo} that have not had their reminder yet. */
    List<DoseLog> findRemindersDue(DoseStatus status, Instant after, Instant upTo);
}
