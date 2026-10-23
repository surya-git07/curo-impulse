package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.DoseStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.Collection;

/**
 * The dose_logs queries that put a lower and an upper bound on scheduledAt. Both bounds go on ONE criteria for the field
 * (where("scheduledAt").gte(a).lt(b)). Calling where("scheduledAt") twice in a chain, which is what a derived repository method
 * with two scheduledAt conditions does, makes Spring Data throw "can't add a second 'scheduledAt' expression".
 * They are plain static builders so a test can build the real Query and call getQueryObject() without a database.
 */
public final class DoseQueries {

    private DoseQueries() {
    }

    /** One patient's doses with from <= scheduledAt < to, earliest first. */
    public static Query patientBetween(String patientId, Instant from, Instant to) {
        Criteria c = Criteria.where("patientId").is(patientId).and("scheduledAt").gte(from).lt(to);
        return Query.query(c).with(Sort.by(Sort.Direction.ASC, "scheduledAt"));
    }

    /** Doses in a status due after {@code after} and up to and including {@code upTo} that have not had a reminder yet. */
    public static Query remindersDue(DoseStatus status, Instant after, Instant upTo) {
        Criteria c = Criteria.where("status").is(status).and("scheduledAt").gt(after).lte(upTo).and("reminderSentAt").is(null);
        return Query.query(c).with(Sort.by(Sort.Direction.ASC, "scheduledAt"));
    }

    /** A patient's dose log: optional from (inclusive) and to (exclusive) on scheduledAt, limited to the given statuses. */
    public static Query log(String patientId, Instant from, Instant to, Collection<DoseStatus> statuses) {
        Criteria c = Criteria.where("patientId").is(patientId).and("status").in(statuses);
        if (from != null || to != null) {
            c = c.and("scheduledAt");
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
