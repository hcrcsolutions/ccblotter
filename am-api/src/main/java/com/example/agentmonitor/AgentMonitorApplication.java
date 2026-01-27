package com.example.agentmonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AgentMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentMonitorApplication.class, args);
    }
}
