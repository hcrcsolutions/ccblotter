package com.fmr.ec3.oscc.mediaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MediaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediaServerApplication.class, args);
    }
}
