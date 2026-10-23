package com.curo.curo_backend.service;

import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.ShareCode;
import com.curo.curo_backend.repository.ShareCodeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/** Six-digit, single-use, short-lived codes a patient hands to a clinician. */
@Service
public class ShareCodeService {

    private static final Pattern FORMAT = Pattern.compile("^[0-9]{6}$");
    private static final int ATTEMPTS = 10;

    @Value("${access.share-code.valid-minutes:15}")
    private int validMinutes;

    @Autowired
    private ShareCodeRepository shareCodeRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private Clock clock;

    private final SecureRandom random = new SecureRandom();

    public static boolean isWellFormed(String code) {
        return code != null && FORMAT.matcher(code).matches();
    }

    public ShareCode create(AppUser patient, List<GrantScope> scope, int durationHours) {
        Instant now = clock.instant();
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            ShareCode shareCode = new ShareCode();
            shareCode.setCode(String.format("%06d", random.nextInt(1_000_000)));
            shareCode.setPatientId(patient.getId());
            shareCode.setScope(scope);
            shareCode.setDurationHours(durationHours);
            shareCode.setCreatedAt(now);
            shareCode.setExpiresAt(now.plus(Duration.ofMinutes(validMinutes)));
            try {
                return shareCodeRepository.save(shareCode);
            } catch (DuplicateKeyException e) {
                // another live code has these digits; draw again
            }
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SHARE_CODE_UNAVAILABLE",
                "Could not create a share code. Please try again.");
    }

    /** Looks the code up without using it up. */
    public Optional<ShareCode> peek(String code) {
        return shareCodeRepository.findByCode(code);
    }

    /** Removes and returns the code in one step, so two clinicians cannot both redeem it. */
    public Optional<ShareCode> consume(String code) {
        return Optional.ofNullable(mongoTemplate.findAndRemove(Query.query(Criteria.where("code").is(code)), ShareCode.class));
    }

    public boolean isExpired(ShareCode code) {
        return !code.getExpiresAt().isAfter(clock.instant());
    }
}
