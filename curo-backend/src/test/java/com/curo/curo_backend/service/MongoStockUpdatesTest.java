package com.curo.curo_backend.service;

import com.curo.curo_backend.model.PharmacyStock;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** What the real MongoDB updates ask for. There is no database here, so this checks the filter and the change they send. */
class MongoStockUpdatesTest {

    private static final Instant AT = Instant.parse("2026-10-23T10:00:00Z");

    private MongoTemplate template;
    private MongoStockUpdates updates;

    @BeforeEach
    void setUp() {
        template = mock(MongoTemplate.class);
        updates = new MongoStockUpdates();
        ReflectionTestUtils.setField(updates, "mongoTemplate", template);
    }

    private UpdateResult modified(long n) {
        UpdateResult r = mock(UpdateResult.class);
        when(r.getModifiedCount()).thenReturn(n);
        return r;
    }

    @Test
    void decrementOnlyActsWhileEnoughIsThereAndSubtractsInTheDatabase() {
        UpdateResult one = modified(1);
        when(template.updateFirst(any(Query.class), any(Update.class), eq(PharmacyStock.class))).thenReturn(one);

        assertThat(updates.decrement("ph1", "amoxicillin", 4, AT)).isTrue();

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> u = ArgumentCaptor.forClass(Update.class);
        verify(template).updateFirst(q.capture(), u.capture(), eq(PharmacyStock.class));
        String filter = q.getValue().getQueryObject().toString();
        assertThat(filter).contains("ph1").contains("amoxicillin").contains("quantity").contains("$gte").contains("4");
        String change = u.getValue().getUpdateObject().toString();
        assertThat(change).contains("$inc").contains("-4").contains("updatedAt");
    }

    @Test
    void decrementReportsFalseWhenNothingMatched() {
        UpdateResult none = modified(0);
        when(template.updateFirst(any(Query.class), any(Update.class), eq(PharmacyStock.class))).thenReturn(none);

        assertThat(updates.decrement("ph1", "amoxicillin", 99, AT)).isFalse();
    }

    @Test
    void incrementAddsBack() {
        updates.increment("ph1", "amoxicillin", 4, AT);

        ArgumentCaptor<Update> u = ArgumentCaptor.forClass(Update.class);
        verify(template).updateFirst(any(Query.class), u.capture(), eq(PharmacyStock.class));
        assertThat(u.getValue().getUpdateObject().toString()).contains("$inc").doesNotContain("-4");
    }

    @Test
    void setUpsertsTheAbsoluteQuantity() {
        updates.set("ph1", "amoxicillin", "Amoxicillin", 25, AT);

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> u = ArgumentCaptor.forClass(Update.class);
        verify(template).upsert(q.capture(), u.capture(), eq(PharmacyStock.class));
        assertThat(q.getValue().getQueryObject().toString()).contains("ph1").contains("amoxicillin");
        assertThat(u.getValue().getUpdateObject().toString()).contains("$set").contains("quantity").contains("25").contains("Amoxicillin")
                .doesNotContain("$inc");
    }
}
