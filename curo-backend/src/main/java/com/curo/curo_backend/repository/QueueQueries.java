package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.QueueStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;

/** queue_entries queries with both bounds on checkedInAt chained on one criteria. */
public final class QueueQueries {

    private QueueQueries() {
    }

    /** This doctor's entries checked in at or after from and before to, earliest first. */
    public static Query betweenFor(String doctorId, Instant from, Instant to) {
        return Query.query(Criteria.where("doctorId").is(doctorId).and("checkedInAt").gte(from).lt(to))
                .with(Sort.by(Sort.Direction.ASC, "checkedInAt"));
    }

    /** This patient's entries for this doctor in the window that are still WAITING. */
    public static Query waitingFor(String careId, String doctorId, Instant from, Instant to) {
        return Query.query(Criteria.where("careId").is(careId).and("doctorId").is(doctorId)
                .and("status").is(QueueStatus.WAITING).and("checkedInAt").gte(from).lt(to));
    }
}
