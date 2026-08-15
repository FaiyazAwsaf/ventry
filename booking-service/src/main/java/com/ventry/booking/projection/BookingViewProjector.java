package com.ventry.booking.projection;

import com.ventry.booking.entity.Booking;
import com.ventry.booking.entity.BookingView;
import com.ventry.booking.repository.BookingViewRepository;
import org.springframework.stereotype.Component;

/**
 * The CQRS projection step: keeps booking_view in sync with the write side. Called from inside
 * BookingEventStore.append()'s own transaction (never separately), so the read model can never
 * observe a state the write side hasn't actually committed - and never drifts out of sync with
 * it, unlike a projector driven by a second, independent write.
 *
 * project() always upserts, which is what makes this safe to call uniformly for every one of
 * Booking's five transitions: the first call (BOOKING_INITIATED) creates the row, every later
 * one just updates status. Redelivery itself is already guarded further upstream - Booking's
 * own confirm()/markFailed()/cancel()/completeCancellation() no-op on a redelivered Kafka event
 * before BookingEventStore.append() (and therefore this) is ever reached - but the upsert here
 * costs nothing extra and removes any need for this class to know about that guard.
 */
@Component
public class BookingViewProjector {

    private final BookingViewRepository bookingViewRepository;

    public BookingViewProjector(BookingViewRepository bookingViewRepository) {
        this.bookingViewRepository = bookingViewRepository;
    }

    /**
     * eventName/tierName are only meaningful the first time a booking's view row is created -
     * every later transition passes null here, since only status/lastUpdated change from then
     * on. See BookingEventStore, the sole caller.
     */
    public void project(Booking booking, String eventName, String tierName) {
        bookingViewRepository.findById(booking.getBookingId()).ifPresentOrElse(
                view -> {
                    view.updateStatus(booking.getStatus());
                    bookingViewRepository.save(view);
                },
                () -> bookingViewRepository.save(new BookingView(
                        booking.getBookingId(),
                        booking.getCustomerId(),
                        booking.getEventId(),
                        eventName,
                        booking.getTierId(),
                        tierName,
                        booking.getQuantity(),
                        booking.getTotalAmount(),
                        booking.getStatus()
                ))
        );
    }
}
