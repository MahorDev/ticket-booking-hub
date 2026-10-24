package org.tickethub.bookingservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.tickethub.bookingservice.entity.OutboxMessage;

import java.util.Collection;
import java.util.List;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    List<OutboxMessage> findTop50ByStatusInOrderByCreatedAtAsc(List<String> statuses);

    List<OutboxMessage> findTop50ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(
            Collection<String> statuses, int maxRetries);

    List<OutboxMessage> findByAggregateIdAndStatus(String aggregateId, String status);
}
