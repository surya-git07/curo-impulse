package com.curo.curo_backend.service;

import com.curo.curo_backend.model.AccessGrant;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.ChangeType;
import com.curo.curo_backend.model.Consent;
import com.curo.curo_backend.model.Frequency;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.GrantStatus;
import com.curo.curo_backend.model.Pharmacy;
import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.model.PrescriptionStatus;
import com.curo.curo_backend.model.PrescriptionVersion;
import com.curo.curo_backend.model.VerificationStatus;
import com.curo.curo_backend.repository.AccessGrantRepository;
import com.curo.curo_backend.repository.PharmacyRepository;
import com.curo.curo_backend.repository.PrescriptionRepository;
import com.curo.curo_backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fills a development database with a small, completely fake demo world so the app can be tried end to end.
 * Off unless DEMO_SEED=true. Safe to run on every start: anything that already exists is left alone, and an expired
 * demo grant is renewed. Never enable it against real data: the accounts all share a published demo password.
 *
 * It relies on the drug and licence-registry seeders (order 10) having run first, and uses the invented
 * registry licences for its doctors and pharmacy.
 */
@Component
@ConditionalOnProperty(name = "demo.seed", havingValue = "true")
@Order(100)
public class DemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    // CareIDs are fixed so the logins never change between restarts. They follow the real CareID format.
    static final String P1 = "CID-DMPAT2";
    static final String P2 = "CID-DMPAT3";
    static final String P3 = "CID-DMPAT4";
    static final String P4 = "CID-DMPAT5";
    static final String P5 = "CID-DMPAT6";
    static final String CAREGIVER = "CID-DMCGR2";
    static final String DOCTOR_1 = "CID-DMDCT2";
    static final String DOCTOR_2 = "CID-DMDCT3";
    static final String PHARMACIST = "CID-DMPHM2";

    static final String PHARMACY_SEED_KEY = "demo-account-pharmacy";
    private static final Duration GRANT_LENGTH = Duration.ofDays(30);

    @Value("${demo.password:DemoPass123!}")
    private String demoPassword;

    @Value("${app.time-zone:Asia/Kolkata}")
    private String timeZone;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccessGrantRepository grantRepository;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private DoseScheduleService doseScheduleService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    /** Raised for a clash with existing data the seeder cannot resolve. The message is ours and safe to log. */
    static class DemoSeedException extends RuntimeException {
        DemoSeedException(String message) {
            super(message);
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<String> logins = seed();
            log.warn("DEMO DATA ENABLED (DEMO_SEED=true). All accounts below are FAKE and share one published password. Never enable this against real data.");
            logins.forEach(line -> log.info("demo login: {}", line));
        } catch (DemoSeedException e) {
            log.warn("demo seed stopped: {}", e.getMessage());
        } catch (RuntimeException e) {
            // exception type only: driver messages can contain connection details
            log.warn("demo seed stopped: MongoDB unreachable or failing ({})", e.getClass().getSimpleName());
        }
    }

    /** Creates whatever is missing and returns one line per demo login. */
    List<String> seed() {
        Instant now = clock.instant();

        AppUser p1 = patient(P1, "9000000101", "Demo Patient One", null, now);
        // CID-DMPAT2 gets a fake clinical profile for the doctor Patient screen. It is filled in once, only while the blood
        // group is missing, so an existing demo database is topped up and nothing else (or anyone else) is touched.
        if (p1.getBloodGroup() == null) {
            p1.setBloodGroup("B+");
            p1.setGender("MALE");
            p1.setDateOfBirth(LocalDate.of(1980, 5, 14));
            p1.setAllergies(List.of("Latex"));
            p1.setChronicConditions(List.of("Type 2 diabetes"));
            p1 = userRepository.save(p1);
        }
        AppUser p2 = patient(P2, "9000000102", "Demo Patient Two", List.of("Penicillin"), now);
        patient(P3, "9000000103", "Demo Patient Three", null, now);
        patient(P4, "9000000104", "Demo Patient Four", null, now);
        patient(P5, "9000000105", "Demo Patient Five", null, now);

        AppUser caregiver = account(CAREGIVER, "9000000111", "Demo Caregiver", "CAREGIVER", now, u -> { });

        // doctors use licences from the invented licence registry (LicenceRegistrySeed), marked VERIFIED as signup would
        AppUser d1 = account(DOCTOR_1, "9000000121", "Anjali Varadarajan", "DOCTOR", now, u -> {
            u.setLicenceNumber("TN-48213");
            u.setStateCouncil("Tamil Nadu Medical Council");
            verified(u);
            u.setFacilityName("Demo Hospital Bengaluru");
        });
        AppUser d2 = account(DOCTOR_2, "9000000122", "Rohan Gowdaiah", "DOCTOR", now, u -> {
            u.setLicenceNumber("KMC-77120");
            u.setStateCouncil("Karnataka Medical Council");
            verified(u);
            u.setFacilityName("Demo Hospital Bengaluru");
        });

        Pharmacy pharmacy = demoPharmacy(now);
        AppUser pharmacist = account(PHARMACIST, "9000000131", "Nandi Hills Pharma", "PHARMACY", now, u -> {
            u.setDrugLicenceNumber("KA/BLR/20B-77310");
            u.setGstin("29AAGCN7731L1ZW");
            u.setState("KA");
            verified(u);
            u.setFacilityId(pharmacy.getId());
            u.setFacilityName(pharmacy.getName());
        });
        if (pharmacy.getOwnerUserId() == null) {
            pharmacy.setOwnerUserId(pharmacist.getId());
            pharmacyRepository.save(pharmacy);
        }

        // d1 may see and prescribe for both patients; d2 has no grant, so the consent gate can be shown refusing
        List<GrantScope> clinical = List.of(GrantScope.PROFILE, GrantScope.PRESCRIPTIONS, GrantScope.MEDICATIONS, GrantScope.DOSES);
        grant(p1, d1, clinical, now.plus(GRANT_LENGTH), now);
        grant(p2, d1, clinical, now.plus(GRANT_LENGTH), now);
        // the caregiver link is a standing CAREGIVER grant with no end date (contract section 10)
        grant(p1, caregiver, List.of(GrantScope.PROFILE, GrantScope.MEDICATIONS, GrantScope.DOSES), null, now);

        // patient one already takes warfarin, so issuing aspirin to them shows the interaction warning
        warfarinFor(p1, d1, now);

        return List.of(
                login("PATIENT", P1, "Demo Patient One (takes warfarin; Dr Anjali has access; caregiver linked)"),
                login("PATIENT", P2, "Demo Patient Two (penicillin allergy; Dr Anjali has access)"),
                login("PATIENT", P3, "Demo Patient Three (no doctor access yet)"),
                login("PATIENT", P4, "Demo Patient Four"),
                login("PATIENT", P5, "Demo Patient Five"),
                login("CAREGIVER", CAREGIVER, "linked to Demo Patient One"),
                login("DOCTOR", DOCTOR_1, "verified; access to patients one and two"),
                login("DOCTOR", DOCTOR_2, "verified; no patient access"),
                login("PHARMACY", PHARMACIST, "verified; Nandi Hills Pharma; can dispense"));
    }

    // ---- accounts

    private AppUser patient(String careId, String phone, String name, List<String> allergies, Instant now) {
        return account(careId, phone, name, "PATIENT", now, u -> u.setAllergies(allergies));
    }

    private AppUser account(String careId, String phone, String name, String role, Instant now, java.util.function.Consumer<AppUser> extra) {
        var existing = userRepository.findByCareId(careId);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (userRepository.existsByPhone(phone)) {
            throw new DemoSeedException("phone " + phone + " is already used by another account, so " + careId + " was not created");
        }
        Consent consent = new Consent();
        consent.setAccepted(true);
        consent.setPolicyVersion("demo");
        consent.setAcceptedAt(now);

        AppUser u = new AppUser();
        u.setCareId(careId);
        u.setPhone(phone);
        u.setName(name);
        u.setRole(role);
        u.setPasswordHash(passwordEncoder.encode(demoPassword));
        u.setConsent(consent);
        u.setCreatedAt(now);
        extra.accept(u);
        return userRepository.save(u);
    }

    private void verified(AppUser u) {
        u.setVerificationStatus(VerificationStatus.VERIFIED);
        u.setVerificationReasonCode(LicenceVerificationResult.MATCHED);
        u.setVerificationReason("Demo account matching the invented licence registry.");
    }

    // ---- pharmacy

    private Pharmacy demoPharmacy(Instant now) {
        return pharmacyRepository.findBySeedKey(PHARMACY_SEED_KEY).orElseGet(() -> {
            Pharmacy p = new Pharmacy();
            p.setSeedKey(PHARMACY_SEED_KEY);
            p.setName("Nandi Hills Pharma");
            p.setAddress("Hebbal, Bengaluru 560024");
            p.setState("KA");
            p.setVerified(true);
            return pharmacyRepository.save(p);
        });
    }

    // ---- grants and prescriptions

    private void grant(AppUser patient, AppUser grantee, List<GrantScope> scope, Instant expiresAt, Instant now) {
        boolean alreadyActive = grantRepository.findByPatientIdAndGranteeIdAndStatus(patient.getId(), grantee.getId(), GrantStatus.ACTIVE)
                .stream().anyMatch(g -> g.isUsableAt(now) && grantee.getRole().equals(g.getRole()));
        if (alreadyActive) {
            return;
        }
        AccessGrant g = new AccessGrant();
        g.setPatientId(patient.getId());
        g.setCareId(patient.getCareId());
        g.setGranteeId(grantee.getId());
        g.setRole(grantee.getRole());
        g.setGranteeName(grantee.getName());
        g.setGranteeFacilityName(grantee.getFacilityName());
        g.setScope(scope);
        g.setStatus(GrantStatus.ACTIVE);
        g.setReason("Demo data");
        g.setExpiresAt(expiresAt);
        g.setCreatedAt(now);
        g.setApprovedAt(now);
        grantRepository.save(g);
    }

    private void warfarinFor(AppUser patient, AppUser doctor, Instant now) {
        boolean has = Courses.live(prescriptionRepository, patient.getCareId()).stream()
                .anyMatch(p -> "Warfarin".equalsIgnoreCase(p.getDrug()));
        if (has) {
            return;
        }
        Prescription p = new Prescription();
        p.setCareId(patient.getCareId());
        p.setDoctorId(doctor.getId());
        p.setDoctorName(doctor.getName());
        p.setDoctorFacilityName(doctor.getFacilityName());
        p.setDrug("Warfarin");
        p.setDose("5 mg");
        p.setDuration("30 days");
        p.setDurationDays(30);
        p.setFrequency(Frequency.ONCE_DAILY);
        p.setSchedule(Frequency.ONCE_DAILY.defaultTimes());
        p.setStatus(PrescriptionStatus.ACTIVE);
        p.setIssuedAt(now);
        PrescriptionVersion v = new PrescriptionVersion();
        v.setVersion(1);
        v.setChangeType(ChangeType.ISSUED);
        v.setDose(p.getDose());
        v.setDuration(p.getDuration());
        v.setDurationDays(30);
        v.setFrequency(p.getFrequency());
        v.setSchedule(p.getSchedule());
        v.setChangedByUserId(doctor.getId());
        v.setChangedByName(doctor.getName());
        v.setChangedByRole("DOCTOR");
        v.setChangedAt(now);
        p.setVersions(new ArrayList<>(List.of(v)));
        p.setCurrentVersion(1);
        Prescription saved = prescriptionRepository.save(p);
        try {
            doseScheduleService.generate(saved, patient.getId());
        } catch (RuntimeException e) {
            log.warn("demo doses could not be scheduled ({})", e.getClass().getSimpleName());
        }
    }

    private String login(String role, String careId, String note) {
        return String.format(Locale.ROOT, "%-9s %s  password: %s  - %s", role, careId, demoPassword, note);
    }
}
