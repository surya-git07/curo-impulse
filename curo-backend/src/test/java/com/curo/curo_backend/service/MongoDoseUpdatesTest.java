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
    void markMissedOnlyTouchesThisDoseWhileItIsStillUpcoming() {
        UpdateResult one = modified(1);
        when(template.updateFirst(any(Query.class), any(Update.class), eq(DoseLog.class))).thenReturn(one);

        assertThat(updates.markMissed("d1")).isTrue();

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> u = ArgumentCaptor.forClass(Update.class);
        verify(template).updateFirst(q.capture(), u.capture(), eq(DoseLog.class));
        String filter = q.getValue().getQueryObject().toString();
        assertThat(filter).contains("d1").contains("UPCOMING").doesNotContain("TAKEN");
        String change = u.getValue().getUpdateObject().toString();
        assertThat(change).contains("MISSED").doesNotContain("takenAt");
    }

    @Test
    void markMissedReportsFalseWhenTheDoseWasNoLongerUpcoming() {
        UpdateResult none = modified(0);
        when(template.updateFirst(any(Query.class), any(Update.class), eq(DoseLog.class))).thenReturn(none);

        assertThat(updates.markMissed("d1")).isFalse();
    }

    @Test
    void theReminderIsClaimedOnlyWhileNoneWasSentBefore() {
        UpdateResult one = modified(1);
        when(template.updateFirst(any(Query.class), any(Update.class), eq(DoseLog.class))).thenReturn(one);

        assertThat(updates.markReminderSent("d1", Instant.parse("2026-10-23T10:00:00Z"))).isTrue();

        ArgumentCaptor<Query> q = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> u = ArgumentCaptor.forClass(Update.class);
        verify(template).updateFirst(q.capture(), u.capture(), eq(DoseLog.class));
        assertThat(q.getValue().getQueryObject().toString()).contains("d1").contains("reminderSentAt");
        assertThat(u.getValue().getUpdateObject().toString()).contains("reminderSentAt").contains("2026-10-23T10:00:00Z");
    }

    @Test
    void aReminderAlreadySentReportsFalse() {
        UpdateResult none = modified(0);
        when(template.updateFirst(any(Query.class), any(Update.class), eq(DoseLog.class))).thenReturn(none);

        assertThat(updates.markReminderSent("d1", Instant.now())).isFalse();
    }
}
