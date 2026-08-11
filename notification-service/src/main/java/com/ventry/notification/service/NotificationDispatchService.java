package com.ventry.notification.service;

import com.ventry.notification.entity.Notification;
import com.ventry.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationDispatchService {

    private final NotificationRepository notificationRepository;
    private final List<NotificationSender> notificationSenders;

    /**
     * Injected as List<NotificationSender>, unlike payment-service's GatewaySelector (which
     * takes BkashMockGateway/SslCommerzMockGateway by concrete type because it must pick
     * exactly one, deterministically, by index). Here every sender in the list gets used on
     * every dispatch - order never matters - so Spring's classpath-scan-dependent autowiring
     * order is harmless.
     */
    public NotificationDispatchService(NotificationRepository notificationRepository,
                                        List<NotificationSender> notificationSenders) {
        this.notificationRepository = notificationRepository;
        this.notificationSenders = notificationSenders;
    }

    /**
     * Idempotent on (bookingId, type) - Kafka's at-least-once delivery (architecture.md's
     * consistency NFR) means the same event can be redelivered, and a customer must not be
     * notified twice for it. Guarded per-type rather than per-booking because a single
     * booking legitimately produces multiple distinct notification types over its lifecycle
     * (e.g. BOOKING_CONFIRMED, then later TICKET_GENERATED) - those are not redeliveries of
     * each other and must both go out.
     *
     * @Transactional wraps the exists-check and both saves in one commit: without it, a crash
     * between the EMAIL save and the SMS save would leave the (bookingId, type) guard already
     * satisfied by the EMAIL row alone, so a Kafka redelivery would skip the whole method and
     * the SMS notification would never go out. All-or-nothing per event, same reasoning
     * EventService already applies to its own multi-step inventory writes.
     */
    @Transactional
    public void dispatch(Notification.Type type, String bookingId, String recipientId, String message) {
        if (notificationRepository.existsByBookingIdAndType(bookingId, type)) {
            return;
        }

        for (NotificationSender sender : notificationSenders) {
            sender.send(recipientId, message);
            notificationRepository.save(new Notification(bookingId, recipientId, type, sender.channel(), message));
        }
    }
}
