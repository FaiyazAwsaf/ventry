package com.ventry.event.repository;

import com.ventry.event.entity.TicketTier;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketTierRepository extends JpaRepository<TicketTier, String> {
}
