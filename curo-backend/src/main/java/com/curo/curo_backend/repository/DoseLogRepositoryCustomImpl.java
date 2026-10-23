package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.DoseLog;
import com.curo.curo_backend.model.DoseStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.List;

// Spring Data picks this up by name: the fragment interface name plus "Impl"
public class DoseLogRepositoryCustomImpl implements DoseLogRepositoryCustom {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public List<DoseLog> findForPatientBetween(String patientId, Instant from, Instant to) {
        return mongoTemplate.find(DoseQueries.patientBetween(patientId, from, to), DoseLog.class);
    }

    @Override
    public List<DoseLog> findRemindersDue(DoseStatus status, Instant after, Instant upTo) {
        return mongoTemplate.find(DoseQueries.remindersDue(status, after, upTo), DoseLog.class);
    }
}
