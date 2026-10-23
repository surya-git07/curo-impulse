package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.DiscontinueRequest;
import com.curo.curo_backend.dto.DrugItem;
import com.curo.curo_backend.dto.IssueRequest;
import com.curo.curo_backend.dto.ModifyRequest;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.AuditLog;
import com.curo.curo_backend.model.AuditOutcome;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.GrantStatus;
import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.support.PrescriptionHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static com.curo.curo_backend.support.Fixtures.signIn;
import static com.curo.curo_backend.support.Fixtures.signOut;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every prescription and medication endpoint goes through the AccessGrant check: no grant, an expired grant, a revoked
 * grant or a grant for the wrong scope gets 403 NO_ACTIVE_GRANT, changes nothing, and is logged as DENIED; a good grant
 * works and is logged as ALLOWED.
 */
class PrescriptionAccessTest {

    private static final java.time.Instant T0 = PrescriptionHarness.T0;

    private PrescriptionHarness h;
    private Prescription existing;

    /** One call per endpoint. Each is made by the same signed-in doctor, who sees the patient only through a grant. */
    private final Map<String, Consumer<PrescriptionHarness>> endpoints = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        h = new PrescriptionHarness();
        existing = h.existing("Warfarin");

        endpoints.put("issue", x -> {
            DrugItem d = new DrugItem();
            d.setDrug("Paracetamol");
            d.setDose("500 mg");
            IssueRequest r = new IssueRequest();
            r.setCareId("CID-7K3MQ9");
            r.setDrugs(List.of(d));
            x.prescriptionService.issue(r);
        });
        endpoints.put("modify", x -> {
            ModifyRequest r = new ModifyRequest();
            r.setDose("1 mg");
            r.setReason("test");
            x.prescriptionService.modify(existing.getId(), r);
        });
        endpoints.put("discontinue", x -> {
            DiscontinueRequest r = new DiscontinueRequest();
            r.setReason("test");
            x.prescriptionService.discontinue(existing.getId(), r);
        });
        endpoints.put("history", x -> x.prescriptionService.history(existing.getId()));
        endpoints.put("medications", x -> x.medicationService.forPatient("CID-7K3MQ9"));
    }

    @AfterEach
    void tearDown() {
        signOut();
    }

    /** What each endpoint needs: prescriptions for issue/modify/discontinue/history, medications for the profile. */
    private GrantScope neededScope(String endpoint) {
        return "medications".equals(endpoint) ? GrantScope.MEDICATIONS : GrantScope.PRESCRIPTIONS;
    }

    private GrantScope wrongScope(String endpoint) {
        return "medications".equals(endpoint) ? GrantScope.PRESCRIPTIONS : GrantScope.MEDICATIONS;
    }

    private void assertRefused(String endpoint) {
        int prescriptionsBefore = h.prescriptions.size();
        int versionsBefore = existing.getVersions().size();

        assertThatThrownBy(() -> endpoints.get(endpoint).accept(h)).as(endpoint).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(e.getCode()).isEqualTo("NO_ACTIVE_GRANT");
        });

        assertThat(h.prescriptions).as(endpoint + " created nothing").hasSize(prescriptionsBefore);
        assertThat(existing.getVersions()).as(endpoint + " changed nothing").hasSize(versionsBefore);
        assertThat(existing.getStatus().name()).isEqualTo("ACTIVE");
        assertThat(h.audit).as(endpoint + " audit").anySatisfy(entry -> {
            assertThat(entry.getOutcome()).isEqualTo(AuditOutcome.DENIED);
            assertThat(entry.getActorUserId()).isEqualTo("d1");
            assertThat(entry.getPatientId()).isEqualTo("p1");
        });
        // a lapsed grant also writes a system GRANT_EXPIRED entry; what must never appear is an allowed READ or WRITE
        assertThat(h.audit).as(endpoint + " never logged as allowed access").noneMatch(entry -> entry.getOutcome() == AuditOutcome.ALLOWED
                && (entry.getAction() == AuditAction.READ || entry.getAction() == AuditAction.WRITE));
    }

    @Test
    void noGrantRefusesEveryEndpoint() {
        for (String endpoint : endpoints.keySet()) {
            setUp();
            assertRefused(endpoint);
        }
    }

    @Test
    void anExpiredGrantRefusesEveryEndpoint() {
        for (String endpoint : endpoints.keySet()) {
            setUp();
            h.grantTo(h.doctor, GrantStatus.ACTIVE, T0.minusSeconds(1), neededScope(endpoint));
            assertRefused(endpoint);
        }
    }

    @Test
    void aGrantThatRunsOutBetweenCallsRefusesTheNextOne() {
        h.grantTo(h.doctor, GrantStatus.ACTIVE, T0.plus(Duration.ofMinutes(10)), GrantScope.PRESCRIPTIONS, GrantScope.MEDICATIONS);
        for (String endpoint : endpoints.keySet()) {
            if (!endpoint.equals("discontinue")) { // discontinue would change the state the others rely on
                endpoints.get(endpoint).accept(h);
            }
        }
        h.clock.advance(Duration.ofMinutes(10));

        for (String endpoint : endpoints.keySet()) {
            assertThatThrownBy(() -> endpoints.get(endpoint).accept(h)).as(endpoint)
                    .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("NO_ACTIVE_GRANT"));
        }
    }

    @Test
    void aRevokedGrantRefusesEveryEndpoint() {
        for (String endpoint : endpoints.keySet()) {
            setUp();
            h.grantTo(h.doctor, GrantStatus.REVOKED, T0.plus(Duration.ofHours(5)), neededScope(endpoint));
            assertRefused(endpoint);
        }
    }

    @Test
    void aPendingRequestRefusesEveryEndpoint() {
        for (String endpoint : endpoints.keySet()) {
            setUp();
            h.grantTo(h.doctor, GrantStatus.PENDING, null, neededScope(endpoint));
            assertRefused(endpoint);
        }
    }

    @Test
    void aGrantForTheWrongScopeRefusesEveryEndpoint() {
        for (String endpoint : endpoints.keySet()) {
            setUp();
            h.grantTo(h.doctor, wrongScope(endpoint), GrantScope.PROFILE);
            assertRefused(endpoint);
        }
    }

    @Test
    void anActiveGrantAllowsEveryEndpointAndEachIsLogged() {
        for (String endpoint : endpoints.keySet()) {
            setUp();
            h.grantTo(h.doctor, neededScope(endpoint));

            endpoints.get(endpoint).accept(h);

            List<AuditLog> allowed = h.audit.stream().filter(e -> e.getOutcome() == AuditOutcome.ALLOWED).toList();
            assertThat(allowed).as(endpoint + " audit").hasSize(1);
            AuditLog entry = allowed.get(0);
            assertThat(entry.getActorUserId()).isEqualTo("d1");
            assertThat(entry.getActorRole()).isEqualTo("DOCTOR");
            assertThat(entry.getPatientId()).isEqualTo("p1");
            assertThat(entry.getCareId()).isEqualTo("CID-7K3MQ9");
            assertThat(entry.getGrantId()).isEqualTo("g1");
            assertThat(entry.getAt()).isEqualTo(T0);
            assertThat(entry.getAction()).isEqualTo(
                    endpoint.equals("history") || endpoint.equals("medications") ? AuditAction.READ : AuditAction.WRITE);
        }
    }

    @Test
    void theAuditEntryNamesTheRecordTouched() {
        h.grantTo(h.doctor, GrantScope.PRESCRIPTIONS);

        endpoints.get("history").accept(h);
        endpoints.get("modify").accept(h);

        assertThat(h.audit).extracting(AuditLog::getResource).containsOnly("PRESCRIPTION");
        assertThat(h.audit).extracting(AuditLog::getResourceId).containsOnly(existing.getId());
    }

    @Test
    void issueLogsTheIdsOfEveryPrescriptionItCreates() {
        h.grantTo(h.doctor, GrantScope.PRESCRIPTIONS);
        DrugItem a = new DrugItem();
        a.setDrug("Paracetamol");
        a.setDose("500 mg");
        DrugItem b = new DrugItem();
        b.setDrug("Cetirizine");
        b.setDose("10 mg");
        IssueRequest r = new IssueRequest();
        r.setCareId("CID-7K3MQ9");
        r.setDrugs(List.of(a, b));

        var response = h.prescriptionService.issue(r);

        assertThat(h.audit).hasSize(1);
        assertThat(h.audit.get(0).getResourceId())
                .isEqualTo(response.getDrugs().get(0).getId() + "," + response.getDrugs().get(1).getId());
    }

    @Test
    void aGrantOnADifferentPatientGivesNothing() {
        for (String endpoint : endpoints.keySet()) {
            setUp();
            var other = com.curo.curo_backend.support.Fixtures.user("p2", "PATIENT", "Other Patient", "CID-OTHRP2");
            h.users.put(other.getId(), other);
            h.grants.add(com.curo.curo_backend.support.Fixtures.grant("gx", other, h.doctor, GrantStatus.ACTIVE, T0.plus(Duration.ofDays(1)),
                    GrantScope.PRESCRIPTIONS, GrantScope.MEDICATIONS));
            assertRefused(endpoint);
        }
    }

    @Test
    void anotherDoctorsGrantDoesNotHelpThisDoctor() {
        for (String endpoint : endpoints.keySet()) {
            setUp();
            h.grantTo(h.otherDoctor, neededScope(endpoint));
            assertRefused(endpoint);
        }
    }

    @Test
    void anAdminNeverGetsThrough() {
        var admin = com.curo.curo_backend.support.Fixtures.user("a1", "ADMIN", "Admin", "CID-ADMINA");
        h.users.put("a1", admin);
        h.grantTo(admin, GrantScope.PRESCRIPTIONS, GrantScope.MEDICATIONS);
        signIn(admin);

        assertThatThrownBy(() -> h.medicationService.forPatient("CID-7K3MQ9")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getCode()).isEqualTo("NO_ACTIVE_GRANT"));
    }

}
