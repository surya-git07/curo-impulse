package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.LoginRequest;
import com.curo.curo_backend.dto.LoginResponse;
import com.curo.curo_backend.dto.SignupRequest;
import com.curo.curo_backend.dto.SignupResponse;
import com.curo.curo_backend.dto.UserSummary;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.Consent;
import com.curo.curo_backend.repository.UserRepository;
import com.curo.curo_backend.security.JwtService;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Service
public class AuthService {

    private static final Set<String> SELF_SIGNUP_ROLES = Set.of("PATIENT", "CAREGIVER");
    private static final Set<String> LICENCED_ROLES = Set.of("DOCTOR", "PHARMACY");
    private static final int MIN_PASSWORD_CHARS = 8;
    private static final int MAX_PASSWORD_BYTES = 72; // bcrypt limit
    private static final int CARE_ID_ATTEMPTS = 10;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CareIdGenerator careIdGenerator;

    // Compared against when the user does not exist, so unknown and wrong-password logins cost the same
    private String dummyHash;

    public SignupResponse signup(SignupRequest request) {
        String role = request.getRole().trim().toUpperCase(Locale.ROOT);
        if (LICENCED_ROLES.contains(role)) {
            throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "ROLE_NOT_SUPPORTED_YET",
                    "Licence-based signup for " + role + " is not available yet.");
        }
        if (!SELF_SIGNUP_ROLES.contains(role)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE",
                    "Role must be PATIENT or CAREGIVER.");
        }

        var consentIn = request.getConsent();
        if (consentIn == null || !Boolean.TRUE.equals(consentIn.getAccepted())
                || consentIn.getPolicyVersion() == null || consentIn.getPolicyVersion().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CONSENT_REQUIRED",
                    "You must accept the privacy policy to create an account.");
        }

        String password = request.getPassword();
        if (password.length() < MIN_PASSWORD_CHARS || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD",
                    "Password must be at least " + MIN_PASSWORD_CHARS + " characters and at most " + MAX_PASSWORD_BYTES + " bytes.");
        }

        String phone = request.getPhone().trim();
        if (userRepository.existsByPhone(phone)) {
            throw new ApiException(HttpStatus.CONFLICT, "PHONE_TAKEN", "This phone number is already registered.");
        }

        Consent consent = new Consent();
        consent.setAccepted(true);
        consent.setPolicyVersion(consentIn.getPolicyVersion().trim());
        consent.setAcceptedAt(Instant.now());

        AppUser user = new AppUser();
        user.setName(request.getName().trim());
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setConsent(consent);
        user.setCreatedAt(Instant.now());

        AppUser saved = saveWithNewCareId(user);
        return new SignupResponse(saved.getId(), saved.getCareId(), saved.getName(), saved.getRole(), saved.getConsent());
    }

    public LoginResponse login(LoginRequest request) {
        String identifier = request.getIdentifier().trim();
        String candidateCareId = identifier.toUpperCase(Locale.ROOT);

        AppUser user = (CareIdGenerator.isCareId(candidateCareId)
                ? userRepository.findByCareId(candidateCareId)
                : userRepository.findByPhone(identifier)).orElse(null);

        boolean passwordOk = matches(request.getPassword(), user == null ? dummyHash() : user.getPasswordHash());
        if (user == null || user.getDeletedAt() != null || !passwordOk) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid identifier or password.");
        }

        String token = jwtService.generateToken(user.getId(), user.getRole(), user.getCareId(), user.getFacilityId());
        Instant expiresAt = jwtService.extractClaim(token, Claims::getExpiration).toInstant();

        UserSummary summary = new UserSummary(user.getId(), user.getCareId(), user.getName(), user.getRole(),
                user.getFacilityId(), user.getFacilityName(), user.isProfileCompleted());
        return new LoginResponse(token, expiresAt, summary);
    }

    private AppUser saveWithNewCareId(AppUser user) {
        for (int attempt = 0; attempt < CARE_ID_ATTEMPTS; attempt++) {
            String careId = careIdGenerator.generate();
            if (userRepository.existsByCareId(careId)) {
                continue;
            }
            user.setCareId(careId);
            try {
                return userRepository.save(user);
            } catch (DuplicateKeyException e) {
                // unique index hit: either a concurrent signup took this phone, or the CareID collided
                if (userRepository.existsByPhone(user.getPhone())) {
                    throw new ApiException(HttpStatus.CONFLICT, "PHONE_TAKEN", "This phone number is already registered.");
                }
            }
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CAREID_UNAVAILABLE",
                "Could not allocate a CareID. Please try again.");
    }

    private boolean matches(String rawPassword, String hash) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, hash);
    }

    private synchronized String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode("not-a-real-password");
        }
        return dummyHash;
    }
}
