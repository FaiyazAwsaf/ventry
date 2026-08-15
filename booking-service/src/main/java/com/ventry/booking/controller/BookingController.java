package com.ventry.booking.controller;

import com.ventry.booking.dto.BookingResponse;
import com.ventry.booking.dto.BookingViewResponse;
import com.ventry.booking.dto.CreateBookingRequest;
import com.ventry.booking.service.BookingQueryService;
import com.ventry.booking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final BookingQueryService bookingQueryService;

    public BookingController(BookingService bookingService, BookingQueryService bookingQueryService) {
        this.bookingService = bookingService;
        this.bookingQueryService = bookingQueryService;
    }

    /**
     * customerId comes from X-User-Id, set by the Gateway from the verified JWT - never
     * from the request body, since that would let a caller book on someone else's behalf.
     */
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @RequestHeader("X-User-Id") String customerId,
            @Valid @RequestBody CreateBookingRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(customerId, request));
    }

    /**
     * Same X-User-Id trust model as createBooking - ownership is enforced in
     * BookingService.cancelBooking, not here, since "does this booking belong to this
     * caller" needs the persisted booking to check against.
     */
    @PostMapping("/{bookingId}/cancel")
    public BookingResponse cancelBooking(
            @RequestHeader("X-User-Id") String customerId,
            @PathVariable String bookingId
    ) {
        return bookingService.cancelBooking(bookingId, customerId);
    }

    /**
     * The CQRS query side. Reads BookingView (see BookingQueryService), never Booking or the
     * event store. customerId comes from X-User-Id, same trust model as every write endpoint
     * above - never a query parameter, which would let a caller list anyone's history.
     * Mapped before /{bookingId} so "history" is never mistaken for a bookingId path variable
     * (Spring resolves the literal segment first regardless of declaration order, but this
     * keeps the two from reading as ambiguous).
     */
    @GetMapping("/history")
    public List<BookingViewResponse> getBookingHistory(@RequestHeader("X-User-Id") String customerId) {
        return bookingQueryService.getHistory(customerId);
    }

    /**
     * Ownership-checked in BookingQueryService.getBooking - same trust model as cancelBooking,
     * a customer can only fetch their own booking.
     */
    @GetMapping("/{bookingId}")
    public BookingViewResponse getBooking(
            @RequestHeader("X-User-Id") String customerId,
            @PathVariable String bookingId
    ) {
        return bookingQueryService.getBooking(bookingId, customerId);
    }
}
