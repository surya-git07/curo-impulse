package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.PatientSummaryView;
import com.curo.curo_backend.dto.PrescriptionView;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.AuditAction;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.repository.PrescriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;

/** The doctor Patient screen: one consent-gated read. Without an active grant nothing about the patient is loaded. */
@Service
public class PatientSummaryService {

    @Value("${app.time-zone:Asia/Kolkata}")
    private String timeZone;

    @Autowired
    private CurrentUserService currentUser;

    @Autowired
    private AccessGuard accessGuard;

    @Autowired
    private MedicationService medicationService;

    @Autowired
    private PrescriptionRepository prescriptionRepository;

    @Autowired
    private Clock clock;

    public PatientSummaryView summary(String careId) {
        if (!"DOCTOR".equals(currentUser.require().getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE", "Only a DOCTOR can read a patient summary.");
        }
        // throws 403 NO_ACTIVE_GRANT (and audits the refusal) before anything about the patient is read
        AccessGuard.Granted granted = accessGuard.require(careId, GrantScope.PROFILE, AuditAction.READ, "PROFILE", careId);
        AppUser patient = granted.patient();
        var scope = granted.grant().getScope();

        List<com.curo.curo_backend.dto.MedicationView> medications = scope.contains(GrantScope.MEDICATIONS)
                ? medicationService.currentMedications(patient.getCareId()) : null;
        List<PrescriptionView> prescriptions = scope.contains(GrantScope.PRESCRIPTIONS)
                ? prescriptionRepository.findByCareIdOrderByIssuedAtDesc(patient.getCareId()).stream()
                        .map(p -> PrescriptionService.view(p, true)).toList()
                : null;

        return new PatientSummaryView(patient.getCareId(), patient.getName(), age(patient), patient.getGender(), patient.getBloodGroup(),
                patient.getAllergies() == null ? List.of() : List.copyOf(patient.getAllergies()),
                patient.getChronicConditions() == null ? List.of() : List.copyOf(patient.getChronicConditions()),
                medications, prescriptions);
    }

    private Integer age(AppUser patient) {
        if (patient.getDateOfBirth() == null) {
            return null;
        }
        LocalDate today = clock.instant().atZone(ZoneId.of(timeZone)).toLocalDate();
        return Period.between(patient.getDateOfBirth(), today).getYears();
    }
}
