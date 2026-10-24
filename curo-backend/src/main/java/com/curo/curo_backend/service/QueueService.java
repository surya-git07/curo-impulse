package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.DoctorOption;
import com.curo.curo_backend.dto.QueueEntryView;
import com.curo.curo_backend.dto.QueueView;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.AuditOutcome;
import com.curo.curo_backend.model.QueueEntry;
import com.curo.curo_backend.model.QueueStatus;
import com.curo.curo_backend.model.VerificationStatus;
import com.curo.curo_backend.repository.QueueEntryRepository;
import com.curo.curo_backend.repository.QueueQueries;
import com.curo.curo_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Comparator;
import java.util.Locale;

/**
 * The front-desk queue. Reception checks a patient in by CareID for one verified doctor; each doctor sees only their own
 * entries for today and marks them done. The queue is only a list of who is waiting. It never opens a patient record:
 * that still needs an access grant.
 */
@Service
public class QueueService {

    private static final String RECEPTION = "RECEPTION";
    private static final String DOCTOR = "DOCTOR";

    @Value("${app.time-zone:Asia/Kolkata}")
    private String timeZone;

    @Autowired
    private QueueEntryRepository queueRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private CurrentUserService currentUser;

    @Autowired
    private AuditService auditService;

    @Autowired
    private Clock clock;

    /** Verified doctors the front desk can pick: name and ids only. */
    public List<DoctorOption> verifiedDoctors() {
        requireRole(RECEPTION);
        return userRepository.findByRoleAndVerificationStatusAndDeletedAtIsNull(DOCTOR, VerificationStatus.VERIFIED).stream()
                .sorted(Comparator.comparing(AppUser::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(d -> new DoctorOption(d.getId(), d.getCareId(), d.getName()))
                .toList();
    }

    public QueueEntryView checkIn(String careIdRaw, String doctorId) {
        AppUser reception = requireRole(RECEPTION);
        // request problems come first: the doctor must be a VERIFIED, live DOCTOR account
        AppUser doctor = doctorId == null || doctorId.isBlank() ? null : userRepository.findById(doctorId.trim())
                .filter(u -> DOCTOR.equals(u.getRole()) && u.getVerificationStatus() == VerificationStatus.VERIFIED && u.getDeletedAt() == null)
                .orElse(null);
        if (doctor == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DOCTOR", "Choose a verified doctor.");
        }
        String careId = careIdRaw == null ? "" : careIdRaw.trim().toUpperCase(Locale.ROOT);
        AppUser patient = CareIdGenerator.isCareId(careId)
                ? userRepository.findByCareId(careId).filter(u -> "PATIENT".equals(u.getRole()) && u.getDeletedAt() == null).orElse(null)
                : null;
        if (patient == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PATIENT_NOT_FOUND", "No patient with that CareID.");
        }

        Instant[] today = today();
        if (mongoTemplate.exists(QueueQueries.waitingFor(careId, doctor.getId(), today[0], today[1]), QueueEntry.class)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_IN_QUEUE", "This patient is already waiting for this doctor today.");
        }

        QueueEntry entry = new QueueEntry();
        entry.setCareId(careId);
        entry.setPatientName(patient.getName());
        entry.setDoctorId(doctor.getId());
        entry.setDoctorName(doctor.getName());
        entry.setStatus(QueueStatus.WAITING);
        entry.setCheckedInAt(clock.instant());
        entry.setCheckedInBy(reception.getId());
        entry.setFacilityId(reception.getFacilityId());
        QueueEntry saved = queueRepository.save(entry);

        auditService.recordQuietly(patient, reception, AuditAction.WRITE, "QUEUE_ENTRY", saved.getId(), null, AuditOutcome.ALLOWED);
        // the reception view has no id: it is not needed there
        return new QueueEntryView(null, saved.getCareId(), saved.getPatientName(), saved.getDoctorId(), saved.getDoctorName(),
                saved.getStatus(), saved.getCheckedInAt());
    }

    public QueueView todaysQueue() {
        AppUser doctor = requireRole(DOCTOR);
        Instant[] today = today();
        List<QueueEntryView> entries = mongoTemplate.find(QueueQueries.betweenFor(doctor.getId(), today[0], today[1]), QueueEntry.class).stream()
                .map(QueueService::view).toList();
        int done = (int) entries.stream().filter(e -> e.getStatus() == QueueStatus.DONE).count();
        return new QueueView(new QueueView.Counts(entries.size(), done, entries.size() - done), entries);
    }

    public QueueEntryView markDone(String id) {
        AppUser doctor = requireRole(DOCTOR);
        QueueEntry entry = queueRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "QUEUE_ENTRY_NOT_FOUND", "No such queue entry."));
        // only the doctor the patient is waiting for; an older entry with no doctor belongs to nobody
        if (!doctor.getId().equals(entry.getDoctorId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "This queue entry belongs to another doctor.");
        }
        if (entry.getStatus() != QueueStatus.DONE) {
            entry.setStatus(QueueStatus.DONE);
            entry = queueRepository.save(entry);
        }
        return view(entry);
    }

    // the Indian calendar day, as for doses: [start of today, start of tomorrow)
    private Instant[] today() {
        ZoneId zone = ZoneId.of(timeZone);
        LocalDate day = clock.instant().atZone(zone).toLocalDate();
        return new Instant[]{day.atStartOfDay(zone).toInstant(), day.plusDays(1).atStartOfDay(zone).toInstant()};
    }

    private AppUser requireRole(String role) {
        AppUser user = currentUser.require();
        if (!role.equals(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE", "Only a " + role + " account can do this.");
        }
        return user;
    }

    private static QueueEntryView view(QueueEntry e) {
        return new QueueEntryView(e.getId(), e.getCareId(), e.getPatientName(), e.getDoctorId(), e.getDoctorName(),
                e.getStatus(), e.getCheckedInAt());
    }
}
