package com.ventry.booking.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Mirrors only the fields Booking Service needs from Event Service's GET /api/events/{eventId}
 * response. ignoreUnknown so the rest of that payload (description, venue, bannerUrl, ...)
 * doesn't break deserialization here - the two services evolve independently.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventDetailsResponse(String id, String name, List<TierView> tiers) {
}
