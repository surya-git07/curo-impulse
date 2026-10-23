package com.curo.curo_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

/**
 * Chooses the sender. SNS is used only when ALERTS_SNS_TOPIC_ARN is set; AWS_REGION is optional (the SDK can find the region
 * itself) and credentials are never configured here. Anything else falls back to logging, so the app still runs.
 */
@Configuration
public class NotificationConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationConfig.class);

    @Bean
    public NotificationSender notificationSender(@Value("${alerts.sns.topic-arn:}") String topicArn,
                                                 @Value("${aws.region:}") String region) {
        if (topicArn == null || topicArn.isBlank()) {
            log.info("ALERTS_SNS_TOPIC_ARN is not set: notifications go to the log only");
            return new LoggingNotificationSender();
        }
        try {
            var builder = SnsClient.builder();
            if (region != null && !region.isBlank()) {
                builder.region(Region.of(region.trim()));
            }
            log.info("notifications go out through Amazon SNS");
            return new SnsNotificationSender(builder.build(), topicArn.trim());
        } catch (RuntimeException e) {
            // e.g. no region could be found: keep running rather than fail to start
            log.warn("SNS could not be set up ({}); notifications go to the log only", e.getClass().getSimpleName());
            return new LoggingNotificationSender();
        }
    }
}
