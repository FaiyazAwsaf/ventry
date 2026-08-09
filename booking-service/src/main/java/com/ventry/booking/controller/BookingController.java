package com.ventry.booking.controller;

import com.ventry.booking.dto.BookingResponse;
import com.ventry.booking.dto.CreateBookingRequest;
import com.ventry.booking.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
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
}
