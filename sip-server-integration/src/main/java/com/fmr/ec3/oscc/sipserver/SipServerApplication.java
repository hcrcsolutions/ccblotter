package com.fmr.ec3.oscc.sipserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SipServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SipServerApplication.class, args);
    }
}
