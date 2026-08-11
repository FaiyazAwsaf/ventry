package com.ventry.notification.kafka;

import com.ventry.common.events.RefundProcessedEvent;
import com.ventry.notification.entity.Notification;
import com.ventry.notification.service.NotificationDispatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundProcessedListenerTest {

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @InjectMocks
    private RefundProcessedListener listener;

    @Test
    void handleRefundProcessed_dispatchesRefundProcessedNotification() {
        RefundProcessedEvent event = new RefundProcessedEvent("booking-1", "customer-1");

        listener.handleRefundProcessed(event);

        verify(notificationDispatchService).dispatch(Notification.Type.REFUND_PROCESSED, "booking-1", "customer-1",
                "Your refund for booking booking-1 has been processed.");
    }
}
