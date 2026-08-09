package com.ventry.qrticket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ventry.common.events.BookingConfirmedEvent;
import com.ventry.common.events.TicketGeneratedEvent;
import com.ventry.qrticket.kafka.TicketEventProducer;
import com.ventry.qrticket.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketEventProducer ticketEventProducer;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, ticketEventProducer, new ObjectMapper());
    }

    @Test
    void generateTicket_persistsTicketAndPublishesTicketGenerated() {
        BookingConfirmedEvent event = new BookingConfirmedEvent(
                "booking-1", "customer-1", "event-1", "tier-1", 2, BigDecimal.valueOf(1000));
        when(ticketRepository.existsByBookingId("booking-1")).thenReturn(false);

        ticketService.generateTicket(event);

        verify(ticketRepository).save(argThat(ticket ->
                ticket.getBookingId().equals("booking-1")
                        && ticket.getEventId().equals("event-1")
                        && ticket.getTierId().equals("tier-1")
                        && ticket.getCustomerId().equals("customer-1")
                        && ticket.getQuantity() == 2
                        && ticket.getQrContent().contains("booking-1")
        ));
        verify(ticketEventProducer).publishTicketGenerated(new TicketGeneratedEvent("booking-1"));
    }

    @Test
    void generateTicket_isIdempotentOnRedeliveredBookingConfirmed() {
        BookingConfirmedEvent event = new BookingConfirmedEvent(
                "booking-1", "customer-1", "event-1", "tier-1", 2, BigDecimal.valueOf(1000));
        when(ticketRepository.existsByBookingId("booking-1")).thenReturn(true);

        ticketService.generateTicket(event);

        verify(ticketRepository, never()).save(any());
        verify(ticketEventProducer, never()).publishTicketGenerated(any());
    }
}
