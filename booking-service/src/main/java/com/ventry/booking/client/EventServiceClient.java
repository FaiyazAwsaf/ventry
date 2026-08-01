package com.ventry.booking.client;

import com.ventry.booking.client.dto.EventDetailsResponse;
import com.ventry.booking.client.dto.InventoryAdjustmentRequest;
import com.ventry.booking.client.dto.TierView;
import com.ventry.booking.exception.EventOrTierNotFoundException;
import com.ventry.booking.exception.TierUnavailableException;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
public class EventServiceClient {

    private final RestClient restClient;

    public EventServiceClient(@LoadBalanced RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("http://event-service").build();
    }

    /**
     * The authoritative unit price for a tier - Booking Service computes totalAmount from
     * this, never from a client-supplied amount, since the amount feeds directly into payment.
     */
    public BigDecimal getTierPrice(String eventId, String tierId) {
        EventDetailsResponse event;
        try {
            event = restClient.get()
                    .uri("/api/events/{eventId}", eventId)
                    .retrieve()
                    .body(EventDetailsResponse.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new EventOrTierNotFoundException(eventId, tierId);
        }

        return event.tiers().stream()
                .filter(tier -> tier.id().equals(tierId))
                .map(TierView::price)
                .findFirst()
                .orElseThrow(() -> new EventOrTierNotFoundException(eventId, tierId));
    }

    /**
     * Event Service's reserve endpoint is itself the atomic availability check (a conditional
     * UPDATE ... WHERE available >= quantity) - there's no separate "check then reserve" call
     * here, since that would just reintroduce the race the atomic UPDATE already avoids. A 409
     * from this call is the authoritative "not enough inventory" signal.
     */
    public void reserveInventory(String eventId, String tierId, int quantity) {
        try {
            restClient.post()
                    .uri("/api/events/{eventId}/tiers/{tierId}/reserve", eventId, tierId)
                    .body(new InventoryAdjustmentRequest(quantity))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            throw new EventOrTierNotFoundException(eventId, tierId);
        } catch (HttpClientErrorException.Conflict e) {
            throw new TierUnavailableException(eventId, tierId, quantity);
        }
    }

    /**
     * The compensating transaction for a failed payment. No Conflict branch here unlike
     * reserveInventory - Event Service's release endpoint treats a capacity-guard trip as
     * a safe no-op (never a 409), specifically so a redelivered payment.failed message
     * can call this again without erroring.
     */
    public void releaseInventory(String eventId, String tierId, int quantity) {
        try {
            restClient.post()
                    .uri("/api/events/{eventId}/tiers/{tierId}/release", eventId, tierId)
                    .body(new InventoryAdjustmentRequest(quantity))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException.NotFound e) {
            throw new EventOrTierNotFoundException(eventId, tierId);
        }
    }
}
