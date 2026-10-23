package com.curo.curo_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Used when SNS is not configured (local development, tests): writes the notification to the log instead of sending it. */
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public String channel() {
        return "LOG";
    }

    @Override
    public boolean send(Notification n) {
        log.info("notification (not sent, SNS not configured) [{}] to {}: {} - {}", n.type(), n.recipientCareId(), n.subject(), n.body());
        return true;
    }
}
