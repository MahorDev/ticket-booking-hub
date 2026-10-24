package org.tickethub.paymentservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.tickethub.paymentservice.entity.PaymentWebhookEvent;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, Long> {

    boolean existsByEventId(String eventId);
    java.util.Optional<PaymentWebhookEvent> findByEventId(String eventId);
}