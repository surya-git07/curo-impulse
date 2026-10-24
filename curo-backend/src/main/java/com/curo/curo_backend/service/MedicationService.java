package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.MedicationProfile;
import com.curo.curo_backend.dto.MedicationView;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.model.PrescriptionStatus;
import com.curo.curo_backend.repository.PrescriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/** A patient's current medication profile: what they are taking right now. */
@Service
public class MedicationService {

    @Value("${app.time-zone:Asia/Kolkata}")
    private String timeZone;

    @Autowired
    private CurrentUserService currentUser;

    @Autowired
    private AccessGuard accessGuard;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private Clock clock;

    /** The signed-in patient's own medications. Their own data, so no grant and no audit entry. */
    public MedicationProfile mine() {
        AppUser patient = currentUser.require();
        if (!"PATIENT".equals(patient.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE", "Only a patient has a medication profile of their own.");
        }
        return profile(patient.getCareId(), patient.getAllergies());
    }

    /** Someone else's medications: needs an active grant covering MEDICATIONS. Allergies are included only if the grant also covers PROFILE. */
    public MedicationProfile forPatient(String careId) {
        AccessGuard.Granted granted = accessGuard.require(careId, GrantScope.MEDICATIONS, AuditAction.READ, "MEDICATIONS", careId);
        boolean mayseeAllergies = granted.grant().getScope().contains(GrantScope.PROFILE);
        return profile(granted.patient().getCareId(), mayseeAllergies ? granted.patient().getAllergies() : null);
    }

    private MedicationProfile profile(String careId, List<String> allergies) {
        return new MedicationProfile(careId, clock.instant(), currentMedications(careId), allergies == null ? null : List.copyOf(allergies));
    }

    /** The current medications (not discontinued, course not over), oldest first. Callers must already have checked consent. */
    List<MedicationView> currentMedications(String careId) {
        Instant now = clock.instant();
        ZoneId zone = ZoneId.of(timeZone);
        LocalDate today = now.atZone(zone).toLocalDate();

        return Courses.live(prescriptionRepository, careId).stream()
                .filter(p -> Courses.isCurrent(p, today, zone))
                .sorted(Comparator.comparing(Prescription::getIssuedAt).thenComparing(Prescription::getDrug))
                .map(p -> new MedicationView(p.getId(), p.getDrug(), p.getDose(), p.getFrequency(), p.getSchedule(),
                        Courses.startDate(p, zone), Courses.endDate(p, zone).orElse(null), p.getStatus(), p.getCurrentVersion(),
                        new MedicationView.Prescriber(p.getDoctorName(), p.getDoctorFacilityName())))
                .toList();
    }
}
