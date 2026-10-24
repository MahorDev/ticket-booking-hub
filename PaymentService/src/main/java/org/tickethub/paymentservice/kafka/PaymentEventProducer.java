package org.tickethub.paymentservice.kafka;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.tickethub.paymentservice.event.PaymentSuccessEvent;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class PaymentEventProducer {
    public static final String PAYMENT_SUCCESS_TOPIC = "payment-success";
    private final KafkaTemplate<String, PaymentSuccessEvent> kafkaTemplate;

    public void publishPaymentSuccess(PaymentSuccessEvent event) {
        try {
            kafkaTemplate.send(PAYMENT_SUCCESS_TOPIC, String.valueOf(event.getPaymentId()), event)
                    .get(5, TimeUnit.SECONDS);
            log.info("Published payment-success event for paymentId={} bookingId={}",
                    event.getPaymentId(), event.getBookingId());
        } catch (Exception e) {
            log.error("Kafka publication for payment-success failed. paymentId={}, error: {}",
                    event.getPaymentId(), e.getMessage());
        }
    }
}
