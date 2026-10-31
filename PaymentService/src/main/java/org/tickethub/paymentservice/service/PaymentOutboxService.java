package org.tickethub.paymentservice.service;

import org.tickethub.paymentservice.entity.OutboxMessage;

import java.util.List;

public interface PaymentOutboxService {

    OutboxMessage saveEvent(String aggregateType, String aggregateId, String eventType, String topic, Object payload);

    void markPublished(Long messageId);

    void markFailed(Long messageId, String error);

    List<OutboxMessage> getPendingMessages();
}
