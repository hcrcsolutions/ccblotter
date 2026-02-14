package com.fmr.ec3.oscc.sender;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KafkaSenderApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaSenderApplication.class, args);
    }
}
