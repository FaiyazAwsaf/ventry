package com.ventry.booking.service;

import com.ventry.booking.dto.BookingViewResponse;
import com.ventry.booking.entity.BookingView;
import com.ventry.booking.exception.BookingNotFoundException;
import com.ventry.booking.exception.BookingOwnershipException;
import com.ventry.booking.repository.BookingViewRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The CQRS query side. Every read here goes through BookingViewRepository - the denormalized
 * read model BookingViewProjector keeps in sync - never BookingRepository or the event store.
 * Kept as its own class, separate from BookingService (the command side), so the command/query
 * split this milestone demonstrates is visible in the code itself, not just the DB schema.
 */
@Service
public class BookingQueryService {

    private final BookingViewRepository bookingViewRepository;

    public BookingQueryService(BookingViewRepository bookingViewRepository) {
        this.bookingViewRepository = bookingViewRepository;
    }

    /**
     * Ownership-checked the same way BookingService.cancelBooking is - a customer can only
     * ever look up their own booking, never someone else's by guessing a bookingId.
     */
    public BookingViewResponse getBooking(String bookingId, String customerId) {
        BookingView view = bookingViewRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (!view.getCustomerId().equals(customerId)) {
            throw new BookingOwnershipException(bookingId);
        }

        return toResponse(view);
    }

    /**
     * customerId always comes from the caller's verified X-User-Id header (see
     * BookingController) - a query-param customerId, as an earlier draft of this feature
     * sketched, would let any caller read anyone's booking history just by supplying a
     * different id.
     */
    public List<BookingViewResponse> getHistory(String customerId) {
        return bookingViewRepository.findByCustomerIdOrderByLastUpdatedDesc(customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    private BookingViewResponse toResponse(BookingView view) {
        return new BookingViewResponse(
                view.getBookingId(),
                view.getCustomerId(),
                view.getEventId(),
                view.getEventName(),
                view.getTierId(),
                view.getTierName(),
                view.getQuantity(),
                view.getTotalAmount(),
                view.getStatus().name(),
                view.getLastUpdated()
        );
    }
}
