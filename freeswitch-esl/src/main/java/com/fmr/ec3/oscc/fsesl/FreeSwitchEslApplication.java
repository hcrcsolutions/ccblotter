package com.fmr.ec3.oscc.fsesl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FreeSwitchEslApplication {

    public static void main(String[] args) {
        SpringApplication.run(FreeSwitchEslApplication.class, args);
    }
}
