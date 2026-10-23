package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.ActorView;
import com.curo.curo_backend.dto.DiscontinueRequest;
import com.curo.curo_backend.dto.DrugItem;
import com.curo.curo_backend.dto.HistoryResponse;
import com.curo.curo_backend.dto.InteractionResult;
import com.curo.curo_backend.dto.IssueRequest;
import com.curo.curo_backend.dto.IssueResponse;
import com.curo.curo_backend.dto.ModifyRequest;
import com.curo.curo_backend.dto.PrescriptionView;
import com.curo.curo_backend.dto.VersionView;
import com.curo.curo_backend.dto.WarningView;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.ChangeType;
import com.curo.curo_backend.model.DrugMaster;
import com.curo.curo_backend.model.Frequency;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.model.PrescriptionStatus;
import com.curo.curo_backend.model.PrescriptionVersion;
import com.curo.curo_backend.model.Severity;
import com.curo.curo_backend.repository.PrescriptionRepository;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Issue, modify and discontinue prescriptions (docs/API_CONTRACT.md section 7). Every method that touches a patient's
 * prescriptions goes through AccessGuard first, which also writes the audit entry.
 */
@Service
public class PrescriptionService {

    private static final Logger log = LoggerFactory.getLogger(PrescriptionService.class);

    private static final int MAX_DRUGS = 20;
    private static final int MAX_TEXT = 100;
    private static final int MAX_REASON = 500;
    private static final Pattern TIME = Pattern.compile("^([01][0-9]|2[0-3]):[0-5][0-9]$");
    private static final Pattern DURATION = Pattern.compile("^(\\d{1,3})\\s*(d|day|days|w|wk|wks|week|weeks)$", Pattern.CASE_INSENSITIVE);

    @Value("${app.time-zone:Asia/Kolkata}")
    private String timeZone;

    @Autowired
    private CurrentUserService currentUser;

    @Autowired
    private AccessGuard accessGuard;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private InteractionChecker interactionChecker;

    @Autowired
    private DrugCatalog drugCatalog;

    @Autowired
    private Clock clock;

    @Autowired
    private DoseScheduleService doseScheduleService;

    private record Schedule(Frequency frequency, List<String> times) {
    }

    private record Course(String text, Integer days) {
    }

    private record Plan(String id, String drug, Optional<DrugMaster> resolved, String dose, Course course, Schedule schedule) {
    }

    // ---- issue

    public IssueResponse issue(IssueRequest request) {
        AppUser doctor = requireDoctor();
        String careId = request.getCareId().trim().toUpperCase(Locale.ROOT);

        // Everything about the request itself is checked first, so a refused doctor learns nothing about the patient
        List<DrugItem> items = request.getDrugs();
        if (items == null || items.isEmpty() || items.size() > MAX_DRUGS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DRUGS_REQUIRED", "Send between 1 and " + MAX_DRUGS + " drugs.");
        }
        List<Plan> plans = new ArrayList<>();
        for (DrugItem item : items) {
            plans.add(plan(item));
        }

        // One audit entry names every prescription this call will create
        String resourceIds = String.join(",", plans.stream().map(Plan::id).toList());
        AccessGuard.Granted granted = accessGuard.require(careId, GrantScope.PRESCRIPTIONS, AuditAction.WRITE, "PRESCRIPTION", resourceIds);
        AppUser patient = granted.patient();
        if (patient.getConsent() == null || !patient.getConsent().isAccepted()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CONSENT_WITHDRAWN",
                    "The patient has withdrawn consent, so no new prescriptions can be issued.");
        }

        Instant now = clock.instant();
        ZoneId zone = ZoneId.of(timeZone);
        LocalDate today = now.atZone(zone).toLocalDate();

        List<String> namesSoFar = new ArrayList<>(Courses.live(prescriptionRepository, careId).stream()
                .filter(p -> Courses.isCurrent(p, today, zone)).map(Prescription::getDrug).toList());

        List<Prescription> created = new ArrayList<>();
        List<WarningView> warnings = new ArrayList<>();
        for (Plan plan : plans) {
            // Each drug is checked against what the patient already takes and the drugs earlier in this same request
            InteractionResult result = safeCheck(plan.drug(), namesSoFar, patient.getAllergies());
            if (result.getSeverity() != Severity.NONE) {
                warnings.add(new WarningView(plan.drug(), result.getSeverity(), result.getWarning(), result.getExplanation()));
            }
            namesSoFar.add(plan.drug());
            created.add(build(plan, careId, doctor, now));
        }

        // Saved whatever the warnings say. The check advises; it never blocks.
        List<Prescription> saved = prescriptionRepository.saveAll(created);
        saved.forEach(p -> scheduleDoses(p, patient, false));
        return new IssueResponse(saved.stream().map(p -> view(p, false)).toList(), warnings);
    }

    // ---- modify

    public PrescriptionView modify(String id, ModifyRequest request) {
        AppUser doctor = requireDoctor();
        Prescription p = find(id);
        AppUser patient = accessGuard.require(p.getCareId(), GrantScope.PRESCRIPTIONS, AuditAction.WRITE, "PRESCRIPTION", id).patient();
        requireIssuer(doctor, p);
        requireActive(p);

        String reason = requireReason(request.getReason());
        List<String> oldTimes = p.getSchedule();
        Integer oldDays = p.getDurationDays();

        String dose = p.getDose();
        if (request.getDose() != null) {
            dose = text(request.getDose(), "dose", "DOSE_REQUIRED");
        }

        Course course = new Course(p.getDuration(), p.getDurationDays());
        if (request.getDuration() != null || request.getDurationDays() != null) {
            course = course(request.getDuration(), request.getDurationDays());
        }

        Frequency frequency = p.getFrequency();
        List<String> times = p.getSchedule();
        if (request.getFrequency() != null || request.getTimes() != null) {
            String freqText = request.getFrequency() != null ? request.getFrequency() : (frequency == null ? null : frequency.name());
            // new times without a new frequency keep the current frequency; a new frequency alone resets to its default times
            Schedule schedule = schedule(freqText, request.getTimes());
            frequency = schedule.frequency();
            times = schedule.times();
        }

        boolean changed = !Objects.equals(dose, p.getDose())
                || !Objects.equals(course.text(), p.getDuration())
                || !Objects.equals(course.days(), p.getDurationDays())
                || frequency != p.getFrequency()
                || !Objects.equals(times, p.getSchedule());
        if (!changed) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_CHANGE", "Nothing differs from the current prescription.");
        }

        p.setDose(dose);
        p.setDuration(course.text());
        p.setDurationDays(course.days());
        p.setFrequency(frequency);
        p.setSchedule(times);
        appendVersion(p, ChangeType.MODIFIED, reason, doctor, clock.instant());
        Prescription saved = prescriptionRepository.save(p);
        // A dose-text change leaves the schedule alone; a change to the times or the length rebuilds the doses still ahead
        if (!Objects.equals(oldTimes, saved.getSchedule()) || !Objects.equals(oldDays, saved.getDurationDays())) {
            scheduleDoses(saved, patient, true);
        }
        return view(saved, true);
    }

    // ---- discontinue

    public PrescriptionView discontinue(String id, DiscontinueRequest request) {
        AppUser doctor = requireDoctor();
        Prescription p = find(id);
        accessGuard.require(p.getCareId(), GrantScope.PRESCRIPTIONS, AuditAction.WRITE, "PRESCRIPTION", id);
        requireIssuer(doctor, p);
        requireActive(p);

        String reason = requireReason(request == null ? null : request.getReason());
        Instant now = clock.instant();
        p.setStatus(PrescriptionStatus.DISCONTINUED);
        p.setDiscontinuedAt(now);
        appendVersion(p, ChangeType.DISCONTINUED, reason, doctor, now);
        Prescription saved = prescriptionRepository.save(p);
        try {
            doseScheduleService.cancelFuture(saved.getId()); // past doses stay as history
        } catch (RuntimeException e) {
            log.warn("future doses of {} could not be cancelled ({})", saved.getId(), e.getClass().getSimpleName());
        }
        return view(saved, true);
    }

    // ---- history

    public HistoryResponse history(String id) {
        AppUser user = currentUser.require();
        if (!"DOCTOR".equals(user.getRole()) && !"PHARMACY".equals(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE", "Only a DOCTOR or PHARMACY can read prescription history.");
        }
        Prescription p = find(id);
        accessGuard.require(p.getCareId(), GrantScope.PRESCRIPTIONS, AuditAction.READ, "PRESCRIPTION", id);
        return new HistoryResponse(p.getId(), p.getCareId(), p.getDrug(), p.getCurrentVersion(), versions(p));
    }

    // ---- helpers

    // The prescription is already saved, so a problem here must not turn the response into an error (a retry would issue it twice)
    private void scheduleDoses(Prescription p, AppUser patient, boolean replaceFuture) {
        try {
            if (replaceFuture) {
                doseScheduleService.cancelFuture(p.getId());
            }
            doseScheduleService.generate(p, patient.getId());
        } catch (RuntimeException e) {
            log.warn("doses for prescription {} could not be scheduled ({})", p.getId(), e.getClass().getSimpleName());
        }
    }

    private AppUser requireDoctor() {
        AppUser user = currentUser.require();
        if (!"DOCTOR".equals(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE", "Only a DOCTOR can do this.");
        }
        return user;
    }

    private Prescription find(String id) {
        return prescriptionRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PRESCRIPTION_NOT_FOUND", "No such prescription."));
    }

    private void requireIssuer(AppUser doctor, Prescription p) {
        if (!doctor.getId().equals(p.getDoctorId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the doctor who issued this prescription can change it.");
        }
    }

    private void requireActive(Prescription p) {
        if (!p.getStatus().isLive()) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", "This prescription has already been discontinued.");
        }
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "Say why the prescription is changing.");
        }
        if (reason.trim().length() > MAX_REASON) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "reason is too long.");
        }
        return reason.trim();
    }

    private InteractionResult safeCheck(String drug, List<String> others, List<String> allergies) {
        try {
            return interactionChecker.check(drug, List.copyOf(others), allergies);
        } catch (RuntimeException e) {
            // A broken check must not stop a prescription. The doctor is told it did not run.
            log.warn("interaction check failed ({})", e.getClass().getSimpleName());
            return new InteractionResult(Severity.UNKNOWN, "The automated check could not run.",
                    "Interactions and allergies were not checked. Verify them manually.");
        }
    }

    private Plan plan(DrugItem item) {
        String drug = text(item.getDrug(), "drug", "DRUG_REQUIRED");
        String dose = text(item.getDose(), "dose", "DOSE_REQUIRED");
        Optional<DrugMaster> resolved = drugCatalog.resolve(drug);
        return new Plan(new ObjectId().toHexString(), resolved.map(DrugMaster::getName).orElse(drug), resolved, dose,
                course(item.getDuration(), item.getDurationDays()), schedule(item.getFrequency(), item.getTimes()));
    }

    private String text(String value, String field, String code) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, code, field + " is required.");
        }
        if (value.trim().length() > MAX_TEXT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", field + " is too long.");
        }
        return value.trim();
    }

    private Schedule schedule(String frequencyText, List<String> times) {
        Frequency frequency = null;
        if (frequencyText != null && !frequencyText.isBlank()) {
            try {
                frequency = Frequency.valueOf(frequencyText.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FREQUENCY",
                        "frequency must be ONCE_DAILY, TWICE_DAILY, THREE_TIMES_DAILY or FOUR_TIMES_DAILY.");
            }
        }
        if (times == null || times.isEmpty()) {
            return new Schedule(frequency, frequency == null ? List.of() : frequency.defaultTimes());
        }
        if (frequency == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE", "frequency is required when times are given.");
        }
        TreeSet<String> sorted = new TreeSet<>();
        for (String t : times) {
            if (t == null || !TIME.matcher(t.trim()).matches()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE", "times must be HH:mm, for example 08:30.");
            }
            sorted.add(t.trim());
        }
        if (sorted.size() != frequency.defaultTimes().size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SCHEDULE",
                    frequency + " needs exactly " + frequency.defaultTimes().size() + " different times.");
        }
        return new Schedule(frequency, List.copyOf(sorted));
    }

    private Course course(String durationText, Integer durationDays) {
        String text = durationText == null || durationText.isBlank() ? null : durationText.trim();
        if (text != null && text.length() > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "duration is too long.");
        }
        if (durationDays != null) {
            if (durationDays < 1 || durationDays > 365) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DURATION", "durationDays must be between 1 and 365.");
            }
            return new Course(text, durationDays);
        }
        if (text == null) {
            return new Course(null, null);
        }
        Matcher m = DURATION.matcher(text);
        if (!m.matches()) {
            return new Course(text, null); // kept as written; no end date can be worked out
        }
        int amount = Integer.parseInt(m.group(1));
        int days = m.group(2).toLowerCase(Locale.ROOT).startsWith("w") ? amount * 7 : amount;
        if (days < 1 || days > 365) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DURATION", "duration must be between 1 and 365 days.");
        }
        return new Course(text, days);
    }

    private Prescription build(Plan plan, String careId, AppUser doctor, Instant now) {
        Prescription p = new Prescription();
        p.setId(plan.id());
        p.setCareId(careId);
        p.setDoctorId(doctor.getId());
        p.setDoctorName(doctor.getName());
        p.setDoctorFacilityName(doctor.getFacilityName());
        p.setDrug(plan.drug());
        p.setDose(plan.dose());
        p.setDuration(plan.course().text());
        p.setDurationDays(plan.course().days());
        p.setFrequency(plan.schedule().frequency());
        p.setSchedule(plan.schedule().times());
        p.setStatus(PrescriptionStatus.ACTIVE);
        p.setIssuedAt(now);
        p.setVersions(new ArrayList<>());
        appendVersion(p, ChangeType.ISSUED, null, doctor, now);
        return p;
    }

    // The top-level fields must already hold the new values; this records them as the next version
    private void appendVersion(Prescription p, ChangeType type, String reason, AppUser by, Instant at) {
        PrescriptionVersion v = new PrescriptionVersion();
        v.setVersion(p.getCurrentVersion() + 1);
        v.setChangeType(type);
        v.setDose(p.getDose());
        v.setDuration(p.getDuration());
        v.setDurationDays(p.getDurationDays());
        v.setFrequency(p.getFrequency());
        v.setSchedule(p.getSchedule());
        v.setReason(reason);
        v.setChangedByUserId(by.getId());
        v.setChangedByName(by.getName());
        v.setChangedByRole(by.getRole());
        v.setChangedAt(at);
        p.getVersions().add(v);
        p.setCurrentVersion(v.getVersion());
    }

    private PrescriptionView view(Prescription p, boolean withVersions) {
        return new PrescriptionView(p.getId(), p.getCareId(), p.getDoctorId(), p.getDrug(), p.getDose(), p.getDuration(),
                p.getDurationDays(), p.getFrequency(), p.getSchedule(), p.getStatus(), p.getIssuedAt(), p.getDiscontinuedAt(),
                p.getCurrentVersion(), withVersions ? versions(p) : null);
    }

    private List<VersionView> versions(Prescription p) {
        return p.getVersions().stream()
                .map(v -> new VersionView(v.getVersion(), v.getChangeType(), v.getDose(), v.getDuration(), v.getDurationDays(),
                        v.getFrequency(), v.getSchedule(), v.getReason(),
                        new ActorView(v.getChangedByUserId(), v.getChangedByRole(), v.getChangedByName(), null), v.getChangedAt()))
                .toList();
    }
}
