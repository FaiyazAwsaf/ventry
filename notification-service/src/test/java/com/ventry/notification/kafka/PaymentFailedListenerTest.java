package com.ventry.notification.kafka;

import com.ventry.common.events.PaymentFailedEvent;
import com.ventry.notification.entity.Notification;
import com.ventry.notification.service.NotificationDispatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentFailedListenerTest {

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @InjectMocks
    private PaymentFailedListener listener;

    @Test
    void handlePaymentFailed_dispatchesPaymentFailedNotification() {
        PaymentFailedEvent event = new PaymentFailedEvent("booking-2", "customer-1", "Payment declined by BKASH");

        listener.handlePaymentFailed(event);

        verify(notificationDispatchService).dispatch(Notification.Type.PAYMENT_FAILED, "booking-2", "customer-1",
                "Your payment for booking booking-2 failed: Payment declined by BKASH");
    }
}
