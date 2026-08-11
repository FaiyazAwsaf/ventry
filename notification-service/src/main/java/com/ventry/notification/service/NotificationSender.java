package com.ventry.notification.service;

import com.ventry.notification.entity.Notification;

/**
 * Unified interface abstracting both mock delivery channels (email, SMS), same role
 * PaymentGateway plays for bKash/SSLCommerz in payment-service - NotificationDispatchService
 * talks to this interface only, never to a concrete sender, so a real provider integration
 * could later replace either implementation without touching any calling code.
 */
public interface NotificationSender {

    /**
     * Self-reported channel, mirrors PaymentGateway.name() - lets the dispatcher iterate
     * senders and know which Notification.Channel each one represents without reflecting on
     * the concrete class.
     */
    Notification.Channel channel();

    void send(String recipientId, String message);
}
