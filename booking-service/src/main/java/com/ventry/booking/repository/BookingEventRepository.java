package com.ventry.booking.repository;

import com.ventry.booking.entity.BookingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingEventRepository extends JpaRepository<BookingEvent, Long> {

    List<BookingEvent> findByBookingIdOrderByTimestampAsc(String bookingId);
}
