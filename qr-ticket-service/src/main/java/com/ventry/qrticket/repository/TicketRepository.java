package com.ventry.qrticket.repository;

import com.ventry.qrticket.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TicketRepository extends JpaRepository<Ticket, String> {

    boolean existsByBookingId(String bookingId);

    Optional<Ticket> findByBookingId(String bookingId);
}
