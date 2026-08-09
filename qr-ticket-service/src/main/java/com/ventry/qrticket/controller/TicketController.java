package com.ventry.qrticket.controller;

import com.ventry.qrticket.dto.ValidateTicketRequest;
import com.ventry.qrticket.dto.ValidateTicketResponse;
import com.ventry.qrticket.entity.Ticket;
import com.ventry.qrticket.exception.ForbiddenException;
import com.ventry.qrticket.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/validate")
    public ValidateTicketResponse validate(
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody ValidateTicketRequest request
    ) {
        if (!"ADMIN".equals(role)) {
            throw new ForbiddenException(role);
        }
        Ticket ticket = ticketService.validateTicket(request.qrContent());
        return new ValidateTicketResponse(
                ticket.getBookingId(), ticket.getEventId(), ticket.getTierId(), ticket.getQuantity(),
                ticket.getStatus().name());
    }
}
