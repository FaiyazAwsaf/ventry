package com.ventry.event.service;

import com.ventry.event.dto.CreateEventRequest;
import com.ventry.event.dto.CreateTierRequest;
import com.ventry.event.dto.EventResponse;
import com.ventry.event.exception.InsufficientInventoryException;
import com.ventry.event.repository.EventRepository;
import com.ventry.event.repository.TicketTierRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No @Transactional here on purpose: proving the atomic UPDATE actually prevents overselling
 * requires genuinely concurrent transactions on separate connections, which a single
 * test-level transaction (as every other IT in this module uses) would not exercise. Test data
 * is cleaned up manually in @AfterEach instead of relying on rollback.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TicketTierInventoryIT {

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketTierRepository ticketTierRepository;

    private String eventId;

    @AfterEach
    void cleanUp() {
        if (eventId != null) {
            eventRepository.deleteById(eventId);
        }
    }

    @Test
    void reserveInventory_underConcurrentRequests_neverOversells() throws InterruptedException {
        EventResponse event = eventService.createEvent(new CreateEventRequest(
                "Concurrency Test Event",
                "desc",
                LocalDateTime.now().plusDays(10),
                "Venue",
                null,
                List.of(new CreateTierRequest("Gold", new BigDecimal("100"), 10))
        ));
        eventId = event.id();
        String tierId = event.tiers().get(0).id();

        int attempts = 20;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Throwable> unexpected = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < attempts; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    eventService.reserveInventory(eventId, tierId, 1);
                    succeeded.incrementAndGet();
                } catch (InsufficientInventoryException e) {
                    rejected.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Throwable t) {
                    unexpected.add(t);
                }
            });
        }

        ready.await();
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        if (!unexpected.isEmpty()) {
            throw new AssertionError(unexpected.size() + " unexpected exceptions, first: " + unexpected.get(0), unexpected.get(0));
        }

        assertThat(succeeded.get()).isEqualTo(10);
        assertThat(rejected.get()).isEqualTo(10);
        assertThat(ticketTierRepository.findByIdAndEventId(tierId, eventId).orElseThrow().getAvailable()).isEqualTo(0);
    }
}
