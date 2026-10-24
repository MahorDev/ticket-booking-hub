package org.tickethub.notificationservice.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.tickethub.notificationservice.event.BookingConfirmedEvent;
import org.tickethub.notificationservice.event.PaymentSuccessEvent;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class NotificationKafkaConfig {
    private Map<String, Object> baseProperties(KafkaProperties properties) {
        Map<String, Object> map = new HashMap<>(properties.buildConsumerProperties());
        map.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        map.remove(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG);
        map.keySet().removeIf(key -> key.startsWith("spring.json."));
        return map;
    }

    @Bean
    public ConsumerFactory<String, PaymentSuccessEvent> paymentSuccessConsumerFactory(KafkaProperties properties) {
        JsonDeserializer<PaymentSuccessEvent> valueDeserializer =
                new JsonDeserializer<>(PaymentSuccessEvent.class, false);
        valueDeserializer.addTrustedPackages("org.tickethub.notificationservice.event");
        return new DefaultKafkaConsumerFactory<>(baseProperties(properties), new StringDeserializer(), valueDeserializer, false);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentSuccessEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, PaymentSuccessEvent> paymentSuccessConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, PaymentSuccessEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(paymentSuccessConsumerFactory);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, BookingConfirmedEvent> bookingConfirmedConsumerFactory(KafkaProperties properties) {
        JsonDeserializer<BookingConfirmedEvent> valueDeserializer =
                new JsonDeserializer<>(BookingConfirmedEvent.class, false);
        valueDeserializer.addTrustedPackages("org.tickethub.notificationservice.event");
        return new DefaultKafkaConsumerFactory<>(baseProperties(properties), new StringDeserializer(), valueDeserializer, false);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, BookingConfirmedEvent> bookingConfirmedKafkaListenerContainerFactory(
            ConsumerFactory<String, BookingConfirmedEvent> bookingConfirmedConsumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, BookingConfirmedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(bookingConfirmedConsumerFactory);
        return factory;
    }
}
