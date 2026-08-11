package com.ventry.notification.service;

import com.ventry.notification.entity.Notification;
import com.ventry.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationSender emailSender;

    @Mock
    private NotificationSender smsSender;

    private NotificationDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        dispatchService = new NotificationDispatchService(notificationRepository, List.of(emailSender, smsSender));
    }

    @Test
    void dispatch_sendsToAllChannelsAndPersistsWhenNotAlreadySent() {
        when(notificationRepository.existsByBookingIdAndType("booking-1", Notification.Type.BOOKING_CONFIRMED))
                .thenReturn(false);
        when(emailSender.channel()).thenReturn(Notification.Channel.EMAIL);
        when(smsSender.channel()).thenReturn(Notification.Channel.SMS);

        dispatchService.dispatch(Notification.Type.BOOKING_CONFIRMED, "booking-1", "customer-1", "Your booking is confirmed!");

        verify(emailSender).send("customer-1", "Your booking is confirmed!");
        verify(smsSender).send("customer-1", "Your booking is confirmed!");

        verify(notificationRepository).save(argThat(notification ->
                notification.getBookingId().equals("booking-1")
                        && notification.getRecipientId().equals("customer-1")
                        && notification.getType() == Notification.Type.BOOKING_CONFIRMED
                        && notification.getChannel() == Notification.Channel.EMAIL
                        && notification.getMessage().equals("Your booking is confirmed!")
        ));
        verify(notificationRepository).save(argThat(notification ->
                notification.getChannel() == Notification.Channel.SMS
        ));
        verify(notificationRepository, times(2)).save(any());
    }

    @Test
    void dispatch_isIdempotentOnRedeliveredEvent() {
        when(notificationRepository.existsByBookingIdAndType("booking-1", Notification.Type.BOOKING_CONFIRMED))
                .thenReturn(true);

        dispatchService.dispatch(Notification.Type.BOOKING_CONFIRMED, "booking-1", "customer-1", "Your booking is confirmed!");

        verifyNoInteractions(emailSender, smsSender);
        verify(notificationRepository, never()).save(any());
    }
}
