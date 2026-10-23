package com.curo.curo_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;

/**
 * Publishes to one Amazon SNS topic whose email subscribers receive the message. Each message carries recipientCareId and
 * alertType as message attributes, so a subscription filter policy can route it to the right person's email. Credentials come
 * from the standard AWS provider chain (environment variables or an instance role), never from this code.
 */
public class SnsNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SnsNotificationSender.class);
    private static final int MAX_SUBJECT = 100; // SNS limit for email subjects

    private final SnsClient client;
    private final String topicArn;

    public SnsNotificationSender(SnsClient client, String topicArn) {
        this.client = client;
        this.topicArn = topicArn;
    }

    @Override
    public String channel() {
        return "EMAIL";
    }

    @Override
    public boolean send(Notification n) {
        try {
            String subject = n.subject().length() > MAX_SUBJECT ? n.subject().substring(0, MAX_SUBJECT) : n.subject();
            client.publish(PublishRequest.builder()
                    .topicArn(topicArn)
                    .subject(subject)
                    .message(n.body())
                    .messageAttributes(Map.of(
                            "recipientCareId", attribute(n.recipientCareId()),
                            "alertType", attribute(n.type())))
                    .build());
            return true;
        } catch (RuntimeException e) {
            // exception type only: SDK messages can include request detail
            log.warn("SNS publish failed ({})", e.getClass().getSimpleName());
            return false;
        }
    }

    private static MessageAttributeValue attribute(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }
}
