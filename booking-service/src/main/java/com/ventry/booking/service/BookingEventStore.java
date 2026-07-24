package com.ventry.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ventry.booking.entity.Booking;
import com.ventry.booking.entity.BookingEvent;
import com.ventry.booking.repository.BookingEventRepository;
import com.ventry.booking.repository.BookingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the current-state row (Booking) and the append-only event-store row (BookingEvent)
 * together in one transaction, so the two never drift apart - e.g. a Booking left CONFIRMED
 * with no matching BOOKING_CONFIRMED event, or vice versa.
 */
@Service
public class BookingEventStore {

    private final BookingRepository bookingRepository;
    private final BookingEventRepository bookingEventRepository;
    private final ObjectMapper objectMapper;

    public BookingEventStore(BookingRepository bookingRepository,
                              BookingEventRepository bookingEventRepository,
                              ObjectMapper objectMapper) {
        this.bookingRepository = bookingRepository;
        this.bookingEventRepository = bookingEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Booking append(Booking booking, String eventType, Object payload) {
        Booking saved = bookingRepository.save(booking);
        bookingEventRepository.save(new BookingEvent(booking.getBookingId(), eventType, writeJson(payload)));
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
