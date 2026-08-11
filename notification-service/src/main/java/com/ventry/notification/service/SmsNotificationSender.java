package com.ventry.notification.service;

import com.ventry.notification.entity.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Mocked SMS channel - same log-instead-of-call simulation as EmailNotificationSender, kept
 * as a separate, near-identical class rather than sharing a base class: the duplication
 * itself is the point, demonstrating two genuinely distinct implementations behind
 * NotificationSender's unified interface (same reasoning already used for
 * BkashMockGateway/SslCommerzMockGateway).
 */
@Component
public class SmsNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationSender.class);

    @Override
    public Notification.Channel channel() {
        return Notification.Channel.SMS;
    }

    @Override
    public void send(String recipientId, String message) {
        log.info("[SMS] to {}: {}", recipientId, message);
    }
}
