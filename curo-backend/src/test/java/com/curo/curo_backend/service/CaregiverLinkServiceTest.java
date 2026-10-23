package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.CaregiverLinkRequest;
import com.curo.curo_backend.dto.CaregiverLinkView;
import com.curo.curo_backend.dto.DiscontinueRequest;
import com.curo.curo_backend.dto.DoseView;
import com.curo.curo_backend.dto.DrugItem;
import com.curo.curo_backend.dto.IssueRequest;
import com.curo.curo_backend.dto.ModifyRequest;
import com.curo.curo_backend.model.AccessGrant;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.AuditOutcome;
import com.curo.curo_backend.model.CaregiverLink;
import com.curo.curo_backend.model.DoseLog;
import com.curo.curo_backend.model.DoseStatus;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.GrantStatus;
import com.curo.curo_backend.model.LinkStatus;
import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.repository.AccessGrantRepository;
import com.curo.curo_backend.repository.CaregiverLinkRepository;
import com.curo.curo_backend.repository.UserRepository;
import com.curo.curo_backend.support.PrescriptionHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static com.curo.curo_backend.support.Fixtures.signIn;
import static com.curo.curo_backend.support.Fixtures.signOut;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The caregiver link, and what the access it creates does and does not allow. Uses the real guard, so the limits are the real ones. */
class CaregiverLinkServiceTest {

    private static final Instant T0 = PrescriptionHarness.T0;

    private PrescriptionHarness h;
    private CaregiverLinkService service;
    private final List<CaregiverLink> links = new ArrayList<>();

    @BeforeEach
    void setUp() {
        h = new PrescriptionHarness();

        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(h.users.get(inv.<String>getArgument(0))));
        when(userRepo.findByCareId(anyString())).thenAnswer(inv -> h.users.values().stream().filter(u -> inv.getArgument(0).equals(u.getCareId())).findFirst());

        // grants are written into the same list the guard reads, so what the link creates is what the guard enforces
        AccessGrantRepository grantRepo = mock(AccessGrantRepository.class);
        when(grantRepo.save(any(AccessGrant.class))).thenAnswer(inv -> {
            AccessGrant g = inv.getArgument(0);
            if (g.getId() == null) {
                g.setId("lg" + (h.grants.size() + 1));
                h.grants.add(g);
            }
            return g;
        });
        when(grantRepo.findById(anyString())).thenAnswer(inv -> h.grants.stream().filter(g -> inv.getArgument(0).equals(g.getId())).findFirst());

        CaregiverLinkRepository linkRepo = mock(CaregiverLinkRepository.class);
        when(linkRepo.save(any(CaregiverLink.class))).thenAnswer(inv -> {
            CaregiverLink l = inv.getArgument(0);
            if (l.getId() == null) {
                l.setId("link" + (links.size() + 1));
                links.add(l);
            }
            return l;
        });
        when(linkRepo.findById(anyString())).thenAnswer(inv -> links.stream().filter(l -> inv.getArgument(0).equals(l.getId())).findFirst());
        when(linkRepo.existsByPatientIdAndCaregiverIdAndStatus(anyString(), anyString(), any(LinkStatus.class))).thenAnswer(inv ->
                links.stream().anyMatch(l -> l.getPatientId().equals(inv.getArgument(0)) && l.getCaregiverId().equals(inv.getArgument(1))
                        && l.getStatus() == inv.getArgument(2)));
        when(linkRepo.findByPatientIdOrderByCreatedAtDesc(anyString())).thenAnswer(inv ->
                links.stream().filter(l -> l.getPatientId().equals(inv.getArgument(0))).toList());
        when(linkRepo.findByCaregiverIdOrderByCreatedAtDesc(anyString())).thenAnswer(inv ->
                links.stream().filter(l -> l.getCaregiverId().equals(inv.getArgument(0))).toList());

        service = new CaregiverLinkService();
        ReflectionTestUtils.setField(service, "currentUser", ReflectionTestUtils.getField(h.guard, "currentUser"));
        ReflectionTestUtils.setField(service, "userRepository", userRepo);
        ReflectionTestUtils.setField(service, "linkRepository", linkRepo);
        ReflectionTestUtils.setField(service, "grantRepository", grantRepo);
        ReflectionTestUtils.setField(service, "auditService", ReflectionTestUtils.getField(h.guard, "auditService"));
        ReflectionTestUtils.setField(service, "clock", h.clock);
        signIn(h.patient);
    }

    @AfterEach
    void tearDown() {
        signOut();
    }

    private CaregiverLinkRequest request(String careId) {
        CaregiverLinkRequest r = new CaregiverLinkRequest();
        r.setCareId(careId);
        return r;
    }

    private CaregiverLinkView addCaregiver() {
        return service.add(request("CID-3CC3CC"));
    }

    private void assertApiError(Supplier<?> call, HttpStatus status, String code) {
        assertThatThrownBy(call::get).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    // ---- adding a caregiver by CareID

    @Test
    void aPatientAddsACaregiverByCareIdAndAStandingGrantIsCreated() {
        CaregiverLinkRequest req = request(" cid-3cc3cc ");
        req.setRelationship("  daughter ");

        CaregiverLinkView view = service.add(req);

        assertThat(view.getStatus()).isEqualTo(LinkStatus.ACTIVE);
        assertThat(view.getPatientCareId()).isEqualTo("CID-7K3MQ9");
        assertThat(view.getCaregiverCareId()).isEqualTo("CID-3CC3CC");
        assertThat(view.getCaregiverName()).isEqualTo("Test Caregiver");
        assertThat(view.getRelationship()).isEqualTo("daughter");
        assertThat(view.getCreatedAt()).isEqualTo(T0);

        AccessGrant grant = h.grants.get(0);
        assertThat(grant.getRole()).isEqualTo("CAREGIVER");
        assertThat(grant.getPatientId()).isEqualTo("p1");
        assertThat(grant.getGranteeId()).isEqualTo("c1");
        assertThat(grant.getStatus()).isEqualTo(GrantStatus.ACTIVE);
        assertThat(grant.getExpiresAt()).as("no end date").isNull();
        assertThat(links.get(0).getGrantId()).isEqualTo(grant.getId());
    }

    @Test
    void theGrantCoversTheScheduleAndNothingElse() {
        addCaregiver();

        assertThat(h.grants.get(0).getScope()).containsExactlyInAnyOrder(GrantScope.MEDICATIONS, GrantScope.DOSES);
    }

    @Test
    void anUnknownCareIdAndANonCaregiverCareIdLookTheSame() {
        for (String careId : new String[]{"CID-ZZZZZZ", "CID-4HN8XD" /* a doctor */, "CID-9PW2TC" /* a pharmacy */, "CID-7K3MQ9" /* the patient */}) {
            assertApiError(() -> service.add(request(careId)), HttpStatus.NOT_FOUND, "CAREGIVER_NOT_FOUND");
        }
        assertThat(links).isEmpty();
        assertThat(h.grants).isEmpty();
    }

    @Test
    void aDeletedCaregiverCannotBeAdded() {
        h.caregiver.setDeletedAt(T0.minusSeconds(60));

        assertApiError(this::addCaregiver, HttpStatus.NOT_FOUND, "CAREGIVER_NOT_FOUND");
    }

    @Test
    void theSameCaregiverCannotBeAddedTwiceButCanBeAddedAgainAfterRemoval() {
        CaregiverLinkView first = addCaregiver();
        assertApiError(this::addCaregiver, HttpStatus.CONFLICT, "LINK_EXISTS");
        assertThat(h.grants).hasSize(1);

        service.revoke(first.getId());
        CaregiverLinkView again = addCaregiver();

        assertThat(again.getStatus()).isEqualTo(LinkStatus.ACTIVE);
        assertThat(h.grants).hasSize(2);
    }

    @Test
    void onlyPatientsAddCaregivers() {
        for (AppUser other : List.of(h.doctor, h.pharmacy, h.caregiver)) {
            signIn(other);
            assertApiError(this::addCaregiver, HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
        }
        assertThat(links).isEmpty();
    }

    @Test
    void addingIsLoggedInThePatientsAccessLog() {
        addCaregiver();

        assertThat(h.audit).singleElement().satisfies(e -> {
            assertThat(e.getAction()).isEqualTo(AuditAction.GRANT_APPROVED);
            assertThat(e.getResource()).isEqualTo("CAREGIVER_LINK");
            assertThat(e.getOutcome()).isEqualTo(AuditOutcome.ALLOWED);
            assertThat(e.getPatientId()).isEqualTo("p1");
        });
    }

    // ---- what the caregiver can see

    private List<DoseLog> someDoses() {
        Prescription rx = h.existing("Warfarin", T0.minus(Duration.ofDays(1)), 30, com.curo.curo_backend.model.PrescriptionStatus.ACTIVE);
        List<DoseLog> doses = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            DoseLog d = new DoseLog();
            d.setId("dose" + (i + 1));
            d.setPrescriptionId(rx.getId());
            d.setPatientId("p1");
            d.setScheduledAt(T0.plus(Duration.ofHours(4L * (i + 1))));
            d.setStatus(DoseStatus.UPCOMING);
            h.doseLogs.add(d);
            doses.add(d);
        }
        return doses;
    }

    @Test
    void theCaregiverSeesTheDoseScheduleOnceLinkedAndNotBefore() {
        someDoses();
        signIn(h.caregiver);
        assertApiError(() -> h.doseService.scheduleFor("CID-7K3MQ9", null, null), HttpStatus.FORBIDDEN, "NO_ACTIVE_GRANT");

        signIn(h.patient);
        addCaregiver();
        signIn(h.caregiver);
        List<DoseView> schedule = h.doseService.scheduleFor("CID-7K3MQ9", null, null);

        assertThat(schedule).hasSize(3);
        assertThat(schedule).extracting(DoseView::getDrug).containsOnly("Warfarin");
        assertThat(schedule).extracting(DoseView::getScheduledAt).isSorted();
        assertThat(h.audit).anySatisfy(e -> {
            assertThat(e.getAction()).isEqualTo(AuditAction.READ);
            assertThat(e.getResource()).isEqualTo("DOSE_SCHEDULE");
            assertThat(e.getActorUserId()).isEqualTo("c1");
        });
    }

    @Test
    void theScheduleRangeIsValidated() {
        addCaregiver();
        signIn(h.caregiver);

        assertApiError(() -> h.doseService.scheduleFor("CID-7K3MQ9", T0, T0), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertApiError(() -> h.doseService.scheduleFor("CID-7K3MQ9", T0, T0.minusSeconds(1)), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertApiError(() -> h.doseService.scheduleFor("CID-7K3MQ9", T0, T0.plus(Duration.ofDays(32))), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertThat(h.doseService.scheduleFor("CID-7K3MQ9", T0, T0.plus(Duration.ofDays(31)))).isEmpty();
    }

    @Test
    void theCaregiverSeesTheMedicationListToo() {
        h.existing("Warfarin");
        addCaregiver();
        signIn(h.caregiver);

        assertThat(h.medicationService.forPatient("CID-7K3MQ9").getMedications()).hasSize(1);
    }

    @Test
    void theCaregiverCannotReadPrescriptionsOrTheProfile() {
        Prescription rx = h.existing("Warfarin");
        h.patient.setAllergies(List.of("Penicillin"));
        addCaregiver();
        signIn(h.caregiver);

        assertApiError(() -> h.prescriptionService.history(rx.getId()), HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
        assertApiError(() -> h.guard.require("CID-7K3MQ9", GrantScope.PRESCRIPTIONS, AuditAction.READ, "PRESCRIPTION", null),
                HttpStatus.FORBIDDEN, "NO_ACTIVE_GRANT");
        assertApiError(() -> h.guard.require("CID-7K3MQ9", GrantScope.PROFILE, AuditAction.READ, "PROFILE", null),
                HttpStatus.FORBIDDEN, "NO_ACTIVE_GRANT");
        assertThat(h.medicationService.forPatient("CID-7K3MQ9").getAllergies()).as("no PROFILE scope, so no allergies").isNull();
    }

    @Test
    void aCaregiverCannotPrescribeModifyOrDiscontinueEvenWithAGrantThatAllowsIt() {
        Prescription rx = h.existing("Warfarin");
        addCaregiver();
        // even if someone gave the caregiver a PRESCRIPTIONS grant, the role itself cannot prescribe
        h.grantTo(h.caregiver, GrantScope.PRESCRIPTIONS, GrantScope.MEDICATIONS, GrantScope.DOSES, GrantScope.PROFILE);
        signIn(h.caregiver);

        DrugItem drug = new DrugItem();
        drug.setDrug("Paracetamol");
        drug.setDose("500 mg");
        IssueRequest issue = new IssueRequest();
        issue.setCareId("CID-7K3MQ9");
        issue.setDrugs(List.of(drug));
        ModifyRequest modify = new ModifyRequest();
        modify.setDose("1 mg");
        modify.setReason("no");
        DiscontinueRequest stop = new DiscontinueRequest();
        stop.setReason("no");

        assertApiError(() -> h.prescriptionService.issue(issue), HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
        assertApiError(() -> h.prescriptionService.modify(rx.getId(), modify), HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
        assertApiError(() -> h.prescriptionService.discontinue(rx.getId(), stop), HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
        assertThat(h.prescriptions).hasSize(1);
        assertThat(rx.getVersions()).hasSize(1);
    }

    // ---- listing and removing

    @Test
    void aPatientSeesTheirCaregiversAndACaregiverSeesTheirPatients() {
        addCaregiver();

        assertThat(service.list()).singleElement().satisfies(v -> assertThat(v.getCaregiverCareId()).isEqualTo("CID-3CC3CC"));
        signIn(h.caregiver);
        assertThat(service.list()).singleElement().satisfies(v -> {
            assertThat(v.getPatientCareId()).isEqualTo("CID-7K3MQ9");
            assertThat(v.getPatientName()).isEqualTo("Test Patient");
        });
    }

    @Test
    void nobodyElseSeesTheLinksAndDoctorsHaveNoneOfTheirOwn() {
        addCaregiver();
        AppUser otherPatient = com.curo.curo_backend.support.Fixtures.user("p2", "PATIENT", "Other Patient", "CID-OTHRP2");
        h.users.put("p2", otherPatient);
        signIn(otherPatient);
        assertThat(service.list()).isEmpty();

        signIn(h.doctor);
        assertApiError(service::list, HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE");
    }

    @Test
    void thePatientCanRemoveTheCaregiverAndAccessEndsAtOnce() {
        someDoses();
        CaregiverLinkView link = addCaregiver();
        signIn(h.caregiver);
        assertThat(h.doseService.scheduleFor("CID-7K3MQ9", null, null)).isNotEmpty();

        signIn(h.patient);
        CaregiverLinkView revoked = service.revoke(link.getId());

        assertThat(revoked.getStatus()).isEqualTo(LinkStatus.REVOKED);
        assertThat(revoked.getRevokedAt()).isEqualTo(T0);
        assertThat(h.grants.get(0).getStatus()).isEqualTo(GrantStatus.REVOKED);
        signIn(h.caregiver);
        assertApiError(() -> h.doseService.scheduleFor("CID-7K3MQ9", null, null), HttpStatus.FORBIDDEN, "NO_ACTIVE_GRANT");
        assertApiError(() -> h.medicationService.forPatient("CID-7K3MQ9"), HttpStatus.FORBIDDEN, "NO_ACTIVE_GRANT");
    }

    @Test
    void theCaregiverCanStepDownToo() {
        CaregiverLinkView link = addCaregiver();
        signIn(h.caregiver);

        assertThat(service.revoke(link.getId()).getStatus()).isEqualTo(LinkStatus.REVOKED);
        assertThat(h.grants.get(0).getStatus()).isEqualTo(GrantStatus.REVOKED);
    }

    @Test
    void strangersCannotRemoveALinkAndSeeNothingOfIt() {
        CaregiverLinkView link = addCaregiver();
        signIn(h.doctor);

        assertApiError(() -> service.revoke(link.getId()), HttpStatus.NOT_FOUND, "LINK_NOT_FOUND");
        assertApiError(() -> service.revoke("nope"), HttpStatus.NOT_FOUND, "LINK_NOT_FOUND");
        assertThat(links.get(0).getStatus()).isEqualTo(LinkStatus.ACTIVE);
    }

    @Test
    void removingTwiceChangesNothingTheSecondTime() {
        CaregiverLinkView link = addCaregiver();
        service.revoke(link.getId());
        int entries = h.audit.size();
        Instant firstRevokedAt = links.get(0).getRevokedAt();
        h.clock.advance(Duration.ofMinutes(5));

        CaregiverLinkView again = service.revoke(link.getId());

        assertThat(again.getStatus()).isEqualTo(LinkStatus.REVOKED);
        assertThat(again.getRevokedAt()).isEqualTo(firstRevokedAt);
        assertThat(h.audit).hasSize(entries);
    }

    @Test
    void aRemovedCaregiverGetsNoMoreMissedDoseAlerts() {
        // the alert recipients come from active CAREGIVER grants, so revoking the link takes the caregiver off the list
        CaregiverLinkView link = addCaregiver();
        service.revoke(link.getId());

        assertThat(h.grants).noneMatch(g -> g.getStatus() == GrantStatus.ACTIVE && "CAREGIVER".equals(g.getRole()));
    }
}
