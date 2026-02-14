package com.fmr.ec3.oscc.sender.config;

import com.fmr.ec3.oscc.common.EventEnvelope;
import com.fmr.ec3.oscc.sender.EventProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@AutoConfiguration
public class KafkaSenderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KafkaProducerConfig kafkaProducerConfig(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        return new KafkaProducerConfig(bootstrapServers);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProducerFactory<String, EventEnvelope<?>> producerFactory(KafkaProducerConfig config) {
        return config.producerFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public KafkaTemplate<String, EventEnvelope<?>> kafkaTemplate(KafkaProducerConfig config) {
        return config.kafkaTemplate();
    }

    @Bean
    @ConditionalOnMissingBean
    public EventProducer eventProducer(KafkaTemplate<String, EventEnvelope<?>> kafkaTemplate) {
        return new EventProducer(kafkaTemplate);
    }
}
