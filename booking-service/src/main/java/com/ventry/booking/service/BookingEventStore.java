package com.ventry.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ventry.booking.entity.Booking;
import com.ventry.booking.entity.BookingEvent;
import com.ventry.booking.projection.BookingViewProjector;
import com.ventry.booking.repository.BookingEventRepository;
import com.ventry.booking.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the current-state row (Booking), the append-only event-store row (BookingEvent), and
 * the CQRS read-model row (BookingView, via BookingViewProjector) together in one transaction,
 * so none of the three ever drift apart - e.g. a Booking left CONFIRMED with no matching
 * BOOKING_CONFIRMED event, or a read model still showing PENDING after the write side moved on.
 */
@Service
public class BookingEventStore {

    private final BookingRepository bookingRepository;
    private final BookingEventRepository bookingEventRepository;
    private final BookingViewProjector bookingViewProjector;
    private final ObjectMapper objectMapper;

    public BookingEventStore(BookingRepository bookingRepository,
                              BookingEventRepository bookingEventRepository,
                              BookingViewProjector bookingViewProjector,
                              ObjectMapper objectMapper) {
        this.bookingRepository = bookingRepository;
        this.bookingEventRepository = bookingEventRepository;
        this.bookingViewProjector = bookingViewProjector;
        this.objectMapper = objectMapper;
    }

    /**
     * Delegates to the full overload below with no event/tier name - every transition after a
     * booking's first (BOOKING_INITIATED) only ever changes the read model's status, never its
     * denormalized names, so there's nothing more to pass here.
     */
    @Transactional
    public Booking append(Booking booking, String eventType, Object payload) {
        return append(booking, eventType, payload, null, null);
    }

    /**
     * eventName/tierName are only meaningful the first time a booking's view row is created -
     * see BookingViewProjector.project(). Called directly only by BookingService.createBooking,
     * the sole call site with those names already in scope, from EventServiceClient.getTierDetails.
     */
    @Transactional
    public Booking append(Booking booking, String eventType, Object payload, String eventName, String tierName) {
        Booking saved = bookingRepository.save(booking);
        bookingEventRepository.save(new BookingEvent(booking.getBookingId(), eventType, writeJson(payload)));
        bookingViewProjector.project(saved, eventName, tierName);
        return saved;
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize booking event payload", e);
        }
    }
}
