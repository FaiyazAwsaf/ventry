package com.ventry.booking.repository;

import com.ventry.booking.entity.BookingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingEventRepository extends JpaRepository<BookingEvent, Long> {
}
