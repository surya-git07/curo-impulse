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

    public PatientProfileView update(UpdateProfileRequest request) {
        AppUser patient = requirePatient();
        if (request == null || request.getAllergies() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "allergies is required (an empty list clears it).");
        }
        if (request.getAllergies().size() > MAX_ALLERGIES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "At most " + MAX_ALLERGIES + " allergies.");
        }
        // trimmed, blanks dropped, repeats (ignoring case) dropped, order kept
        Set<String> seen = new TreeSet<>();
        List<String> cleaned = new ArrayList<>();
        for (String raw : request.getAllergies()) {
            String allergy = raw == null ? "" : raw.trim();
            if (allergy.isEmpty()) {
                continue;
            }
            if (allergy.length() > MAX_LENGTH) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Each allergy must be at most " + MAX_LENGTH + " characters.");
            }
            if (seen.add(allergy.toLowerCase(Locale.ROOT))) {
                cleaned.add(allergy);
            }
        }
        patient.setAllergies(cleaned);
        return view(userRepository.save(patient));
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
                p.getAllergies() == null ? List.of() : p.getAllergies(), p.isProfileCompleted());
    }
}
