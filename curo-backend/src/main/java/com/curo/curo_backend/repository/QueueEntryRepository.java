package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.QueueEntry;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Day-range lookups live in QueueQueries: a derived method with two checkedInAt conditions would fail at runtime. */
public interface QueueEntryRepository extends MongoRepository<QueueEntry, String> {
}
