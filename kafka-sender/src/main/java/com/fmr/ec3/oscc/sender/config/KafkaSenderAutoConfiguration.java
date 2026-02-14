package com.fmr.ec3.oscc.sender.config;

import com.fmr.ec3.oscc.sender.EventProducer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class KafkaSenderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KafkaProducerConfig osccKafkaProducerConfig(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        return new KafkaProducerConfig(bootstrapServers);
    }

    @Bean
    @ConditionalOnMissingBean
    public EventProducer eventProducer(KafkaProducerConfig config) {
        return new EventProducer(config.kafkaTemplate());
    }
}
