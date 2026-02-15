package com.fmr.ec3.oscc.receiver;

import com.fmr.ec3.oscc.receiver.config.RedisConfig;
import com.fmr.ec3.oscc.receiver.config.WebSocketConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Import({WebSocketConfig.class, RedisConfig.class})
public class KafkaReceiverApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaReceiverApplication.class, args);
    }
}
