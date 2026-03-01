package com.procel.ingestion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.procel.ingestion.integration.cobalto.CobaltoProperties;


@SpringBootApplication
@ConfigurationPropertiesScan
@EnableConfigurationProperties(CobaltoProperties.class)
public class IngestionApplication {

	public static void main(String[] args) {
		SpringApplication.run(IngestionApplication.class, args);
	}

}
