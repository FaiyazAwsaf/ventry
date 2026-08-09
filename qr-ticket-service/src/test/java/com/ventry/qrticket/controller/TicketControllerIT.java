package com.ventry.qrticket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ventry.qrticket.entity.Ticket;
import com.ventry.qrticket.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class TicketControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Ticket seedTicket(String bookingId) {
        String qrContent = "{\"bookingId\":\"" + bookingId
                + "\",\"eventId\":\"event-1\",\"tierId\":\"tier-1\",\"customerId\":\"customer-1\"}";
        return ticketRepository.save(new Ticket(bookingId, "event-1", "tier-1", "customer-1", 2, qrContent));
    }

    private String validateBody(String qrContent) throws Exception {
        return "{\"qrContent\":" + objectMapper.writeValueAsString(qrContent) + "}";
    }

    @Test
    void validate_acceptsValidScanAndMarksTicketValidated() throws Exception {
        Ticket ticket = seedTicket("booking-1");

        mockMvc.perform(post("/api/tickets/validate")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content(validateBody(ticket.getQrContent())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value("booking-1"))
                .andExpect(jsonPath("$.status").value("VALIDATED"));
    }

    @Test
    void validate_rejectsSecondScanOfSameTicketWith409() throws Exception {
        Ticket ticket = seedTicket("booking-2");
        String body = validateBody(ticket.getQrContent());

        mockMvc.perform(post("/api/tickets/validate")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/tickets/validate")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void validate_returnsNotFoundForUnknownBooking() throws Exception {
        String qrContent = "{\"bookingId\":\"does-not-exist\",\"eventId\":\"e\",\"tierId\":\"t\",\"customerId\":\"c\"}";

        mockMvc.perform(post("/api/tickets/validate")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content(validateBody(qrContent)))
                .andExpect(status().isNotFound());
    }

    @Test
    void validate_rejectsTamperedContentWith400() throws Exception {
        Ticket ticket = seedTicket("booking-3");
        String tampered = ticket.getQrContent().replace("customer-1", "someone-else");

        mockMvc.perform(post("/api/tickets/validate")
                        .header("X-User-Role", "ADMIN")
                        .contentType("application/json")
                        .content(validateBody(tampered)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validate_rejectsNonAdminRoleWith403() throws Exception {
        Ticket ticket = seedTicket("booking-4");

        mockMvc.perform(post("/api/tickets/validate")
                        .header("X-User-Role", "CUSTOMER")
                        .contentType("application/json")
                        .content(validateBody(ticket.getQrContent())))
                .andExpect(status().isForbidden());
    }
}
