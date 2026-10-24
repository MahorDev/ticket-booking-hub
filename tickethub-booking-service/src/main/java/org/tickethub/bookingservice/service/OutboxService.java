package org.tickethub.bookingservice.service;

import org.tickethub.bookingservice.entity.OutboxMessage;

import java.util.List;

public interface OutboxService {

    OutboxMessage saveEvent(String aggregateType, String aggregateId, String eventType, String topic, Object payload);

    void markPublished(Long messageId);

    void markFailed(Long messageId, String error);

    List<OutboxMessage> getPendingMessages();
}
