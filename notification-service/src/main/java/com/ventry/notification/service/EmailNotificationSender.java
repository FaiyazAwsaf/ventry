package com.ventry.notification.service;

import com.ventry.notification.entity.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Mocked email channel: logs the send instead of calling a real provider (SendGrid, SES,
 * ...). Unlike PaymentGateway's mocks, this one deliberately does log - the log line (plus
 * the persisted Notification row NotificationDispatchService writes right after) is the only
 * observable evidence a notification went out, since no downstream Kafka topic consumes
 * "notification sent" the way payment/booking events chain into the next Saga step.
 */
@Component
public class EmailNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationSender.class);

    @Override
    public Notification.Channel channel() {
        return Notification.Channel.EMAIL;
    }

    @Override
    public void send(String recipientId, String message) {
        log.info("[EMAIL] to {}: {}", recipientId, message);
    }
}
