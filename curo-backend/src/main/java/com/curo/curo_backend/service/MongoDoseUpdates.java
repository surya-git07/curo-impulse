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
    public long markMissed(Instant cutoff) {
        Query query = Query.query(Criteria.where("status").is(DoseStatus.UPCOMING).and("scheduledAt").lte(cutoff));
        return mongoTemplate.updateMulti(query, new Update().set("status", DoseStatus.MISSED), DoseLog.class).getModifiedCount();
    }
}
