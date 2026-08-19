package com.procel.telemetry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProcelTelemetryApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProcelTelemetryApplication.class, args);
    }
}
