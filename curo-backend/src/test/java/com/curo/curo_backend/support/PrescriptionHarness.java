package com.curo.curo_backend.support;

import com.curo.curo_backend.model.AccessGrant;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.AuditLog;
import com.curo.curo_backend.model.ChangeType;
import com.curo.curo_backend.model.DoseLog;
import com.curo.curo_backend.model.DoseStatus;
import com.curo.curo_backend.model.Frequency;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.GrantStatus;
import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.model.PrescriptionStatus;
import com.curo.curo_backend.model.PrescriptionVersion;
import com.curo.curo_backend.repository.AccessGrantRepository;
import com.curo.curo_backend.repository.AuditLogRepository;
import com.curo.curo_backend.repository.DoseLogRepository;
import com.curo.curo_backend.repository.DrugMasterRepository;
import com.curo.curo_backend.repository.PrescriptionRepository;
import com.curo.curo_backend.repository.UserRepository;
import com.curo.curo_backend.service.AccessGuard;
import com.curo.curo_backend.service.AuditService;
import com.curo.curo_backend.service.CurrentUserService;
import com.curo.curo_backend.service.DoseScheduleService;
import com.curo.curo_backend.service.DoseService;
import com.curo.curo_backend.service.DrugCatalog;
import com.curo.curo_backend.service.InteractionChecker;
import com.curo.curo_backend.service.MedicationService;
import com.curo.curo_backend.service.PrescriptionService;
import com.curo.curo_backend.service.TableInteractionChecker;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.curo.curo_backend.support.Fixtures.grant;
import static com.curo.curo_backend.support.Fixtures.signIn;
import static com.curo.curo_backend.support.Fixtures.user;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Real PrescriptionService, MedicationService, AccessGuard and interaction checker over in-memory stand-ins for the
 * database, so tests exercise the genuine access rules and audit entries.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class PrescriptionHarness {

    public static final Instant T0 = Instant.parse("2026-10-23T10:00:00Z");

    public final MutableClock clock = new MutableClock(T0);

    public final AppUser patient = user("p1", "PATIENT", "Test Patient", "CID-7K3MQ9");
    public final AppUser doctor = user("d1", "DOCTOR", "Dr Test", "CID-4HN8XD");
    public final AppUser otherDoctor = user("d2", "DOCTOR", "Dr Other", "CID-2DD2DD");
    public final AppUser pharmacy = user("ph1", "PHARMACY", "Test Pharmacy", "CID-9PW2TC");
    public final AppUser caregiver = user("c1", "CAREGIVER", "Test Caregiver", "CID-3CC3CC");

    public final Map<String, AppUser> users = new HashMap<>();
    public final List<AccessGrant> grants = new ArrayList<>();
    public final List<AuditLog> audit = new ArrayList<>();
    public final Map<String, Prescription> prescriptions = new LinkedHashMap<>();

    public final PrescriptionRepository prescriptionRepository = mock(PrescriptionRepository.class);
    public final AccessGuard guard = new AccessGuard();
    public final PrescriptionService prescriptionService = new PrescriptionService();
    public final MedicationService medicationService = new MedicationService();
    public final TableInteractionChecker checker = new TableInteractionChecker();
    public final List<DoseLog> doseLogs = new ArrayList<>();
    public final DoseLogRepository doseLogRepository = mock(DoseLogRepository.class);
    public final DoseScheduleService doseScheduleService = new DoseScheduleService();
    public final DoseService doseService = new DoseService();
    private int nextDose = 1;

    private int nextPrescription = 1;
    private int nextGrant = 1;

    public PrescriptionHarness() {
        for (AppUser u : List.of(patient, doctor, otherDoctor, pharmacy, caregiver)) {
            users.put(u.getId(), u);
        }

        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(users.get(inv.<String>getArgument(0))));
        when(userRepo.findByCareId(anyString())).thenAnswer(inv -> users.values().stream()
                .filter(u -> inv.getArgument(0).equals(u.getCareId())).findFirst());

        AccessGrantRepository grantRepo = mock(AccessGrantRepository.class);
        when(grantRepo.findByPatientIdAndGranteeIdAndStatus(anyString(), anyString(), any(GrantStatus.class))).thenAnswer(inv ->
                grants.stream().filter(g -> g.getPatientId().equals(inv.getArgument(0))
                        && g.getGranteeId().equals(inv.getArgument(1)) && g.getStatus() == inv.getArgument(2)).toList());
        when(grantRepo.save(any(AccessGrant.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditLogRepository auditRepo = mock(AuditLogRepository.class);
        when(auditRepo.save(any(AuditLog.class))).thenAnswer(inv -> {
            audit.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        AuditService auditService = new AuditService();
        ReflectionTestUtils.setField(auditService, "auditLogRepository", auditRepo);
        ReflectionTestUtils.setField(auditService, "mongoTemplate", mock(MongoTemplate.class));
        ReflectionTestUtils.setField(auditService, "clock", clock);

        CurrentUserService currentUser = new CurrentUserService();
        ReflectionTestUtils.setField(currentUser, "userRepository", userRepo);

        ReflectionTestUtils.setField(guard, "currentUser", currentUser);
        ReflectionTestUtils.setField(guard, "userRepository", userRepo);
        ReflectionTestUtils.setField(guard, "grantRepository", grantRepo);
        ReflectionTestUtils.setField(guard, "auditService", auditService);
        ReflectionTestUtils.setField(guard, "clock", clock);

        when(prescriptionRepository.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(prescriptions.get(inv.<String>getArgument(0))));
        when(prescriptionRepository.save(any(Prescription.class))).thenAnswer(inv -> store(inv.getArgument(0)));
        when(prescriptionRepository.saveAll(any(Iterable.class))).thenAnswer(inv -> {
            List<Prescription> saved = new ArrayList<>();
            for (Object p : (Iterable<?>) inv.getArgument(0)) {
                saved.add(store((Prescription) p));
            }
            return saved;
        });
        when(prescriptionRepository.findByCareIdAndStatus(anyString(), any(PrescriptionStatus.class))).thenAnswer(inv ->
                prescriptions.values().stream().filter(p -> p.getCareId().equals(inv.getArgument(0))
                        && p.getStatus() == inv.getArgument(1)).toList());

        DrugMasterRepository drugRepo = mock(DrugMasterRepository.class);
        when(drugRepo.findByNormalizedName(anyString())).thenReturn(Optional.empty());
        when(drugRepo.findFirstByAliasesContaining(anyString())).thenReturn(Optional.empty());
        DrugCatalog catalog = new DrugCatalog();
        ReflectionTestUtils.setField(catalog, "drugMasterRepository", drugRepo);
        ReflectionTestUtils.setField(checker, "drugCatalog", catalog);

        ReflectionTestUtils.setField(prescriptionService, "timeZone", "Asia/Kolkata");
        ReflectionTestUtils.setField(prescriptionService, "currentUser", currentUser);
        ReflectionTestUtils.setField(prescriptionService, "accessGuard", guard);
        ReflectionTestUtils.setField(prescriptionService, "prescriptionRepository", prescriptionRepository);
        ReflectionTestUtils.setField(prescriptionService, "interactionChecker", checker);
        ReflectionTestUtils.setField(prescriptionService, "drugCatalog", catalog);
        ReflectionTestUtils.setField(prescriptionService, "clock", clock);

        ReflectionTestUtils.setField(medicationService, "timeZone", "Asia/Kolkata");
        ReflectionTestUtils.setField(medicationService, "currentUser", currentUser);
        ReflectionTestUtils.setField(medicationService, "accessGuard", guard);
        ReflectionTestUtils.setField(medicationService, "prescriptionRepository", prescriptionRepository);
        ReflectionTestUtils.setField(medicationService, "clock", clock);

        wireDoses(currentUser);

        signIn(doctor);
    }

    private void wireDoses(CurrentUserService currentUser) {
        when(doseLogRepository.saveAll(any(Iterable.class))).thenAnswer(inv -> {
            List<DoseLog> saved = new ArrayList<>();
            for (Object o : (Iterable<?>) inv.getArgument(0)) {
                DoseLog d = (DoseLog) o;
                if (d.getId() == null) {
                    d.setId("dose" + nextDose++);
                }
                doseLogs.add(d);
                saved.add(d);
            }
            return saved;
        });
        when(doseLogRepository.findById(anyString())).thenAnswer(inv -> doseLogs.stream().filter(d -> d.getId().equals(inv.getArgument(0))).findFirst());
        when(doseLogRepository.findByPrescriptionIdAndScheduledAtAfter(anyString(), any(Instant.class))).thenAnswer(inv ->
                doseLogs.stream().filter(d -> d.getPrescriptionId().equals(inv.getArgument(0)) && d.getScheduledAt().isAfter(inv.getArgument(1))).toList());
        when(doseLogRepository.deleteByPrescriptionIdAndStatusAndScheduledAtAfter(anyString(), any(DoseStatus.class), any(Instant.class))).thenAnswer(inv -> {
            List<DoseLog> gone = doseLogs.stream().filter(d -> d.getPrescriptionId().equals(inv.getArgument(0))
                    && d.getStatus() == inv.getArgument(1) && d.getScheduledAt().isAfter(inv.getArgument(2))).toList();
            doseLogs.removeAll(gone);
            return (long) gone.size();
        });
        when(doseLogRepository.findByPatientIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAt(anyString(), any(Instant.class), any(Instant.class)))
                .thenAnswer(inv -> doseLogs.stream().filter(d -> d.getPatientId().equals(inv.getArgument(0))
                        && !d.getScheduledAt().isBefore(inv.getArgument(1)) && d.getScheduledAt().isBefore(inv.getArgument(2)))
                        .sorted(java.util.Comparator.comparing(DoseLog::getScheduledAt)).toList());
        when(doseLogRepository.findByStatusAndScheduledAtLessThanEqual(any(DoseStatus.class), any(Instant.class))).thenAnswer(inv ->
                doseLogs.stream().filter(d -> d.getStatus() == inv.getArgument(0) && !d.getScheduledAt().isAfter(inv.getArgument(1))).toList());
        when(doseLogRepository.findByStatusAndScheduledAtGreaterThanAndScheduledAtLessThanEqualAndReminderSentAtIsNull(any(DoseStatus.class), any(Instant.class), any(Instant.class)))
                .thenAnswer(inv -> doseLogs.stream().filter(d -> d.getStatus() == inv.getArgument(0) && d.getScheduledAt().isAfter(inv.getArgument(1))
                        && !d.getScheduledAt().isAfter(inv.getArgument(2)) && d.getReminderSentAt() == null).toList());
        when(doseLogRepository.findByPrescriptionIdAndScheduledAtLessThanEqual(anyString(), any(Instant.class), any(org.springframework.data.domain.Pageable.class)))
                .thenAnswer(inv -> doseLogs.stream().filter(d -> d.getPrescriptionId().equals(inv.getArgument(0)) && !d.getScheduledAt().isAfter(inv.getArgument(1)))
                        .sorted(java.util.Comparator.comparing(DoseLog::getScheduledAt).reversed())
                        .limit(((org.springframework.data.domain.Pageable) inv.getArgument(2)).getPageSize()).toList());
        when(prescriptionRepository.findAllById(any(Iterable.class))).thenAnswer(inv -> {
            List<Prescription> found = new ArrayList<>();
            for (Object id : (Iterable<?>) inv.getArgument(0)) {
                if (prescriptions.containsKey(id)) {
                    found.add(prescriptions.get(id));
                }
            }
            return found;
        });

        ReflectionTestUtils.setField(doseScheduleService, "timeZone", "Asia/Kolkata");
        ReflectionTestUtils.setField(doseScheduleService, "horizonDays", 30);
        ReflectionTestUtils.setField(doseScheduleService, "graceMinutes", 30);
        ReflectionTestUtils.setField(doseScheduleService, "doseLogRepository", doseLogRepository);
        ReflectionTestUtils.setField(doseScheduleService, "doseUpdates", new FakeDoseUpdates(doseLogs));
        ReflectionTestUtils.setField(doseScheduleService, "clock", clock);

        ReflectionTestUtils.setField(doseService, "timeZone", "Asia/Kolkata");
        ReflectionTestUtils.setField(doseService, "currentUser", currentUser);
        ReflectionTestUtils.setField(doseService, "doseLogRepository", doseLogRepository);
        ReflectionTestUtils.setField(doseService, "doseUpdates", new FakeDoseUpdates(doseLogs));
        ReflectionTestUtils.setField(doseService, "doseScheduleService", doseScheduleService);
        ReflectionTestUtils.setField(doseService, "accessGuard", guard);
        ReflectionTestUtils.setField(doseService, "prescriptionRepository", prescriptionRepository);
        ReflectionTestUtils.setField(doseService, "mongoTemplate", mock(MongoTemplate.class));
        ReflectionTestUtils.setField(doseService, "clock", clock);
        ReflectionTestUtils.setField(prescriptionService, "doseScheduleService", doseScheduleService);
    }

    private Prescription store(Prescription p) {
        if (p.getId() == null) {
            p.setId("rx" + nextPrescription++);
        }
        prescriptions.put(p.getId(), p);
        return p;
    }

    public void useChecker(InteractionChecker other) {
        ReflectionTestUtils.setField(prescriptionService, "interactionChecker", other);
    }

    /** An ACTIVE grant for the patient to this user, valid for a day. */
    public AccessGrant grantTo(AppUser grantee, GrantScope... scope) {
        return grantTo(grantee, GrantStatus.ACTIVE, T0.plus(Duration.ofDays(1)), scope);
    }

    public AccessGrant grantTo(AppUser grantee, GrantStatus status, Instant expiresAt, GrantScope... scope) {
        AccessGrant g = grant("g" + nextGrant++, patient, grantee, status, expiresAt, scope);
        grants.add(g);
        return g;
    }

    /** A prescription already on the patient's record, as if issued earlier. */
    public Prescription existing(String drug, Instant issuedAt, Integer durationDays, PrescriptionStatus status) {
        Prescription p = new Prescription();
        p.setCareId(patient.getCareId());
        p.setDoctorId(doctor.getId());
        p.setDoctorName(doctor.getName());
        p.setDrug(drug);
        p.setDose("5 mg");
        p.setDurationDays(durationDays);
        p.setFrequency(Frequency.ONCE_DAILY);
        p.setSchedule(Frequency.ONCE_DAILY.defaultTimes());
        p.setStatus(status);
        p.setIssuedAt(issuedAt);
        p.setVersions(new ArrayList<>());
        PrescriptionVersion v = new PrescriptionVersion();
        v.setVersion(1);
        v.setChangeType(ChangeType.ISSUED);
        v.setDose(p.getDose());
        v.setFrequency(p.getFrequency());
        v.setSchedule(p.getSchedule());
        v.setChangedByUserId(doctor.getId());
        v.setChangedByName(doctor.getName());
        v.setChangedByRole("DOCTOR");
        v.setChangedAt(issuedAt);
        p.getVersions().add(v);
        p.setCurrentVersion(1);
        return store(p);
    }

    public Prescription existing(String drug) {
        return existing(drug, T0.minus(Duration.ofDays(1)), null, PrescriptionStatus.ACTIVE);
    }
}
