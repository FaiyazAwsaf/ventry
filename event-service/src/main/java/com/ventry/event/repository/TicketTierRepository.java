package com.ventry.event.repository;

import com.ventry.event.entity.TicketTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TicketTierRepository extends JpaRepository<TicketTier, String> {

    Optional<TicketTier> findByIdAndEventId(String id, String eventId);

    boolean existsByIdAndEventId(String id, String eventId);

    /**
     * Single atomic UPDATE guarded by the WHERE clause - avoids the classic
     * read-then-write oversell race under concurrent bookings. Returns the number of
     * rows updated: 0 means either the tier doesn't exist or there wasn't enough stock.
     * clearAutomatically = true so a findById in the same transaction right after this
     * doesn't return a stale, pre-update entity from the first-level cache.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE TicketTier t SET t.available = t.available - :quantity " +
            "WHERE t.id = :id AND t.event.id = :eventId AND t.available >= :quantity")
    int reserve(@Param("id") String id, @Param("eventId") String eventId, @Param("quantity") int quantity);

    /**
     * Mirror of reserve(): the available + quantity <= capacity guard makes a duplicate
     * release (e.g. a redelivered Kafka message) a harmless no-op instead of over-restoring
     * inventory past capacity.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE TicketTier t SET t.available = t.available + :quantity " +
            "WHERE t.id = :id AND t.event.id = :eventId AND t.available + :quantity <= t.capacity")
    int release(@Param("id") String id, @Param("eventId") String eventId, @Param("quantity") int quantity);
}
