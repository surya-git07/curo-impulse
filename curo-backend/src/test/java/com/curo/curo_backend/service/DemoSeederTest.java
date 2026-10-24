package com.curo.curo_backend.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.curo.curo_backend.dto.InteractionResult;
import com.curo.curo_backend.model.AccessGrant;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.DrugMaster;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.GrantStatus;
import com.curo.curo_backend.model.LicenceRegistryEntry;
import com.curo.curo_backend.model.Pharmacy;
import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.model.PrescriptionStatus;
import com.curo.curo_backend.model.Severity;
import com.curo.curo_backend.model.VerificationStatus;
import com.curo.curo_backend.repository.AccessGrantRepository;
import com.curo.curo_backend.repository.DrugMasterRepository;
import com.curo.curo_backend.repository.PharmacyRepository;
import com.curo.curo_backend.repository.PrescriptionRepository;
import com.curo.curo_backend.repository.UserRepository;
import com.curo.curo_backend.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DemoSeederTest {

    private static final Instant T0 = Instant.parse("2026-10-23T10:00:00Z");
    private static final String PASSWORD = "DemoPass123!";

    private final MutableClock clock = new MutableClock(T0);
    private final List<AppUser> users = new ArrayList<>();
    private final List<AccessGrant> grants = new ArrayList<>();
    private final List<Prescription> prescriptions = new ArrayList<>();
    private final List<Pharmacy> pharmacies = new ArrayList<>();
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UserRepository userRepo;
    private DemoSeeder seeder;

    @BeforeEach
    void setUp() {
        userRepo = mock(UserRepository.class);
        when(userRepo.findByCareId(anyString())).thenAnswer(inv -> users.stream().filter(u -> inv.getArgument(0).equals(u.getCareId())).findFirst());
        when(userRepo.existsByPhone(anyString())).thenAnswer(inv -> users.stream().anyMatch(u -> inv.getArgument(0).equals(u.getPhone())));
        when(userRepo.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            if (u.getId() == null) {
                u.setId("u" + (users.size() + 1));
                users.add(u);
            }
            return u;
        });

        AccessGrantRepository grantRepo = mock(AccessGrantRepository.class);
        when(grantRepo.findByPatientIdAndGranteeIdAndStatus(anyString(), anyString(), any(GrantStatus.class))).thenAnswer(inv ->
                grants.stream().filter(g -> g.getPatientId().equals(inv.getArgument(0)) && g.getGranteeId().equals(inv.getArgument(1))
                        && g.getStatus() == inv.getArgument(2)).toList());
        when(grantRepo.save(any(AccessGrant.class))).thenAnswer(inv -> {
            AccessGrant g = inv.getArgument(0);
            g.setId("g" + (grants.size() + 1));
            grants.add(g);
            return g;
        });

        PrescriptionRepository rxRepo = mock(PrescriptionRepository.class);
        when(rxRepo.findByCareIdAndStatus(anyString(), any(PrescriptionStatus.class))).thenAnswer(inv -> prescriptions.stream()
                .filter(p -> p.getCareId().equals(inv.getArgument(0)) && p.getStatus() == inv.getArgument(1)).toList());
        when(rxRepo.save(any(Prescription.class))).thenAnswer(inv -> {
            Prescription p = inv.getArgument(0);
            p.setId("rx" + (prescriptions.size() + 1));
            prescriptions.add(p);
            return p;
        });

        PharmacyRepository pharmacyRepo = mock(PharmacyRepository.class);
        when(pharmacyRepo.findBySeedKey(anyString())).thenAnswer(inv -> pharmacies.stream().filter(p -> inv.getArgument(0).equals(p.getSeedKey())).findFirst());
        when(pharmacyRepo.save(any(Pharmacy.class))).thenAnswer(inv -> {
            Pharmacy p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId("ph" + (pharmacies.size() + 1));
                pharmacies.add(p);
            }
            return p;
        });

        seeder = new DemoSeeder();
        ReflectionTestUtils.setField(seeder, "demoPassword", PASSWORD);
        ReflectionTestUtils.setField(seeder, "timeZone", "Asia/Kolkata");
        ReflectionTestUtils.setField(seeder, "userRepository", userRepo);
        ReflectionTestUtils.setField(seeder, "grantRepository", grantRepo);
        ReflectionTestUtils.setField(seeder, "prescriptionRepository", rxRepo);
        ReflectionTestUtils.setField(seeder, "pharmacyRepository", pharmacyRepo);
        ReflectionTestUtils.setField(seeder, "doseScheduleService", mock(DoseScheduleService.class));
        ReflectionTestUtils.setField(seeder, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(seeder, "clock", clock);
    }

    private AppUser byCareId(String careId) {
        return users.stream().filter(u -> careId.equals(u.getCareId())).findFirst().orElseThrow();
    }

    private List<AppUser> withRole(String role) {
        return users.stream().filter(u -> role.equals(u.getRole())).toList();
    }

    // ---- what is created

    @Test
    void createsTheWholeDemoWorld() {
        seeder.seed();

        assertThat(withRole("PATIENT")).hasSize(5);
        assertThat(withRole("CAREGIVER")).hasSize(1);
        assertThat(withRole("DOCTOR")).hasSize(2);
        assertThat(withRole("PHARMACY")).hasSize(1);
        assertThat(pharmacies).hasSize(1);
    }

    @Test
    void exactlyOnePatientHasAPenicillinAllergy() {
        seeder.seed();

        List<AppUser> penicillin = withRole("PATIENT").stream()
                .filter(p -> p.getAllergies() != null && p.getAllergies().contains("Penicillin")).toList();
        assertThat(penicillin).hasSize(1);
        assertThat(penicillin.get(0).getAllergies()).containsExactly("Penicillin");
    }

    @Test
    void demoPatientOneHasAFakeClinicalProfileAndNobodyElseDoes() {
        seeder.seed();

        AppUser one = byCareId(DemoSeeder.P1);
        assertThat(one.getBloodGroup()).isEqualTo("B+");
        assertThat(one.getAllergies()).isNotEmpty();
        assertThat(one.getChronicConditions()).isNotEmpty();
        assertThat(one.getGender()).isNotNull();
        assertThat(one.getDateOfBirth()).isNotNull();
        // the other patients are unchanged
        for (String careId : List.of(DemoSeeder.P2, DemoSeeder.P3, DemoSeeder.P4, DemoSeeder.P5)) {
            assertThat(byCareId(careId).getBloodGroup()).as(careId).isNull();
            assertThat(byCareId(careId).getChronicConditions()).as(careId).isNull();
        }
    }

    @Test
    void everyAccountIsFakeHasConsentAndAWorkingDemoPassword() {
        seeder.seed();

        for (AppUser u : users) {
            assertThat(CareIdGenerator.isCareId(u.getCareId())).as(u.getCareId()).isTrue();
            assertThat(u.getName()).isNotBlank();
            assertThat(u.getPhone()).matches("^9000000[0-9]{3}$");
            assertThat(u.getConsent().isAccepted()).isTrue();
            assertThat(u.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
            assertThat(encoder.matches(PASSWORD, u.getPasswordHash())).as(u.getCareId()).isTrue();
        }
        assertThat(users.stream().map(AppUser::getCareId).collect(Collectors.toSet())).hasSameSizeAs(users);
        assertThat(users.stream().map(AppUser::getPhone).collect(Collectors.toSet())).hasSameSizeAs(users);
    }

    @Test
    void thePatientNamesAreObviouslyFake() {
        seeder.seed();

        assertThat(withRole("PATIENT")).allSatisfy(p -> assertThat(p.getName()).startsWith("Demo Patient"));
    }

    @Test
    void theDoctorsAreVerifiedAndUseLicencesFromTheSeededRegistry() {
        seeder.seed();

        List<LicenceRegistryEntry> registry = LicenceRegistrySeed.entries();
        assertThat(withRole("DOCTOR")).allSatisfy(d -> {
            assertThat(d.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
            assertThat(registry).anySatisfy(r -> {
                assertThat(r.getType()).isEqualTo(LicenceRegistryEntry.DOCTOR);
                assertThat(r.getLicenceNumber()).isEqualTo(d.getLicenceNumber());
                assertThat(r.getName()).isEqualTo(d.getName());
                assertThat(r.getStateCouncil()).isEqualTo(d.getStateCouncil());
            });
        });
    }

    @Test
    void thePharmacyIsVerifiedAndLinkedToItsAccount() {
        seeder.seed();

        Pharmacy pharmacy = pharmacies.get(0);
        AppUser pharmacist = withRole("PHARMACY").get(0);
        assertThat(pharmacy.isVerified()).isTrue();
        assertThat(pharmacy.getAddress()).contains("Bengaluru");
        assertThat(pharmacy.getOwnerUserId()).isEqualTo(pharmacist.getId());
        assertThat(pharmacist.getFacilityId()).isEqualTo(pharmacy.getId());
        assertThat(pharmacist.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);

        // licence and GSTIN come from the invented registry and the GSTIN passes the real check
        assertThat(LicenceRegistrySeed.entries()).anySatisfy(r -> {
            assertThat(r.getLicenceNumber()).isEqualTo(pharmacist.getDrugLicenceNumber());
            assertThat(r.getGstin()).isEqualTo(pharmacist.getGstin());
            assertThat(r.getName()).isEqualTo(pharmacist.getName());
        });
        assertThat(com.curo.curo_backend.util.GstinValidator.validate(pharmacist.getGstin(), "KA"))
                .isEqualTo(com.curo.curo_backend.util.GstinValidator.Result.VALID);

    }

    @Test
    void theDemoPharmacyIsMarkedAsADemoOneAndHasNoStockOrCoordinates() {
        seeder.seed();

        assertThat(pharmacies.get(0).getSeedKey()).isEqualTo(DemoSeeder.PHARMACY_SEED_KEY);
        // the pharmacy record has no coordinate or stock fields at all any more
        assertThat(Pharmacy.class.getDeclaredFields()).extracting(java.lang.reflect.Field::getName)
                .noneMatch(n -> n.equals("lat") || n.equals("lng") || n.toLowerCase().contains("stock"));
    }

    // ---- access

    @Test
    void anActiveGrantLetsDemoDoctorOneSeeDemoPatientOne() {
        seeder.seed();

        AppUser doctor = byCareId(DemoSeeder.DOCTOR_1);
        AppUser patient = byCareId(DemoSeeder.P1);
        assertThat(grants).anySatisfy(g -> {
            assertThat(g.getPatientId()).isEqualTo(patient.getId());
            assertThat(g.getGranteeId()).isEqualTo(doctor.getId());
            assertThat(g.getRole()).isEqualTo("DOCTOR");
            assertThat(g.isUsableAt(T0)).isTrue();
            assertThat(g.getScope()).contains(GrantScope.PRESCRIPTIONS, GrantScope.MEDICATIONS, GrantScope.PROFILE);
            assertThat(g.getExpiresAt()).isEqualTo(T0.plus(Duration.ofDays(30)));
        });
    }

    @Test
    void theOtherDoctorAndTheOtherPatientsHaveNoAccessSoTheGateCanBeShown() {
        seeder.seed();

        AppUser doctor2 = byCareId(DemoSeeder.DOCTOR_2);
        assertThat(grants).noneMatch(g -> g.getGranteeId().equals(doctor2.getId()));
        List<String> patientsWithAGrant = grants.stream().filter(g -> "DOCTOR".equals(g.getRole())).map(AccessGrant::getCareId).toList();
        assertThat(patientsWithAGrant).containsExactlyInAnyOrder(DemoSeeder.P1, DemoSeeder.P2);
    }

    @Test
    void theCaregiverIsLinkedToDemoPatientOneWithAStandingGrant() {
        seeder.seed();

        AppUser caregiver = byCareId(DemoSeeder.CAREGIVER);
        AppUser patient = byCareId(DemoSeeder.P1);
        assertThat(grants).filteredOn(g -> g.getGranteeId().equals(caregiver.getId())).singleElement().satisfies(g -> {
            assertThat(g.getPatientId()).isEqualTo(patient.getId());
            assertThat(g.getRole()).isEqualTo("CAREGIVER");
            assertThat(g.getExpiresAt()).as("no end date").isNull();
            assertThat(g.isUsableAt(T0.plus(Duration.ofDays(1000)))).isTrue();
            assertThat(g.getScope()).doesNotContain(GrantScope.PRESCRIPTIONS);
        });
    }

    // ---- dangerous pairs

    @Test
    void demoPatientOneAlreadyTakesWarfarinSoAspirinShowsTheWarning() {
        seeder.seed();

        assertThat(prescriptions).singleElement().satisfies(p -> {
            assertThat(p.getCareId()).isEqualTo(DemoSeeder.P1);
            assertThat(p.getDrug()).isEqualTo("Warfarin");
            assertThat(p.getStatus()).isEqualTo(PrescriptionStatus.ACTIVE);
            assertThat(p.getVersions()).hasSize(1);
            assertThat(p.getDoctorId()).isEqualTo(byCareId(DemoSeeder.DOCTOR_1).getId());
        });

        InteractionResult r = checker().check("Aspirin", List.of("Warfarin"), List.of());
        assertThat(r.getSeverity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void theDemoDrugListHasThirtyToFiftyDrugsAndTheNamedDangerousPairs() {
        assertThat(DrugSeed.entries().size()).isBetween(30, 50);
        TableInteractionChecker checker = checker();

        // warfarin + aspirin, and two more well-known dangerous pairs
        assertThat(checker.check("Aspirin", List.of("Warfarin"), List.of()).getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(checker.check("Sertraline", List.of("Tramadol"), List.of()).getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(checker.check("Sildenafil", List.of("Isosorbide Mononitrate"), List.of()).getSeverity()).isEqualTo(Severity.HIGH);
    }

    @Test
    void thePenicillinAllergicPatientGetsAnAllergyWarningForAmoxicillin() {
        seeder.seed();
        AppUser allergic = byCareId(DemoSeeder.P2);

        InteractionResult r = checker().check("Amoxicillin", List.of(), allergic.getAllergies());

        assertThat(r.getSeverity()).isEqualTo(Severity.HIGH);
    }

    private TableInteractionChecker checker() {
        DrugMasterRepository drugRepo = mock(DrugMasterRepository.class);
        when(drugRepo.findByNormalizedName(anyString())).thenReturn(Optional.empty());
        when(drugRepo.findFirstByAliasesContaining(anyString())).thenReturn(Optional.empty());
        DrugCatalog catalog = new DrugCatalog();
        ReflectionTestUtils.setField(catalog, "drugMasterRepository", drugRepo);
        TableInteractionChecker c = new TableInteractionChecker();
        ReflectionTestUtils.setField(c, "drugCatalog", catalog);
        return c;
    }

    // ---- idempotent

    @Test
    void runningItAgainCreatesNothingNew() {
        seeder.seed();
        int u = users.size();
        int g = grants.size();
        int rx = prescriptions.size();
        int ph = pharmacies.size();
        List<String> hashes = users.stream().map(AppUser::getPasswordHash).toList();

        seeder.seed();
        seeder.seed();

        assertThat(users).hasSize(u);
        assertThat(grants).hasSize(g);
        assertThat(prescriptions).hasSize(rx);
        assertThat(pharmacies).hasSize(ph);
        assertThat(users.stream().map(AppUser::getPasswordHash).toList()).as("passwords are not re-hashed or changed").isEqualTo(hashes);
    }

    @Test
    void aSecondRunReturnsTheSameLogins() {
        List<String> first = seeder.seed();

        assertThat(seeder.seed()).isEqualTo(first);
    }

    @Test
    void editedDataIsNeverOverwritten() {
        seeder.seed();
        byCareId(DemoSeeder.P3).setName("Renamed By Hand");

        seeder.seed();

        assertThat(byCareId(DemoSeeder.P3).getName()).isEqualTo("Renamed By Hand");
    }

    @Test
    void anExpiredDemoGrantIsRenewedButAValidOneIsNotDuplicated() {
        seeder.seed();
        int clinicalGrants = (int) grants.stream().filter(g -> "DOCTOR".equals(g.getRole())).count();

        clock.advance(Duration.ofDays(10));
        seeder.seed();
        assertThat(grants.stream().filter(g -> "DOCTOR".equals(g.getRole())).count()).isEqualTo(clinicalGrants);

        clock.advance(Duration.ofDays(25)); // 35 days: the 30-day grants have lapsed
        seeder.seed();
        AppUser doctor = byCareId(DemoSeeder.DOCTOR_1);
        AppUser patient = byCareId(DemoSeeder.P1);
        assertThat(grants.stream().filter(g -> g.getGranteeId().equals(doctor.getId()) && g.getPatientId().equals(patient.getId())
                && g.isUsableAt(clock.instant()))).hasSize(1);
    }

    // ---- logins in the log

    private ListAppender<ILoggingEvent> captureLogs() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(DemoSeeder.class)).addAppender(appender);
        return appender;
    }

    @Test
    void theLoginsAreLoggedWithCareIdsAndThePasswordButNeverAHash() {
        ListAppender<ILoggingEvent> logs = captureLogs();

        seeder.run(null);

        String all = logs.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));
        for (String careId : List.of(DemoSeeder.P1, DemoSeeder.P2, DemoSeeder.P3, DemoSeeder.P4, DemoSeeder.P5, DemoSeeder.CAREGIVER,
                DemoSeeder.DOCTOR_1, DemoSeeder.DOCTOR_2, DemoSeeder.PHARMACIST)) {
            assertThat(all).contains(careId);
        }
        assertThat(all).contains(PASSWORD).contains("FAKE").contains("Never enable");
        assertThat(all).doesNotContain("$2a$").doesNotContain("$2b$");
        assertThat(logs.list).filteredOn(e -> e.getLevel() == Level.WARN).isNotEmpty();
    }

    @Test
    void aNonDefaultPasswordIsUsedAndShown() {
        ReflectionTestUtils.setField(seeder, "demoPassword", "Another-Demo-1");
        ListAppender<ILoggingEvent> logs = captureLogs();

        seeder.run(null);

        assertThat(logs.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining())).contains("Another-Demo-1");
        assertThat(encoder.matches("Another-Demo-1", users.get(0).getPasswordHash())).isTrue();
    }

    // ---- failure

    @Test
    void anUnreachableDatabaseIsOnlyAWarningWithNoConnectionDetails() {
        when(userRepo.findByCareId(anyString()))
                .thenThrow(new DataAccessResourceFailureException("Timed out connecting to mongodb://user:hunter2@db.example.net"));
        ListAppender<ILoggingEvent> logs = captureLogs();

        assertThatCode(() -> seeder.run(null)).doesNotThrowAnyException();

        String all = logs.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining());
        assertThat(all).contains("DataAccessResourceFailureException").doesNotContain("hunter2", "mongodb://", "db.example.net");
        assertThat(logs.list).allSatisfy(e -> assertThat(e.getThrowableProxy()).isNull());
        assertThat(all).as("no logins are printed when nothing was created").doesNotContain("demo login");
    }

    @Test
    void aPhoneNumberAlreadyUsedByARealAccountStopsTheSeedWithAClearMessage() {
        AppUser real = new AppUser();
        real.setId("real1");
        real.setCareId("CID-REAL22");
        real.setPhone("9000000101");
        users.add(real);
        ListAppender<ILoggingEvent> logs = captureLogs();

        assertThatThrownBy(() -> seeder.seed()).isInstanceOf(DemoSeeder.DemoSeedException.class).hasMessageContaining("9000000101");
        assertThatCode(() -> seeder.run(null)).doesNotThrowAnyException();
        assertThat(logs.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining())).contains("demo seed stopped");
        assertThat(users).as("the real account is untouched").contains(real);
    }

    // ---- switched on only by DEMO_SEED=true

    private ApplicationContextRunner runnerWithMocks() {
        return new ApplicationContextRunner()
                .withUserConfiguration(DemoSeeder.class)
                .withBean(UserRepository.class, () -> mock(UserRepository.class))
                .withBean(AccessGrantRepository.class, () -> mock(AccessGrantRepository.class))
                .withBean(PrescriptionRepository.class, () -> mock(PrescriptionRepository.class))
                .withBean(PharmacyRepository.class, () -> mock(PharmacyRepository.class))
                // registered as a ready-made singleton so Spring does not try to autowire the fields inside the mock
                .withInitializer(ctx -> ctx.getBeanFactory().registerSingleton("doseScheduleService", mock(DoseScheduleService.class)))
                .withBean(PasswordEncoder.class, () -> encoder)
                .withBean(java.time.Clock.class, () -> clock);
    }

    @Test
    void theSeederExistsOnlyWhenDemoSeedIsTrue() {
        runnerWithMocks().withPropertyValues("demo.seed=true").run(ctx -> assertThat(ctx).hasSingleBean(DemoSeeder.class));
        runnerWithMocks().withPropertyValues("demo.seed=false").run(ctx -> assertThat(ctx).doesNotHaveBean(DemoSeeder.class));
        runnerWithMocks().withPropertyValues("demo.seed=yes").run(ctx -> assertThat(ctx).doesNotHaveBean(DemoSeeder.class));
        runnerWithMocks().withPropertyValues("demo.seed=").run(ctx -> assertThat(ctx).doesNotHaveBean(DemoSeeder.class));
        runnerWithMocks().run(ctx -> assertThat(ctx).as("property absent").doesNotHaveBean(DemoSeeder.class));
    }

    @Test
    void theShippedConfigurationLeavesItOffUnlessTheEnvironmentTurnsItOn() throws Exception {
        String properties = Files.readString(Path.of("src/main/resources/application.properties"));

        assertThat(properties).contains("demo.seed=${DEMO_SEED:false}");
    }

    @Test
    void itRunsAfterTheSeedersItDependsOn() {
        int demo = DemoSeeder.class.getAnnotation(org.springframework.core.annotation.Order.class).value();
        for (Class<?> earlier : List.of(DrugSeeder.class, LicenceRegistrySeeder.class)) {
            assertThat(earlier.getAnnotation(org.springframework.core.annotation.Order.class).value()).isLessThan(demo);
        }
    }
}
