package com.proben.telemetry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Componente de proben telemetry application usado pelo modulo Telemetry.
 */
@SpringBootApplication
@EnableScheduling
public class ProbenTelemetryApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProbenTelemetryApplication.class, args);
    }
}
