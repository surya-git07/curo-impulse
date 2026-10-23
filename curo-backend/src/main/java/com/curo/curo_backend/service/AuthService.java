package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.LoginRequest;
import com.curo.curo_backend.dto.LoginResponse;
import com.curo.curo_backend.dto.SignupRequest;
import com.curo.curo_backend.dto.SignupResponse;
import com.curo.curo_backend.dto.UserSummary;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.Consent;
import com.curo.curo_backend.model.VerificationStatus;
import com.curo.curo_backend.repository.UserRepository;
import com.curo.curo_backend.security.JwtService;
import com.curo.curo_backend.util.GstinValidator;
import com.curo.curo_backend.util.IndianStates;
import com.curo.curo_backend.util.LicenceNumbers;
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
    private static final String DOCTOR = "DOCTOR";
    private static final String PHARMACY = "PHARMACY";
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

    @Autowired
    private LicenceVerificationService licenceVerificationService;

    // Compared against when the user does not exist, so unknown and wrong-password logins cost the same
    private String dummyHash;

    public SignupResponse signup(SignupRequest request) {
        String role = request.getRole().trim().toUpperCase(Locale.ROOT);
        boolean licenced = DOCTOR.equals(role) || PHARMACY.equals(role);
        if (!licenced && !SELF_SIGNUP_ROLES.contains(role)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE",
                    "Role must be PATIENT, CAREGIVER, DOCTOR or PHARMACY.");
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

        AppUser user = new AppUser();
        user.setName(request.getName().trim());
        user.setPhone(phone);
        user.setRole(role);

        // Licence checks come before the (slow) password hash so rejected applications cost little
        if (DOCTOR.equals(role)) {
            checkDoctorLicence(request, user);
        } else if (PHARMACY.equals(role)) {
            checkPharmacyLicence(request, user);
        }

        Consent consent = new Consent();
        consent.setAccepted(true);
        consent.setPolicyVersion(consentIn.getPolicyVersion().trim());
        consent.setAcceptedAt(Instant.now());

        user.setPasswordHash(passwordEncoder.encode(password));
        user.setConsent(consent);
        user.setCreatedAt(Instant.now());

        AppUser saved = saveWithNewCareId(user);
        return new SignupResponse(saved.getId(), saved.getCareId(), saved.getName(), saved.getRole(), saved.getConsent(),
                saved.getVerificationStatus(), saved.getVerificationReasonCode(), saved.getVerificationReason());
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
        // Only after the password is right, so these codes do not reveal which accounts exist
        if (user.getVerificationStatus() == VerificationStatus.PENDING) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_PENDING",
                    "Your licence is awaiting approval by an administrator.");
        }
        if (user.getVerificationStatus() == VerificationStatus.REJECTED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_REJECTED",
                    "Your licence application was rejected.");
        }

        String token = jwtService.generateToken(user.getId(), user.getRole(), user.getCareId(), user.getFacilityId());
        Instant expiresAt = jwtService.extractClaim(token, Claims::getExpiration).toInstant();

        UserSummary summary = new UserSummary(user.getId(), user.getCareId(), user.getName(), user.getRole(),
                user.getFacilityId(), user.getFacilityName(), user.isProfileCompleted());
        return new LoginResponse(token, expiresAt, summary);
    }

    private void checkDoctorLicence(SignupRequest request, AppUser user) {
        if (isBlank(request.getLicenceNumber()) || isBlank(request.getStateCouncil())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LICENCE_FIELDS_REQUIRED",
                    "licenceNumber and stateCouncil are required for DOCTOR signup.");
        }
        String number = LicenceNumbers.normalize(request.getLicenceNumber());
        if (!LicenceNumbers.isValidDoctorNumber(number)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, LicenceVerificationResult.INVALID_NUMBER,
                    "Licence number format is not recognised.");
        }
        if (userRepository.existsByLicenceNumber(number)) {
            throw licenceTaken();
        }

        String council = request.getStateCouncil().trim();
        LicenceVerificationResult result = licenceVerificationService.verifyDoctor(number, council, user.getName());
        rejectOrRecord(result, user);

        user.setLicenceNumber(number);
        user.setStateCouncil(council);
    }

    private void checkPharmacyLicence(SignupRequest request, AppUser user) {
        if (isBlank(request.getDrugLicenceNumber()) || isBlank(request.getGstin()) || isBlank(request.getState())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LICENCE_FIELDS_REQUIRED",
                    "drugLicenceNumber, gstin and state are required for PHARMACY signup.");
        }
        String number = LicenceNumbers.normalize(request.getDrugLicenceNumber());
        if (!LicenceNumbers.isValidDrugLicenceNumber(number)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, LicenceVerificationResult.INVALID_NUMBER,
                    "Drug licence number format is not recognised.");
        }
        String state = request.getState().trim().toUpperCase(Locale.ROOT);
        if (!IndianStates.isKnownIso(state)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATE",
                    "state must be an ISO 3166-2:IN code such as TN or KA.");
        }

        String gstin = GstinValidator.normalize(request.getGstin());
        switch (GstinValidator.validate(gstin, state)) {
            case VALID -> { }
            case INVALID_FORMAT -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GSTIN",
                    "GSTIN must be 15 characters in the standard format.");
            case UNKNOWN_STATE_CODE -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GSTIN",
                    "GSTIN starts with an unknown state code.");
            case STATE_MISMATCH -> throw new ApiException(HttpStatus.BAD_REQUEST, "GSTIN_STATE_MISMATCH",
                    "The GSTIN state code does not belong to the state entered.");
            case INVALID_CHECK_CHARACTER -> throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GSTIN",
                    "GSTIN check character is wrong.");
        }

        if (userRepository.existsByDrugLicenceNumber(number)) {
            throw licenceTaken();
        }

        LicenceVerificationResult result = licenceVerificationService.verifyPharmacy(number, gstin, state, user.getName());
        rejectOrRecord(result, user);

        user.setDrugLicenceNumber(number);
        user.setGstin(gstin);
        user.setState(state);
    }

    // REJECTED becomes a 400 with the result's code; VERIFIED and PENDING are kept on the user
    private void rejectOrRecord(LicenceVerificationResult result, AppUser user) {
        if (result.getStatus() == VerificationStatus.REJECTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, result.getCode(), result.getMessage());
        }
        user.setVerificationStatus(result.getStatus());
        user.setVerificationReasonCode(result.getCode());
        user.setVerificationReason(result.getMessage());
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
                // unique index hit: a concurrent signup took this phone or licence, or the CareID collided
                if (userRepository.existsByPhone(user.getPhone())) {
                    throw new ApiException(HttpStatus.CONFLICT, "PHONE_TAKEN", "This phone number is already registered.");
                }
                if (user.getLicenceNumber() != null && userRepository.existsByLicenceNumber(user.getLicenceNumber())
                        || user.getDrugLicenceNumber() != null && userRepository.existsByDrugLicenceNumber(user.getDrugLicenceNumber())) {
                    throw licenceTaken();
                }
            }
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "CAREID_UNAVAILABLE",
                "Could not allocate a CareID. Please try again.");
    }

    private ApiException licenceTaken() {
        return new ApiException(HttpStatus.CONFLICT, "LICENCE_TAKEN",
                "This licence number is already registered to an account.");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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
