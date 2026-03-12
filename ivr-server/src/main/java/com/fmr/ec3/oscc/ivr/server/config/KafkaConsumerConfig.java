package com.fmr.ec3.oscc.ivr.server.config;

import com.fmr.ec3.oscc.common.EventEnvelope;
import com.fmr.ec3.oscc.common.serde.EventEnvelopeDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.StringDeserializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

@Slf4j
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<?>>
            ivrFlowListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, EventEnvelope<?>> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(ivrFlowConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(ivrFlowErrorHandler());
        return factory;
    }

    private ConsumerFactory<String, EventEnvelope<?>> ivrFlowConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG, groupId,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, EventEnvelopeDeserializer.class,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false
        ));
    }

    private DefaultErrorHandler ivrFlowErrorHandler() {
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            (record, ex) -> log.error(
                "Skipping record after retries exhausted: topic={} partition={} offset={} key={}",
                record.topic(), record.partition(), record.offset(), record.key(), ex),
            new FixedBackOff(1000L, 3L)
        );

        errorHandler.addNotRetryableExceptions(
            SerializationException.class,
            ClassCastException.class
        );

        return errorHandler;
    }
}
