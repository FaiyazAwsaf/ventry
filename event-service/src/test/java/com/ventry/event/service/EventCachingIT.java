package com.ventry.event.service;

import com.ventry.event.entity.Event;
import com.ventry.event.repository.EventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class EventCachingIT {

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private CacheManager cacheManager;

    @AfterEach
    void evictEventsCache() {
        // @Transactional rolls back the Postgres rows, but Redis isn't part of that
        // transaction - the cache would otherwise leak stale entries into other tests.
        cacheManager.getCache("events").clear();
    }

    @Test
    void browseEvents_isServedFromCacheUntilEvicted() {
        eventRepository.save(new Event("Cached Event", "desc", LocalDateTime.now().plusDays(5), "Venue", null));
        int cachedSize = eventService.browseEvents().size();

        // Bypasses EventService (and its @CacheEvict), so the cache is now stale on purpose.
        eventRepository.save(new Event("Uncached Event", "desc", LocalDateTime.now().plusDays(6), "Venue", null));

        assertThat(eventService.browseEvents()).hasSize(cachedSize);

        cacheManager.getCache("events").clear();

        assertThat(eventService.browseEvents()).hasSize(cachedSize + 1);
    }
}
