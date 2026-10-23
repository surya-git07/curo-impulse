package com.curo.curo_backend.service;

import com.curo.curo_backend.model.PharmacyStock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class MongoStockUpdates implements StockUpdates {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void set(String pharmacyId, String normalizedDrug, String displayDrug, int quantity, Instant at) {
        Query query = Query.query(Criteria.where("pharmacyId").is(pharmacyId).and("normalizedDrug").is(normalizedDrug));
        Update update = new Update().set("drug", displayDrug).set("quantity", quantity).set("updatedAt", at);
        mongoTemplate.upsert(query, update, PharmacyStock.class);
    }

    @Override
    public boolean decrement(String pharmacyId, String normalizedDrug, int quantity, Instant at) {
        // the quantity >= test and the subtraction happen in one database operation
        Query query = Query.query(Criteria.where("pharmacyId").is(pharmacyId).and("normalizedDrug").is(normalizedDrug)
                .and("quantity").gte(quantity));
        Update update = new Update().inc("quantity", -quantity).set("updatedAt", at);
        return mongoTemplate.updateFirst(query, update, PharmacyStock.class).getModifiedCount() > 0;
    }

    @Override
    public void increment(String pharmacyId, String normalizedDrug, int quantity, Instant at) {
        Query query = Query.query(Criteria.where("pharmacyId").is(pharmacyId).and("normalizedDrug").is(normalizedDrug));
        mongoTemplate.updateFirst(query, new Update().inc("quantity", quantity).set("updatedAt", at), PharmacyStock.class);
    }
}
