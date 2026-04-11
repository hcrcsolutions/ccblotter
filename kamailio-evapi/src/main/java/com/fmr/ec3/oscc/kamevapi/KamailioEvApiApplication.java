package com.fmr.ec3.oscc.kamevapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KamailioEvApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(KamailioEvApiApplication.class, args);
    }
}
