package org.tickethub.notificationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tickethub.notificationservice.entity.Notification;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Optional<Notification> findByEventKey(String eventKey);
}
