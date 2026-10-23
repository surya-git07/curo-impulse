package com.curo.curo_backend.service;

import com.curo.curo_backend.model.DoseLog;
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
class MongoDoseUpdatesTest {

    private MongoTemplate template;
    private MongoDoseUpdates updates;

    @BeforeEach
    void setUp() {
        template = mock(MongoTemplate.class);
        updates = new MongoDoseUpdates();
        ReflectionTestUtils.setField(updates, "mongoTemplate", template);
    }

    private UpdateResult modified(long n) {
        UpdateResult r = mock(UpdateResult.class);
        when(r.getModifiedCount()).thenReturn(n);
        return r;
    }

    @Test
    void markTakenOnlyTouchesTheDoseWhileItIsNotAlreadyTaken() {
        UpdateResult result1 = modified(1);
        when(template.updateFirst(any(Query.class), any(Update.class), eq(DoseLog.class))).thenReturn(result1);

        assertThat(updates.markTaken("d1", Instant.parse("2026-10-23T10:00:00Z"), true, "p1")).isTrue();

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> u = ArgumentCaptor.forClass(Update.class);
        verify(template).updateFirst(q.capture(), u.capture(), eq(DoseLog.class));
        assertThat(q.getValue().getQueryObject().toString()).contains("d1").contains("$ne").contains("TAKEN");
        assertThat(u.getValue().getUpdateObject().toString()).contains("status").contains("takenAt").contains("late").contains("p1");
    }

    @Test
    void markTakenReportsFalseWhenNothingChanged() {
        UpdateResult result0 = modified(0);
        when(template.updateFirst(any(Query.class), any(Update.class), eq(DoseLog.class))).thenReturn(result0);

        assertThat(updates.markTaken("d1", Instant.now(), false, "p1")).isFalse();
    }

    @Test
    void markMissedOnlyTouchesUpcomingDosesDueByTheCutoff() {
        UpdateResult result3 = modified(3);
        when(template.updateMulti(any(Query.class), any(Update.class), eq(DoseLog.class))).thenReturn(result3);

        assertThat(updates.markMissed(Instant.parse("2026-10-23T09:30:00Z"))).isEqualTo(3);

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> u = ArgumentCaptor.forClass(Update.class);
        verify(template).updateMulti(q.capture(), u.capture(), eq(DoseLog.class));
        String filter = q.getValue().getQueryObject().toString();
        assertThat(filter).contains("UPCOMING").contains("$lte").contains("2026-10-23T09:30:00Z");
        assertThat(filter).doesNotContain("TAKEN");
        String change = u.getValue().getUpdateObject().toString();
        assertThat(change).contains("MISSED").doesNotContain("takenAt");
    }
}
