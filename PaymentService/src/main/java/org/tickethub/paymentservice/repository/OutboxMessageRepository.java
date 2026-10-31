package org.tickethub.paymentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.tickethub.paymentservice.entity.OutboxMessage;

import java.util.List;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, Long> {

    List<OutboxMessage> findTop50ByStatusInAndRetryCountLessThanOrderByCreatedAtAsc(List<String> statuses, int maxRetries);

    List<OutboxMessage> findTop50ByStatusInOrderByCreatedAtAsc(List<String> statuses);
}
