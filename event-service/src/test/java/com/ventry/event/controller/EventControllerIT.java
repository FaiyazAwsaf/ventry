package com.ventry.event.controller;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class EventControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fullEventLifecycle_createBrowseGetUpdateDelete() throws Exception {
        String createJson = """
                {
                  "name": "Coldplay Live",
                  "description": "World tour stop",
                  "eventDate": "2027-01-01T20:00:00",
                  "venue": "Army Stadium",
                  "bannerUrl": "https://example.com/banner.png",
                  "tiers": [
                    {"name": "Gold", "price": 5000, "capacity": 100}
                  ]
                }
                """;

        String createResponse = mockMvc.perform(post("/api/events")
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Coldplay Live"))
                .andExpect(jsonPath("$.tiers[0].available").value(100))
                .andReturn().getResponse().getContentAsString();

        String eventId = JsonPath.read(createResponse, "$.id");

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + eventId + "')]").exists());

        mockMvc.perform(get("/api/events/{id}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venue").value("Army Stadium"));

        String updateJson = """
                {
                  "name": "Coldplay Live - Rescheduled",
                  "description": "World tour stop",
                  "eventDate": "2027-02-01T20:00:00",
                  "venue": "Army Stadium",
                  "bannerUrl": "https://example.com/banner.png"
                }
                """;

        mockMvc.perform(put("/api/events/{id}", eventId)
                        .contentType("application/json")
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Coldplay Live - Rescheduled"));

        mockMvc.perform(delete("/api/events/{id}", eventId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/events/{id}", eventId))
                .andExpect(status().isNotFound());
    }

    @Test
    void createEvent_rejectsInvalidPayloadWith400() throws Exception {
        String invalidJson = """
                {
                  "name": "",
                  "eventDate": "2020-01-01T20:00:00",
                  "venue": "",
                  "tiers": []
                }
                """;

        mockMvc.perform(post("/api/events")
                        .contentType("application/json")
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getEvent_returnsNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/events/{id}", "does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void tierInventory_reserveThenReleaseReflectsAvailability() throws Exception {
        String createJson = """
                {
                  "name": "Tour Stop",
                  "description": "desc",
                  "eventDate": "2027-03-01T20:00:00",
                  "venue": "Venue",
                  "bannerUrl": null,
                  "tiers": [
                    {"name": "Silver", "price": 1000, "capacity": 5}
                  ]
                }
                """;

        String createResponse = mockMvc.perform(post("/api/events")
                        .contentType("application/json")
                        .content(createJson))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String eventId = JsonPath.read(createResponse, "$.id");
        String tierId = JsonPath.read(createResponse, "$.tiers[0].id");

        mockMvc.perform(get("/api/events/{eventId}/tiers/{tierId}/availability", eventId, tierId)
                        .param("quantity", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.remaining").value(5));

        mockMvc.perform(post("/api/events/{eventId}/tiers/{tierId}/reserve", eventId, tierId)
                        .contentType("application/json")
                        .content("{\"quantity\": 5}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/events/{eventId}/tiers/{tierId}/reserve", eventId, tierId)
                        .contentType("application/json")
                        .content("{\"quantity\": 1}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/events/{eventId}/tiers/{tierId}/release", eventId, tierId)
                        .contentType("application/json")
                        .content("{\"quantity\": 2}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/events/{eventId}/tiers/{tierId}/availability", eventId, tierId)
                        .param("quantity", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remaining").value(2));
    }

    @Test
    void tierAvailability_returnsNotFoundForUnknownTier() throws Exception {
        mockMvc.perform(get("/api/events/{eventId}/tiers/{tierId}/availability", "no-event", "no-tier")
                        .param("quantity", "1"))
                .andExpect(status().isNotFound());
    }
}
