package com.ventry.booking.service;

import com.ventry.booking.client.EventServiceClient;
import com.ventry.booking.dto.BookingResponse;
import com.ventry.booking.dto.CreateBookingRequest;
import com.ventry.booking.entity.Booking;
import com.ventry.booking.kafka.BookingEventProducer;
import com.ventry.common.events.BookingInitiatedEvent;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class BookingService {

    private static final String BOOKING_INITIATED = "BOOKING_INITIATED";

    private final EventServiceClient eventServiceClient;
    private final BookingEventStore bookingEventStore;
    private final BookingEventProducer bookingEventProducer;

    public BookingService(EventServiceClient eventServiceClient,
                           BookingEventStore bookingEventStore,
                           BookingEventProducer bookingEventProducer) {
        this.eventServiceClient = eventServiceClient;
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
