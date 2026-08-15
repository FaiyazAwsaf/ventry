package com.ventry.booking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ventry.booking.dto.BookingReplayResponse;
import com.ventry.booking.entity.Booking;
import com.ventry.booking.entity.BookingEvent;
import com.ventry.booking.exception.BookingNotFoundException;
import com.ventry.booking.exception.BookingOwnershipException;
import com.ventry.booking.repository.BookingEventRepository;
import com.ventry.common.events.BookingInitiatedEvent;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Rebuilds a booking's state purely by replaying its booking_event_store history, in order -
 * the literal definition of Event Sourcing (architecture.md §5.4: "Current state = replaying
 * all events for a booking_id"). Deliberately independent of BookingView/BookingQueryService:
 * this proves the event log alone is sufficient to answer "what state is this booking in",
 * not merely that two systems built from the same writes happen to agree.
 */
@Service
public class BookingReplayService {

    private final BookingEventRepository bookingEventRepository;
    private final ObjectMapper objectMapper;

    public BookingReplayService(BookingEventRepository bookingEventRepository, ObjectMapper objectMapper) {
        this.bookingEventRepository = bookingEventRepository;
        this.objectMapper = objectMapper;
    }

    public BookingReplayResponse replay(String bookingId, String customerId) {
        List<BookingEvent> events = bookingEventRepository.findByBookingIdOrderByTimestampAsc(bookingId);
        if (events.isEmpty()) {
            throw new BookingNotFoundException(bookingId);
        }

        // BOOKING_INITIATED is always the first event ever appended for a booking (see
        // BookingService.createBooking) and the only one carrying its full shape - every
        // later event only ever changes status, same as BookingViewProjector.
        BookingInitiatedEvent initiated = readPayload(events.get(0), BookingInitiatedEvent.class);

        if (!initiated.customerId().equals(customerId)) {
            throw new BookingOwnershipException(bookingId);
        }

        Booking.Status status = Booking.Status.PENDING;
        for (BookingEvent event : events) {
            status = applyTransition(status, event.getEventType());
        }

        return new BookingReplayResponse(
                bookingId,
                initiated.customerId(),
                initiated.eventId(),
                initiated.tierId(),
                initiated.quantity(),
                initiated.totalAmount(),
                status.name(),
                events.size()
        );
    }

    private Booking.Status applyTransition(Booking.Status current, String eventType) {
        return switch (eventType) {
            case "BOOKING_INITIATED" -> Booking.Status.PENDING;
            case "BOOKING_CONFIRMED" -> Booking.Status.CONFIRMED;
            case "BOOKING_FAILED" -> Booking.Status.FAILED;
            case "BOOKING_CANCELLATION_REQUESTED" -> Booking.Status.CANCELLATION_PENDING;
            case "BOOKING_CANCELLED" -> Booking.Status.CANCELLED;
            // Every event type BookingService ever appends is one of the five above -
            // this only guards against a future event type being added without this
            // switch being updated to match.
            default -> current;
        };
    }

    private <T> T readPayload(BookingEvent event, Class<T> type) {
        try {
            return objectMapper.readValue(event.getPayload(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize booking event payload", e);
        }
    }
}
