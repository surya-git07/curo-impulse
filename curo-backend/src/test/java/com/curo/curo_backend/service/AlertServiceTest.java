package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.AlertView;
import com.curo.curo_backend.dto.PagedResponse;
import com.curo.curo_backend.model.Alert;
import com.curo.curo_backend.model.AlertStatus;
import com.curo.curo_backend.model.AlertType;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.repository.AlertRepository;
import com.curo.curo_backend.repository.UserRepository;
import com.curo.curo_backend.support.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static com.curo.curo_backend.support.Fixtures.signIn;
import static com.curo.curo_backend.support.Fixtures.signOut;
import static com.curo.curo_backend.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlertServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-23T10:00:00Z");

    private final MutableClock clock = new MutableClock(T0);
    private final List<Alert> alerts = new ArrayList<>();
    private final List<NotificationSender.Notification> sent = new ArrayList<>();
    private final AppUser patient = user("p1", "PATIENT", "Test Patient", "CID-7K3MQ9");
    private final AppUser doctor = user("d1", "DOCTOR", "Dr Test", "CID-4HN8XD");
    private boolean senderSucceeds = true;
    private boolean senderThrows = false;
    private AlertService service;

    @BeforeEach
    void setUp() {
        AlertRepository repo = mock(AlertRepository.class);
        when(repo.save(any(Alert.class))).thenAnswer(inv -> {
            Alert a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId("a" + (alerts.size() + 1));
                alerts.add(a);
            }
            return a;
        });
        when(repo.findById(anyString())).thenAnswer(inv -> alerts.stream().filter(a -> a.getId().equals(inv.getArgument(0))).findFirst());
        when(repo.findByRecipientIdOrderByCreatedAtDesc(anyString(), any(Pageable.class))).thenAnswer(inv -> page(inv.getArgument(0), false, inv.getArgument(1)));
        when(repo.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(anyString(), any(Pageable.class))).thenAnswer(inv -> page(inv.getArgument(0), true, inv.getArgument(1)));

        NotificationSender sender = new NotificationSender() {
            @Override
            public String channel() {
                return "EMAIL";
            }

            @Override
            public boolean send(Notification n) {
                if (senderThrows) {
                    throw new IllegalStateException("sender exploded");
                }
                sent.add(n);
                return senderSucceeds;
            }
        };

        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.findById("p1")).thenReturn(Optional.of(patient));
        when(userRepo.findById("d1")).thenReturn(Optional.of(doctor));
        CurrentUserService currentUser = new CurrentUserService();
        ReflectionTestUtils.setField(currentUser, "userRepository", userRepo);

        service = new AlertService();
        ReflectionTestUtils.setField(service, "alertRepository", repo);
        ReflectionTestUtils.setField(service, "notificationSender", sender);
        ReflectionTestUtils.setField(service, "currentUser", currentUser);
        ReflectionTestUtils.setField(service, "clock", clock);
        signIn(patient);
    }

    @AfterEach
    void tearDown() {
        signOut();
    }

    private Page<Alert> page(String recipientId, boolean unreadOnly, Pageable pageable) {
        List<Alert> all = alerts.stream().filter(a -> a.getRecipientId().equals(recipientId) && (!unreadOnly || a.getReadAt() == null))
                .sorted(Comparator.comparing(Alert::getCreatedAt).reversed().thenComparing(Alert::getId, Comparator.reverseOrder())).toList();
        int from = (int) Math.min(pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to), pageable, all.size());
    }

    private Alert create(AppUser to, AlertType type) {
        return service.create(to, type, "CID-7K3MQ9", "dose1", "rx1",
                new AlertService.Content("Warfarin 5 mg missed at 08:00.", "Curo: missed dose", "A dose was missed. Open Curo to see it."));
    }

    private void assertApiError(Supplier<?> call, HttpStatus status, String code) {
        assertThatThrownBy(call::get).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    // ---- creating

    @Test
    void anAlertIsStoredWithTheContractFieldsAndSentThroughTheSender() {
        Alert a = create(patient, AlertType.MISSED_DOSE);

        assertThat(a.getType()).isEqualTo(AlertType.MISSED_DOSE);
        assertThat(a.getRecipientId()).isEqualTo("p1");
        assertThat(a.getChannel()).isEqualTo("EMAIL");
        assertThat(a.getDoseId()).isEqualTo("dose1");
        assertThat(a.getPrescriptionId()).isEqualTo("rx1");
        assertThat(a.getPatientCareId()).isEqualTo("CID-7K3MQ9");
        assertThat(a.getStatus()).isEqualTo(AlertStatus.SENT);
        assertThat(a.getSentAt()).isEqualTo(T0);
        assertThat(a.getReadAt()).isNull();
        assertThat(a.getMessage()).contains("Warfarin");
        assertThat(sent).singleElement().satisfies(n -> {
            assertThat(n.type()).isEqualTo("MISSED_DOSE");
            assertThat(n.recipientCareId()).isEqualTo("CID-7K3MQ9");
            assertThat(n.body()).doesNotContain("Warfarin");
        });
    }

    @Test
    void theTextThatLeavesTheAppIsNotTheTextInTheInbox() {
        create(patient, AlertType.UPCOMING_DOSE);

        assertThat(sent.get(0).subject()).doesNotContain("Warfarin");
        assertThat(alerts.get(0).getMessage()).isNotEqualTo(sent.get(0).body());
    }

    @Test
    void aDeliveryFailureIsRecordedOnTheAlertAndStillKept() {
        senderSucceeds = false;

        Alert a = create(patient, AlertType.MISSED_DOSE);

        assertThat(a.getStatus()).isEqualTo(AlertStatus.FAILED);
        assertThat(alerts).hasSize(1);
        assertThat(service.list(false, 0, 20).getItems()).singleElement().satisfies(v -> assertThat(v.getStatus()).isEqualTo(AlertStatus.FAILED));
    }

    @Test
    void aSenderThatThrowsCannotLoseTheAlert() {
        senderThrows = true;

        Alert a = create(patient, AlertType.ADHERENCE);

        assertThat(a.getStatus()).isEqualTo(AlertStatus.FAILED);
        assertThat(alerts).hasSize(1);
    }

    // ---- the inbox

    @Test
    void eachUserSeesOnlyTheirOwnAlertsNewestFirst() {
        create(patient, AlertType.UPCOMING_DOSE);
        clock.advance(Duration.ofMinutes(1));
        create(doctor, AlertType.MISSED_DOSE);
        clock.advance(Duration.ofMinutes(1));
        create(patient, AlertType.MISSED_DOSE);

        PagedResponse<AlertView> mine = service.list(false, 0, 20);

        assertThat(mine.getTotal()).isEqualTo(2);
        assertThat(mine.getItems()).extracting(AlertView::getType).containsExactly(AlertType.MISSED_DOSE, AlertType.UPCOMING_DOSE);

        signIn(doctor);
        assertThat(service.list(false, 0, 20).getItems()).singleElement().satisfies(v -> assertThat(v.getType()).isEqualTo(AlertType.MISSED_DOSE));
    }

    @Test
    void theViewCarriesTheFieldsAClientNeeds() {
        create(patient, AlertType.MISSED_DOSE);

        AlertView v = service.list(false, 0, 20).getItems().get(0);

        assertThat(v.getId()).isEqualTo("a1");
        assertThat(v.getPatientCareId()).isEqualTo("CID-7K3MQ9");
        assertThat(v.getMessage()).isNotBlank();
        assertThat(v.getDoseId()).isEqualTo("dose1");
        assertThat(v.getPrescriptionId()).isEqualTo("rx1");
        assertThat(v.getChannel()).isEqualTo("EMAIL");
        assertThat(v.getStatus()).isEqualTo(AlertStatus.SENT);
        assertThat(v.getSentAt()).isEqualTo(T0);
        assertThat(v.getReadAt()).isNull();
    }

    @Test
    void unreadOnlyHidesWhatWasRead() {
        create(patient, AlertType.UPCOMING_DOSE);
        clock.advance(Duration.ofMinutes(1));
        create(patient, AlertType.MISSED_DOSE);
        service.markRead("a1");

        assertThat(service.list(true, 0, 20).getItems()).extracting(AlertView::getId).containsExactly("a2");
        assertThat(service.list(false, 0, 20).getTotal()).isEqualTo(2);
    }

    @Test
    void pagingWorksAndIsValidated() {
        for (int i = 0; i < 5; i++) {
            clock.advance(Duration.ofMinutes(1));
            create(patient, AlertType.UPCOMING_DOSE);
        }

        PagedResponse<AlertView> second = service.list(false, 1, 2);
        assertThat(second.getTotal()).isEqualTo(5);
        assertThat(second.getItems()).hasSize(2);
        assertThat(second.getPage()).isEqualTo(1);

        assertApiError(() -> service.list(false, -1, 20), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertApiError(() -> service.list(false, 0, 0), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertApiError(() -> service.list(false, 0, 101), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }

    @Test
    void markingReadSetsTheTimeOnceAndOnlyOnYourOwnAlerts() {
        create(patient, AlertType.MISSED_DOSE);
        create(doctor, AlertType.MISSED_DOSE);
        clock.advance(Duration.ofMinutes(5));

        AlertView first = service.markRead("a1");
        clock.advance(Duration.ofMinutes(5));
        AlertView again = service.markRead("a1");

        assertThat(first.getReadAt()).isEqualTo(T0.plus(Duration.ofMinutes(5)));
        assertThat(again.getReadAt()).as("the first read time is kept").isEqualTo(first.getReadAt());
        assertApiError(() -> service.markRead("a2"), HttpStatus.NOT_FOUND, "ALERT_NOT_FOUND");
        assertApiError(() -> service.markRead("nope"), HttpStatus.NOT_FOUND, "ALERT_NOT_FOUND");
        assertThat(alerts.get(1).getReadAt()).as("someone else alert untouched").isNull();
    }

    @Test
    void notSignedInIs401() {
        signOut();
        assertApiError(() -> service.list(false, 0, 20), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        assertApiError(() -> service.markRead("a1"), HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
    }
}
