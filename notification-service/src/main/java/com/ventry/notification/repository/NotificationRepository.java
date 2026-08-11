package com.ventry.notification.repository;

import com.ventry.notification.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    boolean existsByBookingIdAndType(String bookingId, Notification.Type type);
}
