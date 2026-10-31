package org.tickethub.notificationservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.tickethub.notificationservice.entity.InboxMessage;

import java.util.Optional;

@Repository
public interface InboxMessageRepository extends JpaRepository<InboxMessage, Long> {
    boolean existsByEventId(String eventId);
    Optional<InboxMessage> findByEventId(String eventId);
}
