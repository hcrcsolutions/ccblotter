package com.fmr.ec3.oscc.receiver.config;

import com.fmr.ec3.oscc.common.EventEnvelope;
import com.fmr.ec3.oscc.common.serde.EventEnvelopeDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<?>>
            osccKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<?>> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(osccConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(osccKafkaErrorHandler());
        return factory;
    }

    private ConsumerFactory<String, EventEnvelope<?>> osccConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG, "kafka-receiver",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, EventEnvelopeDeserializer.class,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false
        ));
    }

    /**
     * Retries transient failures (e.g. Redis down) 3 times with 1s backoff.
     * Permanent failures (bad data, schema mismatch) skip retries immediately.
     * After exhausting retries, the record is logged and skipped so the
     * partition is never permanently blocked by a poison-pill message.
     */
    private DefaultErrorHandler osccKafkaErrorHandler() {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            (record, ex) -> log.error(
                "Skipping record after retries exhausted: topic={} partition={} offset={} key={}",
                record.topic(), record.partition(), record.offset(), record.key(), ex),
            new FixedBackOff(1000L, 3L)
        );

        // Don't retry data/schema errors — they will never succeed.
        errorHandler.addNotRetryableExceptions(
            SerializationException.class,
            ClassCastException.class
        );

        return errorHandler;
    }
}
