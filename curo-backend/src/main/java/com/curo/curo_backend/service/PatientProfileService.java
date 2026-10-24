package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.PatientProfileView;
import com.curo.curo_backend.dto.UpdateProfileRequest;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** The signed-in patient's own profile. Only allergies are editable so far. */
@Service
public class PatientProfileService {

    private static final int MAX_ALLERGIES = 30;
    private static final int MAX_LENGTH = 60;

    @Autowired
    private CurrentUserService currentUser;

    @Autowired
    private UserRepository userRepository;

    public PatientProfileView get() {
        return view(requirePatient());
    }

    private static final java.util.Set<String> GENDERS = java.util.Set.of("MALE", "FEMALE", "OTHER");
    private static final java.util.Set<String> BLOOD_GROUPS = java.util.Set.of("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-");

    public PatientProfileView update(UpdateProfileRequest request) {
        AppUser patient = requirePatient();
        if (request == null || (request.getAllergies() == null && request.getChronicConditions() == null
                && request.getDateOfBirth() == null && request.getGender() == null && request.getBloodGroup() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Send at least one of allergies, chronicConditions, dateOfBirth, gender or bloodGroup (an empty list clears a list).");
        }
        // validate everything first so a bad value changes nothing
        List<String> allergies = request.getAllergies() == null ? null : cleanList(request.getAllergies(), "allergies");
        List<String> conditions = request.getChronicConditions() == null ? null : cleanList(request.getChronicConditions(), "chronicConditions");
        String gender = null;
        if (request.getGender() != null) {
            gender = request.getGender().trim().toUpperCase(Locale.ROOT);
            if (!GENDERS.contains(gender)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "gender must be MALE, FEMALE or OTHER.");
            }
        }
        String bloodGroup = null;
        if (request.getBloodGroup() != null) {
            bloodGroup = request.getBloodGroup().trim().toUpperCase(Locale.ROOT);
            if (!BLOOD_GROUPS.contains(bloodGroup)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "bloodGroup must be one of A+, A-, B+, B-, AB+, AB-, O+, O-.");
            }
        }
        if (request.getDateOfBirth() != null
                && (request.getDateOfBirth().isAfter(java.time.LocalDate.now()) || request.getDateOfBirth().getYear() < 1900)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "dateOfBirth must be a past date after 1900.");
        }

        if (allergies != null) {
            patient.setAllergies(allergies);
        }
        if (conditions != null) {
            patient.setChronicConditions(conditions);
        }
        if (gender != null) {
            patient.setGender(gender);
        }
        if (bloodGroup != null) {
            patient.setBloodGroup(bloodGroup);
        }
        if (request.getDateOfBirth() != null) {
            patient.setDateOfBirth(request.getDateOfBirth());
        }
        return view(userRepository.save(patient));
    }

    // trimmed, blanks dropped, repeats (ignoring case) dropped, order kept
    private List<String> cleanList(List<String> values, String field) {
        if (values.size() > MAX_ALLERGIES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "At most " + MAX_ALLERGIES + " entries in " + field + ".");
        }
        Set<String> seen = new TreeSet<>();
        List<String> cleaned = new ArrayList<>();
        for (String raw : values) {
            String entry = raw == null ? "" : raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            if (entry.length() > MAX_LENGTH) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Each entry in " + field + " must be at most " + MAX_LENGTH + " characters.");
            }
            if (seen.add(entry.toLowerCase(Locale.ROOT))) {
                cleaned.add(entry);
            }
        }
        return cleaned;
    }

    private AppUser requirePatient() {
        AppUser user = currentUser.require();
        if (!"PATIENT".equals(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN_ROLE", "Only a patient has a patient profile.");
        }
        return user;
    }

    private PatientProfileView view(AppUser p) {
        return new PatientProfileView(p.getId(), p.getCareId(), p.getName(), p.getPhone(),
                p.getAllergies() == null ? List.of() : p.getAllergies(), p.isProfileCompleted(),
                p.getDateOfBirth(), p.getGender(), p.getBloodGroup(),
                p.getChronicConditions() == null ? List.of() : p.getChronicConditions());
    }
}
