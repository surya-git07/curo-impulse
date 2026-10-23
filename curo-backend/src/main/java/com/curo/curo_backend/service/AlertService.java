package com.curo.curo_backend.service;

import com.curo.curo_backend.dto.AlertView;
import com.curo.curo_backend.dto.PagedResponse;
import com.curo.curo_backend.model.Alert;
import com.curo.curo_backend.model.AlertStatus;
import com.curo.curo_backend.model.AlertType;
import com.curo.curo_backend.model.AppUser;
import com.curo.curo_backend.repository.AlertRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/** Creates alerts (stored, then pushed through the NotificationSender) and serves each user their own inbox. */
@Service
public class AlertService {

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private NotificationSender notificationSender;

    @Autowired
    private CurrentUserService currentUser;

    @Autowired
    private Clock clock;

    /** What the app shows inside, and the plain text that may leave it. */
    public record Content(String inApp, String subject, String external) {
    }

    /**
     * Stores the alert and tries to push it out. A delivery failure is recorded on the alert (FAILED) and never thrown: the
     * alert is still in the recipient inbox.
     */
    public Alert create(AppUser recipient, AlertType type, String patientCareId, String doseId, String prescriptionId, Content content) {
        Instant now = clock.instant();
        boolean sent;
        try {
            sent = notificationSender.send(new NotificationSender.Notification(type.name(), recipient.getCareId(), content.subject(), content.external()));
        } catch (RuntimeException e) {
            sent = false; // send() is not supposed to throw, but one bad sender must not lose the alert
        }
        Alert alert = new Alert();
        alert.setType(type);
        alert.setRecipientId(recipient.getId());
        alert.setPatientCareId(patientCareId);
        alert.setDoseId(doseId);
        alert.setPrescriptionId(prescriptionId);
        alert.setMessage(content.inApp());
        alert.setChannel(notificationSender.channel());
        alert.setStatus(sent ? AlertStatus.SENT : AlertStatus.FAILED);
        alert.setSentAt(now);
        alert.setCreatedAt(now);
        return alertRepository.save(alert);
    }

    // ---- the signed-in user's inbox

    public PagedResponse<AlertView> list(boolean unreadOnly, int page, int size) {
        AppUser user = currentUser.require();
        if (page < 0 || size < 1 || size > AuditService.MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "page must be 0 or more and size between 1 and " + AuditService.MAX_PAGE_SIZE + ".");
        }
        PageRequest paging = PageRequest.of(page, size);
        Page<Alert> result = unreadOnly
                ? alertRepository.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(user.getId(), paging)
                : alertRepository.findByRecipientIdOrderByCreatedAtDesc(user.getId(), paging);
        return new PagedResponse<>(page, size, result.getTotalElements(), result.getContent().stream().map(AlertService::view).toList());
    }

    public AlertView markRead(String alertId) {
        AppUser user = currentUser.require();
        // someone else's alert looks exactly like one that does not exist
        Alert alert = alertRepository.findById(alertId).filter(a -> user.getId().equals(a.getRecipientId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ALERT_NOT_FOUND", "No such alert."));
        if (alert.getReadAt() == null) {
            alert.setReadAt(clock.instant());
            alert = alertRepository.save(alert);
        }
        return view(alert);
    }

    static AlertView view(Alert a) {
        return new AlertView(a.getId(), a.getType(), a.getPatientCareId(), a.getMessage(), a.getDoseId(), a.getPrescriptionId(),
                a.getChannel(), a.getStatus(), a.getSentAt(), a.getReadAt());
    }
}
