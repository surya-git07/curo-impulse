package com.curo.curo_backend.service;

import com.curo.curo_backend.model.DoseLog;
import com.curo.curo_backend.model.DoseStatus;
import com.curo.curo_backend.model.Prescription;
import com.curo.curo_backend.repository.DoseLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns a prescription's schedule into dose_logs rows, and owns the MISSED rule. Doses are created for each dose time
 * from the prescription's start day to its last day (or a rolling horizon when there is no duration), skipping times
 * that have already passed. Dose times are local (Asia/Kolkata); stored times are UTC.
 */
@Service
public class DoseScheduleService {

    @Value("${app.time-zone:Asia/Kolkata}")
    private String timeZone;

    // how far ahead to schedule a prescription that has no end date
    @Value("${doses.ongoing-horizon-days:30}")
    private int horizonDays;

    // a dose still UPCOMING this long after it was due is MISSED
    @Value("${doses.missed-grace-minutes:30}")
    private int graceMinutes;

    @Autowired
    private DoseLogRepository doseLogRepository;

    @Autowired
    private DoseUpdates doseUpdates;

    @Autowired
    private Clock clock;

    public int graceMinutes() {
        return graceMinutes;
    }

    /**
     * Creates the doses still ahead for this prescription. Existing doses are left alone, so it is safe to call after
     * cancelFuture: anything already taken early keeps its slot and is not duplicated.
     *
     * @return how many doses were created
     */
    public int generate(Prescription p, String patientId) {
        if (p.getSchedule() == null || p.getSchedule().isEmpty()) {
            return 0; // "as needed": nothing to remind about
        }
        ZoneId zone = ZoneId.of(timeZone);
        Instant now = clock.instant();
        LocalDate today = now.atZone(zone).toLocalDate();
        LocalDate first = Courses.startDate(p, zone).isAfter(today) ? Courses.startDate(p, zone) : today;
        LocalDate last = Courses.endDate(p, zone).orElse(today.plusDays(horizonDays - 1L));

        Set<Instant> taken = new HashSet<>();
        doseLogRepository.findByPrescriptionIdAndScheduledAtAfter(p.getId(), now).forEach(d -> taken.add(d.getScheduledAt()));

        List<DoseLog> doses = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(last); day = day.plusDays(1)) {
            for (String time : p.getSchedule()) {
                Instant at = day.atTime(LocalTime.parse(time)).atZone(zone).toInstant();
                if (at.isAfter(now) && !taken.contains(at)) {
                    DoseLog dose = new DoseLog();
                    dose.setPrescriptionId(p.getId());
                    dose.setPatientId(patientId);
                    dose.setScheduledAt(at);
                    dose.setStatus(DoseStatus.UPCOMING);
                    dose.setCreatedAt(now);
                    doses.add(dose);
                }
            }
        }
        doseLogRepository.saveAll(doses);
        return doses.size();
    }

    /** Removes the doses still ahead that nobody has taken. Past doses, taken or missed, are history and stay. */
    public long cancelFuture(String prescriptionId) {
        return doseLogRepository.deleteByPrescriptionIdAndStatusAndScheduledAtAfter(prescriptionId, DoseStatus.UPCOMING, clock.instant());
    }

    /** Marks every UPCOMING dose that is more than the grace period overdue as MISSED. Safe to run again and again. */
    public long markMissed() {
        return doseUpdates.markMissed(clock.instant().minus(Duration.ofMinutes(graceMinutes)));
    }
}
