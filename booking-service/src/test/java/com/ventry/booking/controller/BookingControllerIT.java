package com.ventry.booking.controller;

import com.jayway.jsonpath.JsonPath;
import com.ventry.booking.client.EventServiceClient;
import com.ventry.booking.entity.Booking;
import com.ventry.booking.exception.EventOrTierNotFoundException;
import com.ventry.booking.exception.TierUnavailableException;
import com.ventry.booking.kafka.BookingEventProducer;
import com.ventry.booking.repository.BookingEventRepository;
import com.ventry.booking.repository.BookingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EventServiceClient and BookingEventProducer are mocked here rather than hitting real
 * Event Service / Kafka - this test proves the HTTP -> DB wiring (request validation,
 * persistence, exception -> status mapping), not cross-service integration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class BookingControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingEventRepository bookingEventRepository;

    @MockitoBean
    private EventServiceClient eventServiceClient;

    @MockitoBean
    private BookingEventProducer bookingEventProducer;

    @Test
    void createBooking_persistsEventLogAndPublishesOnSuccess() throws Exception {
        when(eventServiceClient.getTierPrice("event-1", "tier-1")).thenReturn(BigDecimal.valueOf(500));

        String requestJson = """
                {"eventId":"event-1","tierId":"tier-1","quantity":2}
                """;

        String responseJson = mockMvc.perform(post("/api/bookings")
                        .header("X-User-Id", "customer-1")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerId").value("customer-1"))
                .andExpect(jsonPath("$.totalAmount").value(1000))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();

        String bookingId = JsonPath.read(responseJson, "$.bookingId");

        verify(eventServiceClient).reserveInventory("event-1", "tier-1", 2);
        verify(bookingEventProducer).publishBookingInitiated(any());

        assertThat(bookingRepository.findById(bookingId)).isPresent();
        assertThat(bookingEventRepository.findAll())
                .anyMatch(e -> e.getBookingId().equals(bookingId) && e.getEventType().equals("BOOKING_INITIATED"));
    }

    @Test
    void createBooking_returns404WhenEventOrTierMissing() throws Exception {
        when(eventServiceClient.getTierPrice("missing-event", "tier-1"))
                .thenThrow(new EventOrTierNotFoundException("missing-event", "tier-1"));

        String requestJson = """
                {"eventId":"missing-event","tierId":"tier-1","quantity":1}
                """;

        mockMvc.perform(post("/api/bookings")
                        .header("X-User-Id", "customer-1")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isNotFound());
    }

    @Test
    void createBooking_returns409WhenTierUnavailable() throws Exception {
        when(eventServiceClient.getTierPrice("event-1", "tier-1")).thenReturn(BigDecimal.valueOf(500));
        doThrow(new TierUnavailableException("event-1", "tier-1", 10))
                .when(eventServiceClient).reserveInventory("event-1", "tier-1", 10);

        String requestJson = """
                {"eventId":"event-1","tierId":"tier-1","quantity":10}
                """;

        mockMvc.perform(post("/api/bookings")
                        .header("X-User-Id", "customer-1")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isConflict());
    }

    @Test
    void createBooking_returns400WhenUserIdHeaderMissing() throws Exception {
        String requestJson = """
                {"eventId":"event-1","tierId":"tier-1","quantity":1}
                """;

        mockMvc.perform(post("/api/bookings")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBooking_returns400ForInvalidQuantity() throws Exception {
        String requestJson = """
                {"eventId":"event-1","tierId":"tier-1","quantity":0}
                """;

        mockMvc.perform(post("/api/bookings")
                        .header("X-User-Id", "customer-1")
                        .contentType("application/json")
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    private Booking seedConfirmedBooking(String customerId) {
        Booking booking = new Booking(customerId, "event-1", "tier-1", 2, BigDecimal.valueOf(1000));
        booking.confirm();
        return bookingRepository.save(booking);
    }

    @Test
    void cancelBooking_transitionsConfirmedBookingToCancellationPending() throws Exception {
        Booking booking = seedConfirmedBooking("customer-1");

        mockMvc.perform(post("/api/bookings/{bookingId}/cancel", booking.getBookingId())
                        .header("X-User-Id", "customer-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLATION_PENDING"));

        verify(bookingEventProducer).publishBookingCancelled(any());
        assertThat(bookingEventRepository.findAll())
                .anyMatch(e -> e.getBookingId().equals(booking.getBookingId())
                        && e.getEventType().equals("BOOKING_CANCELLATION_REQUESTED"));
    }

    @Test
    void cancelBooking_rejectsNonOwnerWith403() throws Exception {
        Booking booking = seedConfirmedBooking("customer-1");

        mockMvc.perform(post("/api/bookings/{bookingId}/cancel", booking.getBookingId())
                        .header("X-User-Id", "someone-else"))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelBooking_rejectsNotYetConfirmedBookingWith409() throws Exception {
        Booking booking = bookingRepository.save(
                new Booking("customer-1", "event-1", "tier-1", 2, BigDecimal.valueOf(1000)));

        mockMvc.perform(post("/api/bookings/{bookingId}/cancel", booking.getBookingId())
                        .header("X-User-Id", "customer-1"))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelBooking_returns404ForUnknownBooking() throws Exception {
        mockMvc.perform(post("/api/bookings/{bookingId}/cancel", "does-not-exist")
                        .header("X-User-Id", "customer-1"))
                .andExpect(status().isNotFound());
    }
}
