package com.curo.curo_backend.service;

import com.curo.curo_backend.model.DoseLog;
import com.curo.curo_backend.model.DoseStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class MongoDoseUpdates implements DoseUpdates {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public boolean markTaken(String doseId, Instant takenAt, boolean late, String takenByUserId) {
        Query query = Query.query(Criteria.where("id").is(doseId).and("status").ne(DoseStatus.TAKEN));
        Update update = new Update().set("status", DoseStatus.TAKEN).set("takenAt", takenAt).set("late", late)
                .set("takenByUserId", takenByUserId);
        return mongoTemplate.updateFirst(query, update, DoseLog.class).getModifiedCount() > 0;
    }

    @Override
    public boolean markMissed(String doseId) {
        Query query = Query.query(Criteria.where("id").is(doseId).and("status").is(DoseStatus.UPCOMING));
        return mongoTemplate.updateFirst(query, new Update().set("status", DoseStatus.MISSED), DoseLog.class).getModifiedCount() > 0;
    }

    @Override
    public boolean markReminderSent(String doseId, Instant at) {
        // reminderSentAt is null: the same operation that checks it also sets it
        Query query = Query.query(Criteria.where("id").is(doseId).and("reminderSentAt").is(null));
        return mongoTemplate.updateFirst(query, new Update().set("reminderSentAt", at), DoseLog.class).getModifiedCount() > 0;
    }
}
