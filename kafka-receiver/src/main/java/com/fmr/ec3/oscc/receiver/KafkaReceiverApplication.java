package com.fmr.ec3.oscc.receiver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KafkaReceiverApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaReceiverApplication.class, args);
    }
}
