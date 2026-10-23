package com.curo.curo_backend.repository;

import com.curo.curo_backend.model.DoseLog;
import com.curo.curo_backend.model.DoseStatus;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.core.support.AbstractRepositoryMetadata;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * getQueryObject() is where Spring Data throws "can't add a second 'x' expression", and it needs no database,
 * so these tests catch that class of bug offline.
 */
class QueryShapeTest {

    private static final Instant A = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant B = Instant.parse("2026-01-02T00:00:00Z");

    private static Document field(Document q, String name) {
        assertEquals(1, q.keySet().stream().filter(name::equals).count());
        return (Document) q.get(name);
    }

    @Test
    void patientBetweenHasOneScheduledAtWithBothBounds() {
        Document q = DoseQueries.patientBetween("p1", A, B).getQueryObject();
        Document s = field(q, "scheduledAt");
        assertEquals(Set.of("$gte", "$lt"), s.keySet());
        assertEquals("p1", q.get("patientId"));
    }

    @Test
    void remindersDueHasOneScheduledAtWithBothBounds() {
        Document q = DoseQueries.remindersDue(DoseStatus.UPCOMING, A, B).getQueryObject();
        Document s = field(q, "scheduledAt");
        assertEquals(Set.of("$gt", "$lte"), s.keySet());
        assertTrue(q.containsKey("reminderSentAt"));
        assertNull(q.get("reminderSentAt"));
    }

    @Test
    void doseLogHasOneScheduledAtWithBothBounds() {
        Document q = DoseQueries.log("p1", A, B, List.of(DoseStatus.TAKEN, DoseStatus.MISSED)).getQueryObject();
        assertEquals(Set.of("$gte", "$lt"), field(q, "scheduledAt").keySet());
        assertTrue(q.containsKey("status"));
    }

    @Test
    void doseLogWithOneOrNoBound() {
        List<DoseStatus> st = List.of(DoseStatus.UPCOMING);
        assertEquals(Set.of("$gte"), field(DoseQueries.log("p1", A, null, st).getQueryObject(), "scheduledAt").keySet());
        assertEquals(Set.of("$lt"), field(DoseQueries.log("p1", null, B, st).getQueryObject(), "scheduledAt").keySet());
        assertFalse(DoseQueries.log("p1", null, null, st).getQueryObject().containsKey("scheduledAt"));
    }

    @Test
    void auditHasOneAtWithBothBounds() {
        Document q = AuditQueries.forPatient("p1", A, B, "doctor").getQueryObject();
        assertEquals(Set.of("$gte", "$lt"), field(q, "at").keySet());
        assertEquals("DOCTOR", q.get("actorRole"));
        assertFalse(AuditQueries.forPatient("p1", null, null, null).getQueryObject().containsKey("at"));
    }

    @Test
    void repeatingAFieldInOneChainThrows() {
        Criteria c = Criteria.where("patientId").is("p1").and("scheduledAt").gte(A).and("scheduledAt").lt(B);
        assertThrows(RuntimeException.class, () -> Query.query(c).getQueryObject());
    }

    @Test
    void fragmentRunsTheExplicitQueries() {
        MongoTemplate template = mock(MongoTemplate.class);
        DoseLogRepositoryCustomImpl impl = new DoseLogRepositoryCustomImpl();
        ReflectionTestUtils.setField(impl, "mongoTemplate", template);
        impl.findForPatientBetween("p1", A, B);
        impl.findRemindersDue(DoseStatus.UPCOMING, A, B);
        verify(template, org.mockito.Mockito.times(2)).find(any(Query.class), eq(DoseLog.class));
    }

    /** No derived repository method may use the same property twice in one condition group. */
    @Test
    void noDerivedMethodRepeatsAProperty() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(org.springframework.beans.factory.annotation.AnnotatedBeanDefinition d) {
                return d.getMetadata().isInterface();
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(MongoRepository.class));
        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (BeanDefinition bd : scanner.findCandidateComponents("com.curo.curo_backend.repository")) {
            Class<?> repo = Class.forName(bd.getBeanClassName());
            if (repo.getName().contains(".legacy.")) {
                continue;
            }
            Class<?> domain = AbstractRepositoryMetadata.getMetadata(repo).getDomainType();
            for (Method m : repo.getDeclaredMethods()) {
                String n = m.getName();
                if (!(n.startsWith("find") || n.startsWith("count") || n.startsWith("delete") || n.startsWith("exists"))) {
                    continue;
                }
                PartTree tree = new PartTree(n, domain);
                checked++;
                for (PartTree.OrPart or : tree) {
                    Set<String> seen = new HashSet<>();
                    for (Part p : or) {
                        if (!seen.add(p.getProperty().toDotPath())) {
                            problems.add(repo.getSimpleName() + "." + n + " repeats " + p.getProperty().toDotPath());
                        }
                    }
                }
            }
        }
        assertTrue(checked > 10, "expected to inspect the derived methods, saw " + checked);
        assertTrue(problems.isEmpty(), problems.toString());
    }
}
