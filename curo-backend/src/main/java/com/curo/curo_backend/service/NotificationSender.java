package com.curo.curo_backend.service;

/**
 * The one way the app sends a message outside itself. Everything that needs to notify someone depends on this interface,
 * so the delivery service can change (SNS today, something else later) without touching alert logic.
 */
public interface NotificationSender {

    /**
     * What goes out. Keep it free of patient detail: no drug names, no patient names. The full text is in the app.
     *
     * @param type            the alert type, e.g. MISSED_DOSE
     * @param recipientCareId who it is for, so delivery can route it (an SNS subscription filter matches on this)
     */
    record Notification(String type, String recipientCareId, String subject, String body) {
    }

    /** Short name of how this sender delivers, stored on the alert: EMAIL or LOG. */
    String channel();

    /** @return true if the message was handed to the delivery service; false if it could not be. Must not throw. */
    boolean send(Notification notification);
}
