package com.ventry.notification.service;

import com.ventry.notification.entity.Notification;
import com.ventry.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real dispatch -> Postgres path (real NotificationRepository, real
 * Email/SmsNotificationSender beans, no mocks) - the "primary flow" integration test every
 * service in this project is expected to have. No live Kafka involved, same documented gap as
 * every other service's IT (BookingControllerIT etc.): dispatch() is called directly rather
 * than publishing to a real topic and waiting for a listener to consume it.
 *
 * Each test uses a fresh random bookingId rather than asserting on repository counts, since the
 * shared local Postgres instance may already hold rows from other test runs or manual demo use
 * that @Transactional rollback here would never have touched.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class NotificationDispatchServiceIT {

    @Autowired
    private NotificationDispatchService notificationDispatchService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void dispatch_persistsOneRowPerChannel() {
        String bookingId = "it-booking-" + UUID.randomUUID();

        notificationDispatchService.dispatch(Notification.Type.BOOKING_CONFIRMED, bookingId, "customer-1",
                "Your booking " + bookingId + " is confirmed!");

        List<Notification> saved = findByBookingId(bookingId);

        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(Notification::getChannel)
                .containsExactlyInAnyOrder(Notification.Channel.EMAIL, Notification.Channel.SMS);
        assertThat(saved).allSatisfy(notification -> {
            assertThat(notification.getRecipientId()).isEqualTo("customer-1");
            assertThat(notification.getType()).isEqualTo(Notification.Type.BOOKING_CONFIRMED);
            assertThat(notification.getMessage()).isEqualTo("Your booking " + bookingId + " is confirmed!");
        });
    }

    @Test
    void dispatch_isIdempotentAtTheDatabaseLevelOnRedeliveredEvent() {
        String bookingId = "it-booking-" + UUID.randomUUID();

        notificationDispatchService.dispatch(Notification.Type.BOOKING_CONFIRMED, bookingId, "customer-1", "first delivery");
        notificationDispatchService.dispatch(Notification.Type.BOOKING_CONFIRMED, bookingId, "customer-1", "redelivered");

        List<Notification> saved = findByBookingId(bookingId);

        assertThat(saved).hasSize(2);
        assertThat(saved).allSatisfy(notification -> assertThat(notification.getMessage()).isEqualTo("first delivery"));
    }

    @Test
    void dispatch_withDifferentTypesForSameBookingPersistsBoth() {
        String bookingId = "it-booking-" + UUID.randomUUID();

        notificationDispatchService.dispatch(Notification.Type.BOOKING_CONFIRMED, bookingId, "customer-1", "confirmed");
        notificationDispatchService.dispatch(Notification.Type.TICKET_GENERATED, bookingId, "customer-1", "ticket ready");

        List<Notification> saved = findByBookingId(bookingId);

        assertThat(saved).hasSize(4);
        assertThat(saved).extracting(Notification::getType)
                .containsExactlyInAnyOrder(
                        Notification.Type.BOOKING_CONFIRMED, Notification.Type.BOOKING_CONFIRMED,
                        Notification.Type.TICKET_GENERATED, Notification.Type.TICKET_GENERATED);
    }

    private List<Notification> findByBookingId(String bookingId) {
        return notificationRepository.findAll().stream()
                .filter(notification -> notification.getBookingId().equals(bookingId))
                .toList();
    }
}
