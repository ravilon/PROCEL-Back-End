package com.proben.telemetry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ProbenTelemetryApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProbenTelemetryApplication.class, args);
    }
}
