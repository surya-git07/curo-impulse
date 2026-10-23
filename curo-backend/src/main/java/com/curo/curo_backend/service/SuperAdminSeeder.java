package com.curo.curo_backend.service;

import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.Consent;
import com.curo.curo_backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Makes sure one SUPER_ADMIN account exists, so PENDING doctors and pharmacies can be approved. The phone comes from
 * SUPER_ADMIN_PHONE (a fake number by default) and the password only from SUPER_ADMIN_PASSWORD. With no password set
 * nothing is created, and the password is never logged. An existing account is left exactly as it is.
 */
@Component
@Order(20)
public class SuperAdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminSeeder.class);
    static final String DEFAULT_PHONE = "9000000001";
    private static final int CARE_ID_ATTEMPTS = 10;

    @Value("${SUPER_ADMIN_PHONE:" + DEFAULT_PHONE + "}")
    private String phone;

    @Value("${SUPER_ADMIN_PASSWORD:}")
    private String password;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CareIdGenerator careIdGenerator;

    @Override
    public void run(ApplicationArguments args) {
        try {
            seed();
        } catch (RuntimeException e) {
            log.warn("super admin seed skipped: MongoDB unreachable or failing ({})", e.getClass().getSimpleName());
        }
    }

    /** @return true if an account was created */
    boolean seed() {
        String number = phone == null ? "" : phone.trim();
        if (number.isEmpty()) {
            number = DEFAULT_PHONE;
        }
        if (userRepository.existsByPhone(number)) {
            log.info("super admin seed: an account with phone {} already exists, left unchanged", number);
            return false;
        }
        if (password == null || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            log.warn("super admin seed skipped: set SUPER_ADMIN_PASSWORD (8 to 72 bytes) in the environment to create the SUPER_ADMIN");
            return false;
        }
        AppUser admin = new AppUser();
        admin.setName("Platform Super Admin");
        admin.setPhone(number);
        admin.setRole("SUPER_ADMIN");
        admin.setPasswordHash(passwordEncoder.encode(password));
        Consent consent = new Consent();
        consent.setAccepted(true);
        consent.setPolicyVersion("2026-10-01");
        consent.setAcceptedAt(Instant.now());
        admin.setConsent(consent);
        admin.setCreatedAt(Instant.now());
        for (int attempt = 0; attempt < CARE_ID_ATTEMPTS; attempt++) {
            String careId = careIdGenerator.generate();
            if (userRepository.existsByCareId(careId)) {
                continue;
            }
            admin.setCareId(careId);
            userRepository.save(admin);
            log.info("super admin seed: created SUPER_ADMIN with phone {}", number);
            return true;
        }
        log.warn("super admin seed skipped: could not allocate a CareID");
        return false;
    }
}
