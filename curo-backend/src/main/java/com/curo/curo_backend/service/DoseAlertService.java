package com.curo.curo_backend.service;

import com.curo.curo_backend.model.AccessGrant;
import com.curo.curo_backend.model.AlertType;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.DoseLog;
import com.curo.curo_backend.model.DoseStatus;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.GrantStatus;
import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.repository.AccessGrantRepository;
import com.curo.curo_backend.repository.DoseLogRepository;
import com.curo.curo_backend.repository.PrescriptionRepository;
import com.curo.curo_backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The work done on each scheduler tick: remind patients of doses coming up, and when a dose is missed tell the patient, their
 * caregivers and the prescribing doctor, escalating when misses pile up. One failing dose never stops the others.
 *
 * Who may be told is limited by consent: a caregiver only through an active caregiver link, a doctor only while they hold an active
 * grant covering DOSES. What leaves the app (the email) never names the drug or the patient; the full text is in the inbox.
 */
@Service
public class DoseAlertService {

    private static final Logger log = LoggerFactory.getLogger(DoseAlertService.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MAX_ESCALATION = 20;

    public record MissedResult(long markedMissed, long alertsCreated) {
    }

    // how long before a dose the reminder goes out (the tick runs every 5 minutes, so it lands 5 to 10 minutes before)
    @Value("${alerts.reminder-lead-minutes:10}")
    private int reminderLeadMinutes;

    // this many misses in a row (and every multiple of it) raises an ADHERENCE alert
    @Value("${alerts.escalation-consecutive-misses:3}")
    private int escalationMisses;

    @Value("${app.time-zone:Asia/Kolkata}")
    private String timeZone;

    @Autowired
    private DoseLogRepository doseLogRepository;

    @Autowired
    private DoseUpdates doseUpdates;

    @Autowired
    private DoseScheduleService doseScheduleService;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccessGrantRepository grantRepository;

    @Autowired
    private AlertService alertService;

    @Autowired
    private Clock clock;

    // ---- reminders

    /** Sends the upcoming-dose reminder to the patient for doses due within the lead time. Each dose is reminded once. */
    public long sendReminders() {
        Instant now = clock.instant();
        long sent = 0;
        List<DoseLog> due = doseLogRepository.findByStatusAndScheduledAtGreaterThanAndScheduledAtLessThanEqualAndReminderSentAtIsNull(
                DoseStatus.UPCOMING, now, now.plus(Duration.ofMinutes(reminderLeadMinutes)));
        for (DoseLog dose : due) {
            try {
                // claim it first: if another run already did, skip. This is what stops double reminders.
                if (!doseUpdates.markReminderSent(dose.getId(), now)) {
                    continue;
                }
                AppUser patient = userRepository.findById(dose.getPatientId()).filter(u -> u.getDeletedAt() == null).orElse(null);
                if (patient == null) {
                    continue;
                }
                Prescription rx = prescriptionRepository.findById(dose.getPrescriptionId()).orElse(null);
                String what = rx == null ? "your medicine" : rx.getDrug() + " " + rx.getDose();
                alertService.create(patient, AlertType.UPCOMING_DOSE, patient.getCareId(), dose.getId(), dose.getPrescriptionId(),
                        new AlertService.Content("Coming up at " + local(dose) + ": " + what + ".",
                                "Curo: dose reminder", "You have a dose due soon. Open Curo to see it."));
                sent++;
            } catch (RuntimeException e) {
                log.warn("reminder for a dose failed ({})", e.getClass().getSimpleName());
            }
        }
        return sent;
    }

    // ---- missed doses

    /** Marks overdue doses MISSED and sends the missed-dose alerts (and escalations) for exactly the doses this call changed. */
    public MissedResult processMissed() {
        long alerts = 0;
        List<DoseLog> missed = doseScheduleService.markMissedDoses();
        for (DoseLog dose : missed) {
            try {
                alerts += notifyMissed(dose);
            } catch (RuntimeException e) {
                log.warn("alerts for a missed dose failed ({})", e.getClass().getSimpleName());
            }
        }
        return new MissedResult(missed.size(), alerts);
    }

    private long notifyMissed(DoseLog dose) {
        AppUser patient = userRepository.findById(dose.getPatientId()).filter(u -> u.getDeletedAt() == null).orElse(null);
        if (patient == null) {
            return 0;
        }
        Prescription rx = prescriptionRepository.findById(dose.getPrescriptionId()).orElse(null);
        String what = rx == null ? "a dose" : rx.getDrug() + " " + rx.getDose();
        String when = local(dose);

        List<AppUser> others = new ArrayList<>(caregivers(patient));
        if (rx != null) {
            doctorToTell(patient, rx).ifPresent(others::add);
        }

        long created = 0;
        alertService.create(patient, AlertType.MISSED_DOSE, patient.getCareId(), dose.getId(), dose.getPrescriptionId(),
                new AlertService.Content("You missed your " + what + " dose scheduled at " + when + ".",
                        "Curo: missed dose", "A scheduled dose was missed. Open Curo to see it."));
        created++;
        for (AppUser recipient : others) {
            alertService.create(recipient, AlertType.MISSED_DOSE, patient.getCareId(), dose.getId(), dose.getPrescriptionId(),
                    new AlertService.Content(patient.getName() + " (" + patient.getCareId() + ") missed " + what + " at " + when + ".",
                            "Curo: missed dose", "A dose was missed for patient " + patient.getCareId() + ". Open Curo to see it."));
            created++;
        }

        // escalation: the Nth miss in a row, and every Nth after
        int n = Math.min(Math.max(escalationMisses, 1), MAX_ESCALATION);
        int streak = streakEndingAt(dose);
        if (streak >= n && streak % n == 0) {
            List<AppUser> everyone = new ArrayList<>();
            everyone.add(patient);
            everyone.addAll(others);
            String drug = rx == null ? "a medicine" : rx.getDrug();
            for (AppUser recipient : everyone) {
                boolean self = recipient.getId().equals(patient.getId());
                alertService.create(recipient, AlertType.ADHERENCE, patient.getCareId(), dose.getId(), dose.getPrescriptionId(),
                        new AlertService.Content(self
                                ? "You have missed " + streak + " " + drug + " doses in a row."
                                : patient.getName() + " (" + patient.getCareId() + ") has missed " + streak + " " + drug + " doses in a row.",
                                "Curo: repeated missed doses",
                                self ? "Several doses in a row were missed. Open Curo to see them."
                                        : "Several doses in a row were missed for patient " + patient.getCareId() + ". Open Curo to see them."));
                created++;
            }
        }
        return created;
    }

    /** How many doses of this prescription, counting back from this one, were missed with nothing taken in between. */
    int streakEndingAt(DoseLog dose) {
        int n = Math.min(Math.max(escalationMisses, 1), MAX_ESCALATION);
        // enough history to see a full streak of n and a few multiples of it
        List<DoseLog> recent = doseLogRepository.findByPrescriptionIdAndScheduledAtLessThanEqual(dose.getPrescriptionId(),
                dose.getScheduledAt(), PageRequest.of(0, n * 5, Sort.by(Sort.Direction.DESC, "scheduledAt")));
        int streak = 0;
        for (DoseLog d : recent) {
            if (d.getStatus() != DoseStatus.MISSED) {
                break;
            }
            streak++;
        }
        return streak;
    }

    /** Caregivers who hold an active, unexpired caregiver grant covering DOSES. */
    private List<AppUser> caregivers(AppUser patient) {
        Instant now = clock.instant();
        List<AppUser> result = new ArrayList<>();
        for (AccessGrant g : grantRepository.findByPatientIdAndRoleAndStatus(patient.getId(), "CAREGIVER", GrantStatus.ACTIVE)) {
            if (g.isUsableAt(now) && g.getScope() != null && g.getScope().contains(GrantScope.DOSES)) {
                userRepository.findById(g.getGranteeId()).filter(u -> u.getDeletedAt() == null).ifPresent(result::add);
            }
        }
        return result;
    }

    /** The prescribing doctor, but only while they hold an active grant that covers DOSES. Without consent they are not told. */
    private Optional<AppUser> doctorToTell(AppUser patient, Prescription rx) {
        Instant now = clock.instant();
        boolean mayKnow = grantRepository.findByPatientIdAndGranteeIdAndStatus(patient.getId(), rx.getDoctorId(), GrantStatus.ACTIVE).stream()
                .anyMatch(g -> g.isUsableAt(now) && "DOCTOR".equals(g.getRole()) && g.getScope() != null && g.getScope().contains(GrantScope.DOSES));
        return mayKnow ? userRepository.findById(rx.getDoctorId()).filter(u -> u.getDeletedAt() == null) : Optional.empty();
    }

    private String local(DoseLog dose) {
        return dose.getScheduledAt().atZone(ZoneId.of(timeZone)).format(TIME);
    }
}
