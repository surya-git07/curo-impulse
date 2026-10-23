package com.curo.curo_backend.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationSenderTest {

    private static final String TOPIC = "arn:aws:sns:ap-south-1:000000000000:curo-alerts-test";

    private static NotificationSender.Notification note() {
        return new NotificationSender.Notification("MISSED_DOSE", "CID-7K3MQ9", "Curo: missed dose", "A dose was missed. Open Curo to see it.");
    }

    private ListAppender<ILoggingEvent> capture(Class<?> loggerOf) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        ((Logger) LoggerFactory.getLogger(loggerOf)).addAppender(appender);
        return appender;
    }

    // ---- the logging fallback

    @Test
    void theLoggingSenderWritesTheNotificationToTheLogAndReportsSuccess() {
        ListAppender<ILoggingEvent> logs = capture(LoggingNotificationSender.class);
        LoggingNotificationSender sender = new LoggingNotificationSender();

        assertThat(sender.send(note())).isTrue();
        assertThat(sender.channel()).isEqualTo("LOG");

        assertThat(logs.list).singleElement().satisfies(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.INFO);
            assertThat(e.getFormattedMessage()).contains("MISSED_DOSE", "CID-7K3MQ9", "Curo: missed dose", "not sent");
        });
    }

    @Test
    void theLoggingSenderNeverThrowsEvenWithEmptyFields() {
        assertThatCode(() -> new LoggingNotificationSender().send(new NotificationSender.Notification("X", "", "", ""))).doesNotThrowAnyException();
    }

    // ---- SNS

    @Test
    void snsPublishesToTheConfiguredTopicWithRoutingAttributes() {
        SnsClient client = mock(SnsClient.class);
        SnsNotificationSender sender = new SnsNotificationSender(client, TOPIC);

        assertThat(sender.send(note())).isTrue();
        assertThat(sender.channel()).isEqualTo("EMAIL");

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(client).publish(captor.capture());
        PublishRequest sent = captor.getValue();
        assertThat(sent.topicArn()).isEqualTo(TOPIC);
        assertThat(sent.subject()).isEqualTo("Curo: missed dose");
        assertThat(sent.message()).isEqualTo("A dose was missed. Open Curo to see it.");
        assertThat(sent.messageAttributes().get("recipientCareId").stringValue()).isEqualTo("CID-7K3MQ9");
        assertThat(sent.messageAttributes().get("alertType").stringValue()).isEqualTo("MISSED_DOSE");
        assertThat(sent.messageAttributes().get("recipientCareId").dataType()).isEqualTo("String");
    }

    @Test
    void aLongSubjectIsCutToWhatSnsAccepts() {
        SnsClient client = mock(SnsClient.class);

        new SnsNotificationSender(client, TOPIC).send(new NotificationSender.Notification("X", "CID-7K3MQ9", "s".repeat(250), "body"));

        ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
        verify(client).publish(captor.capture());
        assertThat(captor.getValue().subject()).hasSize(100);
    }

    @Test
    void aFailedPublishReturnsFalseDoesNotThrowAndLogsNoDetail() {
        SnsClient client = mock(SnsClient.class);
        when(client.publish(any(PublishRequest.class)))
                .thenThrow(SnsException.builder().message("AuthorizationError for arn:aws:sns:ap-south-1:000000000000:secret-topic").build());
        ListAppender<ILoggingEvent> logs = capture(SnsNotificationSender.class);

        boolean sent = new SnsNotificationSender(client, TOPIC).send(note());

        assertThat(sent).isFalse();
        String all = logs.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining());
        assertThat(all).contains("SnsException").doesNotContain("secret-topic", "arn:aws", "AuthorizationError");
        assertThat(logs.list).allSatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getThrowableProxy()).isNull();
        });
    }

    @Test
    void anyRuntimeFailureIsContained() {
        SnsClient client = mock(SnsClient.class);
        when(client.publish(any(PublishRequest.class))).thenThrow(new IllegalStateException("boom"));

        assertThat(new SnsNotificationSender(client, TOPIC).send(note())).isFalse();
    }

    // ---- choosing between them

    @Test
    void withoutATopicTheLoggingSenderIsUsed() {
        NotificationConfig config = new NotificationConfig();

        assertThat(config.notificationSender("", "")).isInstanceOf(LoggingNotificationSender.class);
        assertThat(config.notificationSender("   ", "ap-south-1")).isInstanceOf(LoggingNotificationSender.class);
        assertThat(config.notificationSender(null, null)).isInstanceOf(LoggingNotificationSender.class);
    }

    @Test
    void withATopicSnsIsUsedAndNoCredentialsAreNeededToBuildIt() {
        NotificationConfig config = new NotificationConfig();

        NotificationSender sender = config.notificationSender(TOPIC, "ap-south-1");

        assertThat(sender).isInstanceOf(SnsNotificationSender.class);
        assertThat(sender.channel()).isEqualTo("EMAIL");
    }

    @Test
    void theRegionIsOptional() {
        // with no region given the SDK looks for one itself; if it cannot find one the app falls back to logging instead of failing to start
        NotificationSender sender = new NotificationConfig().notificationSender(TOPIC, "");

        assertThat(sender).isInstanceOfAny(SnsNotificationSender.class, LoggingNotificationSender.class);
    }

    @Test
    void theFallbackIsAnnouncedInTheLog() {
        ListAppender<ILoggingEvent> logs = capture(NotificationConfig.class);

        new NotificationConfig().notificationSender("", "");

        assertThat(logs.list).singleElement().satisfies(e -> assertThat(e.getFormattedMessage()).contains("ALERTS_SNS_TOPIC_ARN is not set"));
    }

    @Test
    void theTopicNameNeverAppearsInTheLog() {
        ListAppender<ILoggingEvent> logs = capture(NotificationConfig.class);

        new NotificationConfig().notificationSender(TOPIC, "ap-south-1");

        assertThat(logs.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining())).doesNotContain("arn:aws", "curo-alerts-test");
    }
}
