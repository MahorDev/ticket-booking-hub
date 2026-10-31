package org.tickethub.notificationservice.config;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;
import org.tickethub.notificationservice.event.BookingConfirmedEvent;
import org.tickethub.notificationservice.event.PaymentSuccessEvent;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class NotificationKafkaConfig {
    public static final String BOOKING_CONFIRMED_DLT = "booking.confirmed.DLT";

    private Map<String, Object> baseProperties(KafkaProperties properties) {
        Map<String, Object> map = new HashMap<>(properties.buildConsumerProperties());
        map.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        map.remove(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG);
        map.keySet().removeIf(key -> key.startsWith("spring.json."));
        return map;
    }

    @Bean
    public ProducerFactory<Object, Object> notificationProducerFactory(KafkaProperties properties) {
        Map<String, Object> map = new HashMap<>(properties.buildProducerProperties());
        map.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        map.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(map);
    }

    @Bean
    public KafkaTemplate<Object, Object> notificationKafkaTemplate(ProducerFactory<Object, Object> notificationProducerFactory) {
        return new KafkaTemplate<>(notificationProducerFactory);
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<Object, Object> template) {
        return new DeadLetterPublishingRecoverer(template, (record, ex) -> {
            String topic = record.topic();
            String dltTopic = BOOKING_CONFIRMED_DLT;
            if (topic != null && topic.contains("payment")) {
                dltTopic = "payment.success.DLT";
            }
            return new TopicPartition(dltTopic, record.partition() >= 0 ? record.partition() : 0);
        });
    }

    @Bean
    public DefaultErrorHandler notificationErrorHandler(DeadLetterPublishingRecoverer recoverer) {
        // Scenario 1: Bounded retries with backoff (3 attempts, 1s apart)
        FixedBackOff backOff = new FixedBackOff(1000L, 3L);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // Scenario 1 requirement: Non-retryable bad-data errors bypass retry straight to DLT
        errorHandler.addNotRetryableExceptions(
                IllegalArgumentException.class,
                DeserializationException.class,
                JsonParseException.class,
                JsonMappingException.class
        );
        return errorHandler;
    }

    @Bean
    public ConsumerFactory<String, PaymentSuccessEvent> paymentSuccessConsumerFactory(KafkaProperties properties) {
        JsonDeserializer<PaymentSuccessEvent> valueDeserializer =
                new JsonDeserializer<>(PaymentSuccessEvent.class, false);
        valueDeserializer.addTrustedPackages("org.tickethub.notificationservice.event");
        ErrorHandlingDeserializer<PaymentSuccessEvent> errorHandlingDeserializer =
                new ErrorHandlingDeserializer<>(valueDeserializer);
        return new DefaultKafkaConsumerFactory<>(baseProperties(properties), new StringDeserializer(), errorHandlingDeserializer, false);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentSuccessEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, PaymentSuccessEvent> paymentSuccessConsumerFactory,
            DefaultErrorHandler notificationErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, PaymentSuccessEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentSuccessConsumerFactory);
        factory.setCommonErrorHandler(notificationErrorHandler);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, BookingConfirmedEvent> bookingConfirmedConsumerFactory(KafkaProperties properties) {
        JsonDeserializer<BookingConfirmedEvent> valueDeserializer =
                new JsonDeserializer<>(BookingConfirmedEvent.class, false);
        valueDeserializer.addTrustedPackages("org.tickethub.notificationservice.event");
        ErrorHandlingDeserializer<BookingConfirmedEvent> errorHandlingDeserializer =
                new ErrorHandlingDeserializer<>(valueDeserializer);
        return new DefaultKafkaConsumerFactory<>(baseProperties(properties), new StringDeserializer(), errorHandlingDeserializer, false);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BookingConfirmedEvent> bookingConfirmedKafkaListenerContainerFactory(
            ConsumerFactory<String, BookingConfirmedEvent> bookingConfirmedConsumerFactory,
            DefaultErrorHandler notificationErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, BookingConfirmedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(bookingConfirmedConsumerFactory);
        factory.setCommonErrorHandler(notificationErrorHandler);
        return factory;
    }
}
