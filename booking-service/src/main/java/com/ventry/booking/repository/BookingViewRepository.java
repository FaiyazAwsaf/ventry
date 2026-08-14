package com.ventry.booking.repository;

import com.ventry.booking.entity.BookingView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingViewRepository extends JpaRepository<BookingView, String> {

    List<BookingView> findByCustomerIdOrderByLastUpdatedDesc(String customerId);
}
