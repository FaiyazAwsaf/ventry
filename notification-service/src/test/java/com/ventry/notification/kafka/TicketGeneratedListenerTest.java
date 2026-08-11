package com.ventry.notification.kafka;

import com.ventry.common.events.TicketGeneratedEvent;
import com.ventry.notification.entity.Notification;
import com.ventry.notification.service.NotificationDispatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TicketGeneratedListenerTest {

    @Mock
    private NotificationDispatchService notificationDispatchService;

    @InjectMocks
    private TicketGeneratedListener listener;

    @Test
    void handleTicketGenerated_dispatchesTicketGeneratedNotification() {
        TicketGeneratedEvent event = new TicketGeneratedEvent("booking-1", "customer-1");

        listener.handleTicketGenerated(event);

        verify(notificationDispatchService).dispatch(Notification.Type.TICKET_GENERATED, "booking-1", "customer-1",
                "Your ticket for booking booking-1 is ready.");
    }
}
