package com.ventry.booking.service;

import com.ventry.booking.client.EventServiceClient;
import com.ventry.booking.dto.BookingResponse;
import com.ventry.booking.dto.CreateBookingRequest;
import com.ventry.booking.entity.Booking;
import com.ventry.booking.exception.BookingNotFoundException;
import com.ventry.booking.kafka.BookingEventProducer;
import com.ventry.booking.repository.BookingRepository;
import com.ventry.common.events.BookingConfirmedEvent;
import com.ventry.common.events.BookingInitiatedEvent;
import com.ventry.common.events.PaymentFailedEvent;
import com.ventry.common.events.PaymentSuccessEvent;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class BookingService {

    private static final String BOOKING_INITIATED = "BOOKING_INITIATED";
    private static final String BOOKING_CONFIRMED = "BOOKING_CONFIRMED";
    private static final String BOOKING_FAILED = "BOOKING_FAILED";

    private final EventServiceClient eventServiceClient;
    private final BookingRepository bookingRepository;
    private final BookingEventStore bookingEventStore;
    private final BookingEventProducer bookingEventProducer;

    public BookingService(EventServiceClient eventServiceClient,
                           BookingRepository bookingRepository,
                           BookingEventStore bookingEventStore,
                           BookingEventProducer bookingEventProducer) {
        this.eventServiceClient = eventServiceClient;
        this.bookingRepository = bookingRepository;
        this.bookingEventStore = bookingEventStore;
        this.bookingEventProducer = bookingEventProducer;
    }

    /**
     * The trigger for the entire Saga. If reserveInventory fails (tier unavailable or
     * doesn't exist), this throws before anything is persisted or published - a rejected
     * booking never enters the saga at all, so there's nothing to compensate.
     */
    public BookingResponse createBooking(String customerId, CreateBookingRequest request) {
        BigDecimal unitPrice = eventServiceClient.getTierPrice(request.eventId(), request.tierId());
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(request.quantity()));

        eventServiceClient.reserveInventory(request.eventId(), request.tierId(), request.quantity());

        Booking booking = new Booking(
                customerId, request.eventId(), request.tierId(), request.quantity(), totalAmount
        );
        BookingInitiatedEvent event = new BookingInitiatedEvent(
                booking.getBookingId(), customerId, request.eventId(), request.tierId(),
                request.quantity(), totalAmount
        );

        bookingEventStore.append(booking, BOOKING_INITIATED, event);
        bookingEventProducer.publishBookingInitiated(event);

        return toResponse(booking);
    }

    /**
     * Consumes payment.success. Guarded by Booking.confirm()'s idempotency check - a
     * redelivered event for an already-CONFIRMED/FAILED booking is a silent no-op, so
     * neither the event-store row nor the booking.confirmed publish happen twice.
     */
    public void confirmBooking(PaymentSuccessEvent event) {
        Booking booking = findBookingOrThrow(event.bookingId());

        if (!booking.confirm()) {
            return;
        }

        BookingConfirmedEvent confirmedEvent = new BookingConfirmedEvent(
                booking.getBookingId(), booking.getCustomerId(), booking.getEventId(),
                booking.getTierId(), booking.getQuantity(), booking.getTotalAmount()
        );

        bookingEventStore.append(booking, BOOKING_CONFIRMED, confirmedEvent);
        bookingEventProducer.publishBookingConfirmed(confirmedEvent);
    }

    /**
     * Consumes payment.failed - the same idempotency guard as confirmBooking also stops
     * the compensating releaseInventory REST call from firing twice on a redelivery.
     * No further Kafka publish here: payment.failed itself already reaches Notification
     * Service directly (see architecture.md's Kafka topic table), so Booking Service
     * doesn't need to republish anything for the failure branch.
     */
    public void failBooking(PaymentFailedEvent event) {
        Booking booking = findBookingOrThrow(event.bookingId());

        if (!booking.markFailed()) {
            return;
        }

        bookingEventStore.append(booking, BOOKING_FAILED, event);
        eventServiceClient.releaseInventory(booking.getEventId(), booking.getTierId(), booking.getQuantity());
    }

    private Booking findBookingOrThrow(String bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    private BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getBookingId(),
                booking.getCustomerId(),
                booking.getEventId(),
                booking.getTierId(),
                booking.getQuantity(),
                booking.getTotalAmount(),
                booking.getStatus().name()
        );
    }
}
