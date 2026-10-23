package com.curo.curo_backend.service;

import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.model.GrantScope;
import com.curo.curo_backend.model.ShareCode;
import com.curo.curo_backend.repository.ShareCodeRepository;
import com.curo.curo_backend.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.curo.curo_backend.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShareCodeServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-23T10:00:00Z");

    private final MutableClock clock = new MutableClock(T0);
    private final AppUser patient = user("p1", "PATIENT", "Test Patient", "CID-7K3MQ9");
    private ShareCodeRepository repo;
    private MongoTemplate template;
    private ShareCodeService service;

    @BeforeEach
    void setUp() {
        repo = mock(ShareCodeRepository.class);
        template = mock(MongoTemplate.class);
        when(repo.save(any(ShareCode.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new ShareCodeService();
        ReflectionTestUtils.setField(service, "validMinutes", 15);
        ReflectionTestUtils.setField(service, "shareCodeRepository", repo);
        ReflectionTestUtils.setField(service, "mongoTemplate", template);
        ReflectionTestUtils.setField(service, "clock", clock);
    }

    @Test
    void codesAreSixDigitsIncludingLeadingZeros() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 3000; i++) {
            String code = service.create(patient, List.of(GrantScope.PROFILE), 24).getCode();
            assertThat(code).matches("^[0-9]{6}$");
            seen.add(code);
        }
        assertThat(seen.size()).as("not constant").isGreaterThan(2900);
        assertThat(seen).anyMatch(c -> c.startsWith("0"));
    }

    @Test
    void codeCarriesPatientScopeDurationAndExpiry() {
        ShareCode code = service.create(patient, List.of(GrantScope.PROFILE, GrantScope.DOSES), 6);

        assertThat(code.getPatientId()).isEqualTo("p1");
        assertThat(code.getScope()).containsExactly(GrantScope.PROFILE, GrantScope.DOSES);
        assertThat(code.getDurationHours()).isEqualTo(6);
        assertThat(code.getCreatedAt()).isEqualTo(T0);
        assertThat(code.getExpiresAt()).isEqualTo(T0.plus(Duration.ofMinutes(15)));
    }

    @Test
    void aCollisionWithALiveCodeDrawsAgain() {
        when(repo.save(any(ShareCode.class))).thenThrow(new DuplicateKeyException("code")).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.create(patient, List.of(GrantScope.PROFILE), 24).getCode()).matches("^[0-9]{6}$");
        verify(repo, times(2)).save(any(ShareCode.class));
    }

    @Test
    void givesUpAfterTenCollisions() {
        when(repo.save(any(ShareCode.class))).thenThrow(new DuplicateKeyException("code"));

        assertThatThrownBy(() -> service.create(patient, List.of(GrantScope.PROFILE), 24))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("SHARE_CODE_UNAVAILABLE"));
        verify(repo, times(10)).save(any(ShareCode.class));
    }

    @Test
    void consumeRemovesByCodeInOneStep() {
        ShareCode stored = new ShareCode();
        stored.setCode("123456");
        when(template.findAndRemove(any(Query.class), eq(ShareCode.class))).thenReturn(stored);

        assertThat(service.consume("123456")).containsSame(stored);
        verify(template).findAndRemove(any(Query.class), eq(ShareCode.class));
    }

    @Test
    void consumeOfAMissingCodeIsEmpty() {
        assertThat(service.consume("123456")).isEmpty();
    }

    @Test
    void expiryIsInclusiveOfTheFinalInstant() {
        ShareCode code = service.create(patient, List.of(GrantScope.PROFILE), 24);

        assertThat(service.isExpired(code)).isFalse();
        clock.advance(Duration.ofMinutes(15).minusSeconds(1));
        assertThat(service.isExpired(code)).isFalse();
        clock.advance(Duration.ofSeconds(1));
        assertThat(service.isExpired(code)).isTrue();
    }

    @Test
    void wellFormedMeansExactlySixDigits() {
        assertThat(ShareCodeService.isWellFormed("012345")).isTrue();
        assertThat(ShareCodeService.isWellFormed("12345")).isFalse();
        assertThat(ShareCodeService.isWellFormed("1234567")).isFalse();
        assertThat(ShareCodeService.isWellFormed("12345a")).isFalse();
        assertThat(ShareCodeService.isWellFormed(" 12345")).isFalse();
        assertThat(ShareCodeService.isWellFormed(null)).isFalse();
    }
}
