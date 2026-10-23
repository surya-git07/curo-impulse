package com.curo.curo_backend.repository;

import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.Locale;

/** The audit_logs query for one patient's access log. Both time bounds go on one criteria for the field "at". */
public final class AuditQueries {

    private AuditQueries() {
    }

    /** One patient's entries, optionally from (inclusive) and to (exclusive), optionally by the role of whoever acted. */
    public static Query forPatient(String patientId, Instant from, Instant to, String actorRole) {
        Criteria c = Criteria.where("patientId").is(patientId);
        if (actorRole != null && !actorRole.isBlank()) {
            c = c.and("actorRole").is(actorRole.trim().toUpperCase(Locale.ROOT));
        }
        if (from != null || to != null) {
            c = c.and("at");
            if (from != null) {
                c = c.gte(from);
            }
            if (to != null) {
                c = c.lt(to);
            }
        }
        return Query.query(c);
    }
}
